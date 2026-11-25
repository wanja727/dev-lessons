package lab.devlessons.lock;

import static lab.devlessons.support.Jdbc.exec;
import static lab.devlessons.support.Jdbc.number;
import static lab.devlessons.support.Jdbc.rows;
import static lab.devlessons.support.Jdbc.update;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import lab.devlessons.lock.StockServices.OptimisticRetryService;
import lab.devlessons.lock.StockServices.StockService;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import lab.devlessons.support.SqlCapture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 레슨 "낙관적 락과 비관적 락"(D10)을 다시 쓰며, 레슨에 붙일 출력과 새 주장을 남긴다.
 * 예전 주장은 LockingTest가 그대로 확인한다. SQL로 보이는 주장은 lk_stock 테이블과 연결 두 개로, JPA 주장은 lock 패키지의 엔티티로 확인한다.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class LockingLessonTest {

    private static final String LESSON = "locking";

    @Autowired DataSource ds;
    @Autowired StockService stock;
    @Autowired OptimisticRetryService retry;
    @Autowired PlainStockRepository plainRepo;
    @Autowired VersionedStockRepository versionedRepo;

    @BeforeEach
    void setUp() {
        exec(ds, "drop table if exists lk_stock, lk_job",
                "create table lk_stock (product_id int primary key, quantity int not null, version bigint not null)",
                "insert into lk_stock values (1, 100, 7)",
                "create table lk_job (id int primary key, status text not null)",
                "insert into lk_job values (1, 'READY'), (2, 'READY'), (3, 'READY')");
        plainRepo.deleteAll();
        versionedRepo.deleteAll();
        retry.resetRetries();
    }

    private Connection tx() throws SQLException {
        Connection c = ds.getConnection();
        exec(c, "set lock_timeout = 0");
        c.setAutoCommit(false);
        return c;
    }

    @Test
    @DisplayName("락 없이 두 주문이 같은 100을 읽고 각자 90을 쓰면 남은 재고는 90이다. 원자적 UPDATE는 80이다")
    void lostUpdateAndAtomic() throws SQLException {
        try (Connection a = tx(); Connection b = tx()) {
            long ra = number(a, "select quantity from lk_stock where product_id = 1");
            long rb = number(b, "select quantity from lk_stock where product_id = 1");
            exec(a, "update lk_stock set quantity = " + (ra - 10) + " where product_id = 1");
            a.commit();
            exec(b, "update lk_stock set quantity = " + (rb - 10) + " where product_id = 1");
            b.commit();
        }
        long lost = number(ds, "select quantity from lk_stock");
        exec(ds, "update lk_stock set quantity = 100");
        try (Connection a = tx(); Connection b = tx()) {
            exec(a, "update lk_stock set quantity = quantity - 10 where product_id = 1 and quantity >= 10");
            CompletableFuture<Void> bf = CompletableFuture.runAsync(() -> {
                try {
                    exec(b, "update lk_stock set quantity = quantity - 10 where product_id = 1 and quantity >= 10");
                    b.commit();
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            });
            a.commit();
            bf.join();
        }
        long atomic = number(ds, "select quantity from lk_stock");

        assertThat(lost).isEqualTo(90);
        assertThat(atomic).isEqualTo(80);
        CiOutput.record(LESSON, "lost-update", "[락 없이 읽고 계산해서 쓰기] 두 주문이 100을 읽고 각자 90을 씀 → 남은 재고 " + lost
                + "\n[원자적 UPDATE quantity = quantity - 10] → 남은 재고 " + atomic);
    }

    @Test
    @DisplayName("SQL로 쓴 낙관적 락: 둘 다 version 7을 읽으면 먼저 쓴 쪽은 1행, 나중 쪽은 0행을 바꾼다")
    void optimisticSql() throws SQLException {
        List<String> log = new ArrayList<>();
        try (Connection a = tx(); Connection b = tx()) {
            log.add("A: select → " + rows(a, "select quantity, version from lk_stock where product_id = 1").get(0) + " (quantity | version)");
            log.add("B: select → " + rows(b, "select quantity, version from lk_stock where product_id = 1").get(0));
            int ua = update(a, "update lk_stock set quantity = 90, version = version + 1 where product_id = 1 and version = 7");
            a.commit();
            log.add("A: update ... where version = 7 → 바뀐 행 " + ua + ", commit");
            int ub = update(b, "update lk_stock set quantity = 90, version = version + 1 where product_id = 1 and version = 7");
            b.commit();
            log.add("B: update ... where version = 7 → 바뀐 행 " + ub);
            assertThat(ua).isEqualTo(1);
            assertThat(ub).isZero();
        }
        log.add("결과: " + rows(ds, "select quantity, version from lk_stock").get(0) + " (quantity | version)");
        CiOutput.record(LESSON, "optimistic-sql", String.join("\n", log));
    }

    @Test
    @DisplayName("JPA @Version: 하이버네이트가 UPDATE에 version 조건을 넣고, 같은 버전을 읽은 나중 쪽은 ObjectOptimisticLockingFailureException")
    void optimisticJpa() throws Exception {
        Long id = versionedRepo.save(new VersionedStock(100)).getId();
        SqlCapture.clear();
        stock.decreaseOptimistic(id, 1, () -> { });
        String updateSql = SqlCapture.sqls().stream().filter(s -> s.toLowerCase().startsWith("update")).findFirst().orElseThrow();

        CyclicBarrier bothRead = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<?>> fs = List.of(
                pool.submit(() -> stock.decreaseOptimistic(id, 1, () -> await(bothRead))),
                pool.submit(() -> stock.decreaseOptimistic(id, 1, () -> await(bothRead))));
        List<String> results = new ArrayList<>();
        for (Future<?> f : fs) {
            try {
                f.get(10, TimeUnit.SECONDS);
                results.add("성공");
            } catch (java.util.concurrent.ExecutionException e) {
                results.add(e.getCause().getClass().getName());
            }
        }
        pool.shutdown();

        assertThat(updateSql.toLowerCase()).contains("version=?").contains("where");
        assertThat(results).contains("성공", "org.springframework.orm.ObjectOptimisticLockingFailureException");
        CiOutput.record(LESSON, "optimistic-jpa", "하이버네이트가 보낸 UPDATE:\n" + updateSql
                + "\n\n같은 버전을 읽은 두 트랜잭션의 결과: " + results);
    }

    private static void await(CyclicBarrier b) {
        try {
            b.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("FOR UPDATE: B는 A가 커밋할 때까지 기다린 뒤 A가 쓴 90을 읽는다. 일반 SELECT는 기다리지 않는다")
    void pessimisticSql() throws Exception {
        List<String> log = new ArrayList<>();
        try (Connection a = tx(); Connection b = tx(); Connection c = ds.getConnection()) {
            log.add("A: select ... for update → " + number(a, "select quantity from lk_stock where product_id = 1 for update"));
            long t0 = System.nanoTime();
            long plain = number(c, "select quantity from lk_stock where product_id = 1");
            long plainMs = (System.nanoTime() - t0) / 1_000_000;
            log.add("C: 일반 select → " + plain + " (" + plainMs + "ms, 기다리지 않음)");
            long t1 = System.nanoTime();
            CompletableFuture<Long> bRead = CompletableFuture.supplyAsync(() -> {
                try {
                    return number(b, "select quantity from lk_stock where product_id = 1 for update");
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            });
            Thread.sleep(1000);
            assertThat(bRead).isNotDone();
            exec(a, "update lk_stock set quantity = 90 where product_id = 1");
            a.commit();
            log.add("A: update quantity = 90, commit (1초 뒤)");
            long seen = bRead.get(5, TimeUnit.SECONDS);
            long waited = (System.nanoTime() - t1) / 1_000_000;
            log.add("B: select ... for update → " + seen + " (" + waited + "ms 기다림)");
            b.commit();
            assertThat(seen).isEqualTo(90);
            assertThat(plainMs).isLessThan(500);
        }
        CiOutput.record(LESSON, "pessimistic-sql", String.join("\n", log));
    }

    @Test
    @DisplayName("JPA @Lock(PESSIMISTIC_WRITE)는 PostgreSQL에 for no key update를 보낸다")
    void pessimisticJpaSql() {
        Long id = plainRepo.save(new PlainStock(100)).getId();
        SqlCapture.clear();
        stock.decreasePessimistic(id, 1, () -> { });
        String select = SqlCapture.sqls().stream().filter(s -> s.toLowerCase().contains("for ")).findFirst().orElseThrow();
        assertThat(select.toLowerCase()).contains("for no key update");
        CiOutput.record(LESSON, "pessimistic-jpa", select);
    }

    @Test
    @DisplayName("동시에 50건이 1씩 빼면 비관적 락은 다시 시도 없이 50, 낙관적 락은 다시 시도를 거쳐 50이다")
    void fiftyConcurrent() throws Exception {
        Long pid = plainRepo.save(new PlainStock(100)).getId();
        Long vid = versionedRepo.save(new VersionedStock(100)).getId();

        long t0 = System.nanoTime();
        runConcurrently(50, () -> stock.decreasePessimistic(pid, 1, () -> { }));
        long pessimisticMs = (System.nanoTime() - t0) / 1_000_000;
        long t1 = System.nanoTime();
        runConcurrently(50, () -> retry.decrease(vid, 1));
        long optimisticMs = (System.nanoTime() - t1) / 1_000_000;

        int pLeft = plainRepo.findById(pid).orElseThrow().getQuantity();
        int vLeft = versionedRepo.findById(vid).orElseThrow().getQuantity();
        assertThat(pLeft).isEqualTo(50);
        assertThat(vLeft).isEqualTo(50);
        CiOutput.record(LESSON, "fifty", String.join("\n",
                "방식 | 남은 재고 | 다시 시도 | 걸린 시간",
                "비관적 락 | " + pLeft + " | 0 | " + pessimisticMs + "ms",
                "낙관적 락 + 다시 시도 | " + vLeft + " | " + retry.retries() + " | " + optimisticMs + "ms"));
    }

    private static void runConcurrently(int n, Runnable task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CyclicBarrier start = new CyclicBarrier(n);
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            fs.add(pool.submit(() -> {
                await(start);
                task.run();
            }));
        }
        for (Future<?> f : fs) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
    }

    @Test
    @DisplayName("NOWAIT는 이미 잠긴 행이면 바로 55P03으로 실패하고, SKIP LOCKED는 잠긴 행을 건너뛴다")
    void nowaitAndSkipLocked() throws SQLException {
        String nowait;
        String skipped;
        try (Connection a = tx(); Connection b = tx()) {
            number(a, "select quantity from lk_stock where product_id = 1 for update");
            long t = System.nanoTime();
            SQLException e = (SQLException) catchThrowable(() -> number(b, "select quantity from lk_stock where product_id = 1 for update nowait"));
            nowait = e.getSQLState() + " " + e.getMessage().lines().findFirst().orElse("") + " (" + (System.nanoTime() - t) / 1_000_000 + "ms)";
            b.rollback();
            a.rollback();
        }
        try (Connection a = tx(); Connection b = tx()) {
            String aJob = rows(a, "select id from lk_job where status = 'READY' order by id limit 1 for update skip locked").get(0);
            String bJob = rows(b, "select id from lk_job where status = 'READY' order by id limit 1 for update skip locked").get(0);
            skipped = "서버 A가 가져간 작업: " + aJob + "\n서버 B가 가져간 작업: " + bJob;
            assertThat(aJob).isEqualTo("1");
            assertThat(bJob).isEqualTo("2");
            a.rollback();
            b.rollback();
        }
        assertThat(nowait).startsWith("55P03");
        CiOutput.record(LESSON, "nowait", "[for update nowait]\n" + nowait + "\n\n[for update skip locked]\n" + skipped);
    }

    @Test
    @DisplayName("@Lock 조회를 트랜잭션 없이 부르면 예외가 난다")
    void lockWithoutTransaction() {
        Long id = plainRepo.save(new PlainStock(100)).getId();
        Throwable thrown = catchThrowable(() -> plainRepo.findForUpdate(id));
        assertThat(thrown).isNotNull();
        CiOutput.record(LESSON, "no-tx", thrown.getClass().getName() + "\n" + thrown.getMessage().lines().findFirst().orElse(""));
    }
}
