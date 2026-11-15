package lab.devlessons.connpool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/** 레슨 "DB 커넥션과 커넥션 풀"의 주장을 PostgreSQL 16과 HikariCP로 검증한다. */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class ConnectionPoolTest {

    @Autowired DataSource dataSource;
    @Autowired TransactionTemplate tx;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private HikariDataSource app() throws SQLException {
        return dataSource.unwrap(HikariDataSource.class);
    }

    /** 같은 DB를 가리키는 작은 풀을 따로 만든다 */
    private HikariDataSource smallPool(int size, long timeoutMs) throws SQLException {
        HikariDataSource base = app();
        HikariConfig c = new HikariConfig();
        c.setJdbcUrl(base.getJdbcUrl());
        c.setUsername(base.getUsername());
        c.setPassword(base.getPassword());
        c.setMaximumPoolSize(size);
        c.setMinimumIdle(size);
        c.setConnectionTimeout(Math.max(250, timeoutMs));
        c.setPoolName("lesson-" + size);
        return new HikariDataSource(c);
    }

    private static int backendPid(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("select pg_backend_pid()")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    @DisplayName("연결을 새로 만드는 것은 풀에서 빌리는 것보다 훨씬 느리다 (실측, 출력 공개)")
    void newConnectionIsSlowerThanPooled() throws Exception {
        HikariDataSource base = app();
        int n = 20;
        try (Connection warm = DriverManager.getConnection(base.getJdbcUrl(), base.getUsername(), base.getPassword())) {
            backendPid(warm); // 드라이버 준비
        }
        long s1 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            try (Connection c = DriverManager.getConnection(base.getJdbcUrl(), base.getUsername(), base.getPassword())) {
                backendPid(c);
            }
        }
        double fresh = (System.nanoTime() - s1) / 1_000_000.0 / n;

        try (HikariDataSource pool = smallPool(2, 1000)) {
            try (Connection c = pool.getConnection()) { backendPid(c); }
            long s2 = System.nanoTime();
            for (int i = 0; i < n; i++) {
                try (Connection c = pool.getConnection()) {
                    backendPid(c);
                }
            }
            double pooled = (System.nanoTime() - s2) / 1_000_000.0 / n;

            assertThat(fresh).isGreaterThan(pooled);
            CiOutput.record("connection-pool", "timing", String.format(
                    "연결을 새로 만들고 SELECT 한 번 (20회 평균): %.2fms%n풀에서 빌리고 SELECT 한 번 (20회 평균): %.2fms%n약 %.0f배 차이",
                    fresh, pooled, fresh / pooled));
        }
    }

    @Test
    @DisplayName("close()한 연결은 끊어지지 않고 풀로 돌아가, 다음에 같은 DB 연결(같은 서버 프로세스)이 재사용된다")
    void closeReturnsToPool() throws Exception {
        try (HikariDataSource pool = smallPool(1, 1000)) {
            int first;
            try (Connection c = pool.getConnection()) { first = backendPid(c); }
            int second;
            try (Connection c = pool.getConnection()) { second = backendPid(c); }

            assertThat(second).isEqualTo(first);
            CiOutput.record("connection-pool", "reuse",
                    "1번째 getConnection → DB 서버 프로세스 " + first + "\nclose()\n2번째 getConnection → DB 서버 프로세스 " + second
                            + "\n(같은 번호 = 같은 연결을 다시 씀)");
        }
    }

    @Test
    @DisplayName("동시에 쥔 연결은 DB 쪽에서 각각 다른 서버 프로세스다")
    void eachConnectionIsAServerProcess() throws Exception {
        try (HikariDataSource pool = smallPool(2, 1000);
             Connection a = pool.getConnection(); Connection b = pool.getConnection()) {
            assertThat(backendPid(a)).isNotEqualTo(backendPid(b));
        }
    }

    @Test
    @DisplayName("풀의 연결이 모두 쓰이면 다음 요청은 기다리고, 시간 안에 못 받으면 예외로 실패한다 (출력 공개)")
    void exhaustedPoolTimesOut() throws Exception {
        try (HikariDataSource pool = smallPool(2, 500)) {
            Connection a = pool.getConnection();
            Connection b = pool.getConnection();
            long s = System.nanoTime();
            assertThatThrownBy(pool::getConnection).isInstanceOf(SQLTransientConnectionException.class)
                    .satisfies(e -> CiOutput.record("connection-pool", "timeout", String.format(
                            "풀 크기 2, 기다리는 최대 시간 500ms%n요청 1 → 연결 받음%n요청 2 → 연결 받음%n요청 3 → %dms 기다린 뒤 실패:%n  %s",
                            (System.nanoTime() - s) / 1_000_000, e.getMessage())));
            long waited = (System.nanoTime() - s) / 1_000_000;
            assertThat(waited).isGreaterThanOrEqualTo(450);
            a.close();
            b.close();
        }
    }

    @Test
    @DisplayName("기다리던 요청은 누군가 연결을 반납하는 순간 그 연결을 받는다")
    void waiterGetsReturnedConnection() throws Exception {
        try (HikariDataSource pool = smallPool(1, 3000)) {
            Connection held = pool.getConnection();
            int pid = backendPid(held);
            long s = System.nanoTime();
            CompletableFuture<Integer> waiter = CompletableFuture.supplyAsync(() -> {
                try (Connection c = pool.getConnection()) {
                    return backendPid(c);
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            });
            Thread.sleep(300);
            assertThat(waiter).isNotDone();
            held.close();

            assertThat(waiter.get(2, TimeUnit.SECONDS)).isEqualTo(pid);
            assertThat((System.nanoTime() - s) / 1_000_000).isGreaterThanOrEqualTo(300);
        }
    }

    @Test
    @DisplayName("스프링 트랜잭션은 시작부터 끝까지 연결 하나를 쥐고, 끝나면 풀에 돌려준다")
    void transactionHoldsOneConnection() throws Exception {
        HikariDataSource pool = app();
        int inside = tx.execute(status -> {
            jdbc.queryForObject("select 1", Integer.class);   // 트랜잭션 안에서 SQL을 보내면 연결을 쥔다
            return pool.getHikariPoolMXBean().getActiveConnections();
        });
        int after = pool.getHikariPoolMXBean().getActiveConnections();

        assertThat(inside).isEqualTo(1);
        assertThat(after).isZero();
        assertThat(pool.getMaximumPoolSize()).isEqualTo(10); // HikariCP 기본값
    }
}
