package lab.devlessons.lock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lab.devlessons.lock.StockServices.OptimisticRetryService;
import lab.devlessons.lock.StockServices.StockService;
import lab.devlessons.support.PostgresContainerConfig;
import lab.devlessons.support.SqlCapture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/** 레슨 "낙관적 락 vs 비관적 락"의 주장을 PostgreSQL에서 검증한다. 테스트에는 트랜잭션이 없다. */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class LockingTest {

    @Autowired StockService stock;
    @Autowired OptimisticRetryService retry;
    @Autowired PlainStockRepository plainRepo;
    @Autowired VersionedStockRepository versionedRepo;

    Long plainId;
    Long versionedId;

    @BeforeEach
    void setUp() {
        plainRepo.deleteAll();
        versionedRepo.deleteAll();
        plainId = plainRepo.save(new PlainStock(100)).getId();
        versionedId = versionedRepo.save(new VersionedStock(100)).getId();
        retry.resetRetries();
    }

    @Test
    @DisplayName("락 없음: 두 주문이 동시에 재고 100을 읽고 10씩 빼면 80이 아니라 90 (갱신 손실)")
    void noLockLosesUpdate() throws Exception {
        CyclicBarrier bothRead = new CyclicBarrier(2);

        runBoth(() -> stock.decreaseNoLock(plainId, 10, () -> await(bothRead)));

        assertThat(plainRepo.findById(plainId).orElseThrow().getQuantity()).isEqualTo(90);
    }

    @Test
    @DisplayName("낙관적 락: 둘 다 같은 버전을 읽으면 먼저 커밋한 쪽만 성공하고, 나중 쪽은 ObjectOptimisticLockingFailureException")
    void optimisticSecondCommitFails() throws Exception {
        CyclicBarrier bothRead = new CyclicBarrier(2);

        List<Throwable> errors = runBoth(() -> stock.decreaseOptimistic(versionedId, 10, () -> await(bothRead)));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(versionedRepo.findById(versionedId).orElseThrow().getQuantity()).isEqualTo(90);
    }

    @Test
    @DisplayName("낙관적 락: UPDATE마다 버전이 1씩 올라간다")
    void versionIncrements() {
        Long before = versionedRepo.findById(versionedId).orElseThrow().getVersion();
        stock.decreaseOptimistic(versionedId, 1, () -> { });

        assertThat(versionedRepo.findById(versionedId).orElseThrow().getVersion()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("낙관적 락 + 재시도: 동시에 20건이 1씩 빼도 정확히 80")
    void optimisticWithRetryIsExact() throws Exception {
        runMany(20, () -> retry.decrease(versionedId, 1));

        assertThat(versionedRepo.findById(versionedId).orElseThrow().getQuantity()).isEqualTo(80);
    }

    @Test
    @DisplayName("비관적 락: SELECT ... FOR UPDATE를 보내고, 뒤 트랜잭션은 앞 트랜잭션이 끝날 때까지 기다린다")
    void pessimisticBlocks() throws Exception {
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SqlCapture.clear();

        CompletableFuture<Void> first = CompletableFuture.runAsync(() ->
                stock.decreasePessimistic(plainId, 10, () -> {
                    holding.countDown();
                    awaitLatch(release); // 락을 쥔 채로 멈춰 있다
                }));
        assertThat(holding.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> second = CompletableFuture.runAsync(() -> stock.decreasePessimistic(plainId, 10, () -> { }));
        Thread.sleep(300);
        assertThat(second.isDone()).as("앞 트랜잭션이 락을 쥐고 있는 동안 기다린다").isFalse();

        release.countDown();
        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);

        assertThat(plainRepo.findById(plainId).orElseThrow().getQuantity()).isEqualTo(80);
        assertThat(SqlCapture.sqls()).anySatisfy(sql -> assertThat(sql.toLowerCase()).containsPattern("for (no key )?update"));
    }

    @Test
    @DisplayName("비관적 락: 동시에 50건이 1씩 빼도 정확히 50")
    void pessimisticIsExact() throws Exception {
        runMany(50, () -> stock.decreasePessimistic(plainId, 1, () -> { }));

        assertThat(plainRepo.findById(plainId).orElseThrow().getQuantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("NOWAIT: 이미 잠긴 행이면 기다리지 않고 바로 PessimisticLockingFailureException")
    void noWaitFailsFast() throws Exception {
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> first = CompletableFuture.runAsync(() ->
                stock.decreasePessimistic(plainId, 10, () -> {
                    holding.countDown();
                    awaitLatch(release);
                }));
        assertThat(holding.await(10, TimeUnit.SECONDS)).isTrue();

        Throwable error = null;
        try {
            stock.decreasePessimisticNoWait(plainId, 10);
        } catch (Throwable t) {
            error = t;
        } finally {
            release.countDown();
        }
        first.get(10, TimeUnit.SECONDS);

        assertThat(error).isInstanceOf(PessimisticLockingFailureException.class);
        assertThat(plainRepo.findById(plainId).orElseThrow().getQuantity()).isEqualTo(90);
    }

    /** 두 스레드로 동시에 실행하고, 난 예외들을 모아 돌려준다 */
    private static List<Throwable> runBoth(Runnable task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> fs = List.of(pool.submit(task), pool.submit(task));
            List<Throwable> errors = new ArrayList<>();
            for (Future<?> f : fs) {
                try {
                    f.get(30, TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException e) {
                    errors.add(e.getCause());
                }
            }
            return errors;
        } finally {
            pool.shutdownNow();
        }
    }

    private static void runMany(int n, Runnable task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(n, 8));
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                fs.add(pool.submit(() -> {
                    awaitLatch(start);
                    task.run();
                }));
            }
            start.countDown();
            for (Future<?> f : fs) {
                f.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
