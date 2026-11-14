package lab.devlessons.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 레슨 "Future와 Callable"의 주장을 검증한다. */
class FutureTest {

    private ExecutorService pool;

    @AfterEach
    void tearDown() {
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("submit은 작업이 끝나길 기다리지 않고 즉시 Future를 돌려준다")
    void submitReturnsImmediately() throws Exception {
        pool = Executors.newFixedThreadPool(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<String> future = pool.submit(() -> {
            release.await();
            return "done";
        });
        assertThat(future.isDone()).isFalse();

        release.countDown();
        assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("done");
        assertThat(future.isDone()).isTrue();
    }

    @Test
    @DisplayName("get()은 작업이 끝날 때까지 호출한 스레드를 막는다 (블로킹)")
    void getBlocks() throws Exception {
        pool = Executors.newFixedThreadPool(1);
        long start = System.nanoTime();

        Future<Integer> future = pool.submit(() -> {
            Thread.sleep(300);
            return 1;
        });
        future.get();

        assertThat(elapsedMillis(start)).isGreaterThanOrEqualTo(250);
    }

    @Test
    @DisplayName("get(timeout)은 시간 안에 끝나지 않으면 TimeoutException, 작업은 계속 남아 있다")
    void getWithTimeout() {
        pool = Executors.newFixedThreadPool(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<String> future = pool.submit(() -> {
            release.await();
            return "late";
        });

        assertThatThrownBy(() -> future.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        assertThat(future.isDone()).isFalse();
        release.countDown();
    }

    @Test
    @DisplayName("작업에서 난 예외는 submit이 아니라 get()에서 ExecutionException으로 감싸져 나온다")
    void exceptionIsWrapped() {
        pool = Executors.newFixedThreadPool(1);

        Future<Integer> future = pool.submit((Callable<Integer>) () -> {
            throw new IllegalStateException("API 실패");
        });

        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("cancel(true)는 실행 중인 스레드를 인터럽트하고, 이후 get()은 CancellationException")
    void cancelInterrupts() throws Exception {
        pool = Executors.newFixedThreadPool(1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);

        Future<?> future = pool.submit(() -> {
            started.countDown();
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
        });
        started.await(5, TimeUnit.SECONDS);
        future.cancel(true);

        assertThat(future.isCancelled()).isTrue();
        assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(future::get).isInstanceOf(CancellationException.class);
    }

    @Test
    @DisplayName("Runnable을 submit하면 결과가 없어서 get()은 null")
    void runnableHasNoResult() throws Exception {
        pool = Executors.newFixedThreadPool(1);
        Runnable task = () -> { };

        assertThat(pool.submit(task).get()).isNull();
    }

    @Test
    @DisplayName("모두 submit한 뒤 get하면 총 시간은 가장 긴 작업 정도 (300ms × 3, 스레드 3개)")
    void submitAllThenGet() throws Exception {
        pool = Executors.newFixedThreadPool(3);
        long start = System.nanoTime();

        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            futures.add(pool.submit(FutureTest::slowTask));
        }
        for (Future<Integer> f : futures) {
            f.get();
        }

        assertThat(elapsedMillis(start)).isBetween(250L, 800L);
    }

    @Test
    @DisplayName("submit 직후 바로 get하면 하나씩 기다려서 총 시간은 합 (900ms 이상)")
    void submitThenGetImmediately() throws Exception {
        pool = Executors.newFixedThreadPool(3);
        long start = System.nanoTime();

        for (int i = 0; i < 3; i++) {
            pool.submit(FutureTest::slowTask).get();
        }

        assertThat(elapsedMillis(start)).isGreaterThanOrEqualTo(850);
    }

    @Test
    @DisplayName("스레드 풀 크기(2)보다 많은 작업(4)은 큐에서 기다린다")
    void extraTasksWaitInQueue() throws Exception {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
        pool = executor;
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        for (int i = 0; i < 4; i++) {
            executor.submit(() -> {
                started.countDown();
                release.await();
                return null;
            });
        }
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(executor.getQueue()).hasSize(2);
        release.countDown();
    }

    @Test
    @DisplayName("CompletableFuture: get 없이 콜백으로 이어 붙이고, 값이 완성되는 순간 실행된다")
    void completableFutureCallback() {
        CompletableFuture<String> price = new CompletableFuture<>();
        AtomicReference<String> seen = new AtomicReference<>();

        price.thenApply(p -> p + "원").thenAccept(seen::set);
        assertThat(seen.get()).isNull();

        price.complete("1000");
        assertThat(seen.get()).isEqualTo("1000원");
    }

    @Test
    @DisplayName("CompletableFuture: thenCombine으로 두 결과를 합치고, exceptionally로 실패를 기본값으로 바꾼다")
    void completableFutureCombineAndRecover() throws Exception {
        pool = Executors.newFixedThreadPool(2);

        CompletableFuture<Integer> a = CompletableFuture.supplyAsync(() -> 1000, pool);
        CompletableFuture<Integer> b = CompletableFuture.supplyAsync(() -> 2000, pool);
        assertThat(a.thenCombine(b, Integer::sum).get(5, TimeUnit.SECONDS)).isEqualTo(3000);

        CompletableFuture<Integer> failed = CompletableFuture.supplyAsync(() -> {
            throw new IllegalStateException("API 실패");
        }, pool);
        assertThat(failed.exceptionally(e -> 0).get(5, TimeUnit.SECONDS)).isZero();
    }

    private static Integer slowTask() throws InterruptedException {
        Thread.sleep(300);
        return 1;
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
