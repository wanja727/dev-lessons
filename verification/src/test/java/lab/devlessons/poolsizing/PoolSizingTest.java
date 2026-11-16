package lab.devlessons.poolsizing;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 레슨 "커넥션 풀과 톰캣 스레드 풀 크기"의 주장을 PostgreSQL 16과 HikariCP로 검증한다. */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class PoolSizingTest {

    static final String LESSON = "pool-sizing";

    @Autowired DataSource dataSource;

    private HikariDataSource app() throws SQLException {
        return dataSource.unwrap(HikariDataSource.class);
    }

    /** 같은 DB를 가리키는 작은 풀을 따로 만든다 */
    private HikariDataSource pool(int size, long timeoutMs) throws SQLException {
        HikariDataSource base = app();
        HikariConfig c = new HikariConfig();
        c.setJdbcUrl(base.getJdbcUrl());
        c.setUsername(base.getUsername());
        c.setPassword(base.getPassword());
        c.setMaximumPoolSize(size);
        c.setMinimumIdle(size);
        c.setConnectionTimeout(Math.max(250, timeoutMs));
        c.setPoolName("sizing-" + size);
        return new HikariDataSource(c);
    }

    @Test
    @DisplayName("기본값: 스프링 부트의 톰캣 최대 스레드는 200개, HikariCP 최대 커넥션은 10개")
    void defaults() throws Exception {
        int tomcatMax = new ServerProperties().getTomcat().getThreads().getMax();
        int hikariMax = app().getMaximumPoolSize();

        assertThat(tomcatMax).isEqualTo(200);
        assertThat(hikariMax).isEqualTo(10);
        CiOutput.record(LESSON, "defaults", "[스프링 부트 3.5 기본값]\n"
                + "server.tomcat.threads.max = " + tomcatMax + "\n"
                + "spring.datasource.hikari.maximum-pool-size = " + hikariMax);
    }

    /** 스레드 n개가 동시에 커넥션을 하나씩 쓰며 200ms짜리 쿼리를 실행한다. 전체 걸린 시간 */
    private long runQueries(DataSource ds, int threads) throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            fs.add(pool.submit(() -> {
                start.await();
                jdbc.execute("SELECT pg_sleep(0.2)");
                return null;
            }));
        }
        long t0 = System.nanoTime();
        start.countDown();
        for (Future<?> f : fs) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
    }

    @Test
    @DisplayName("스레드가 커넥션보다 많으면 남는 스레드는 커넥션을 기다린다: 스레드 6, 커넥션 2면 200ms 쿼리가 세 번에 나눠 실행된다")
    void moreThreadsThanConnectionsWait() throws Exception {
        long twoConnections;
        long sixConnections;
        try (HikariDataSource two = pool(2, 5_000); HikariDataSource six = pool(6, 5_000)) {
            runQueries(two, 2); // 미리 연결해 둔다
            runQueries(six, 6);
            twoConnections = runQueries(two, 6);
            sixConnections = runQueries(six, 6);
        }

        assertThat(twoConnections).isGreaterThanOrEqualTo(550);
        assertThat(sixConnections).isLessThan(450);
        CiOutput.record(LESSON, "waves", "[스레드 6개가 동시에 200ms 쿼리 1번씩]\n"
                + "커넥션 2개: " + twoConnections + "ms (2개씩 세 번)\n"
                + "커넥션 6개: " + sixConnections + "ms (한 번에)");
    }

    /**
     * 요청 n개가 동시에: 바깥 트랜잭션으로 커넥션 하나를 잡은 채, REQUIRES_NEW로 커넥션을 하나 더 요청한다.
     * 성공한 요청 수를 돌려준다.
     */
    private int nestedRequiresNew(int poolSize, int requests) throws Exception {
        try (HikariDataSource ds = pool(poolSize, 1_000)) {
            DataSourceTransactionManager tm = new DataSourceTransactionManager(ds);
            TransactionTemplate outer = new TransactionTemplate(tm);
            TransactionTemplate inner = new TransactionTemplate(tm);
            inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            CyclicBarrier allHoldOne = new CyclicBarrier(requests);
            ExecutorService threads = Executors.newFixedThreadPool(requests);
            List<Future<Boolean>> fs = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                fs.add(threads.submit(() -> {
                    try {
                        outer.executeWithoutResult(s -> {
                            jdbc.queryForObject("SELECT 1", Integer.class); // 바깥 트랜잭션이 커넥션 1개를 쥐고 있다
                            await(allHoldOne);
                            inner.executeWithoutResult(s2 -> jdbc.queryForObject("SELECT 1", Integer.class)); // 1개 더
                        });
                        return true;
                    } catch (RuntimeException e) {
                        return false; // 커넥션을 얻지 못해 타임아웃
                    }
                }));
            }
            int ok = 0;
            for (Future<Boolean> f : fs) {
                if (f.get(30, TimeUnit.SECONDS)) {
                    ok++;
                }
            }
            threads.shutdown();
            return ok;
        }
    }

    @Test
    @DisplayName("풀 고갈 데드락: 커넥션을 쥔 채 커넥션을 하나 더 요청하는 코드는, 동시 요청 수만큼만 커넥션이 있으면 모두 타임아웃으로 실패한다")
    void poolLockingDeadlock() throws Exception {
        int poolTwo = nestedRequiresNew(2, 2);
        int poolThree = nestedRequiresNew(3, 2);

        assertThat(poolTwo).isZero();
        assertThat(poolThree).isEqualTo(2);
        CiOutput.record(LESSON, "pool-locking", "[동시 요청 2개, 각자 커넥션 1개를 쥔 채 REQUIRES_NEW로 1개 더, 커넥션 대기 최대 1초]\n"
                + "커넥션 2개: 성공 " + poolTwo + " / 2 (서로의 커넥션을 기다리다 타임아웃)\n"
                + "커넥션 3개: 성공 " + poolThree + " / 2 (한 요청이 1개 더 얻어 끝내고 반납)");
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
