package lab.devlessons.isolation;

import static java.sql.Connection.TRANSACTION_READ_COMMITTED;
import static java.sql.Connection.TRANSACTION_READ_UNCOMMITTED;
import static java.sql.Connection.TRANSACTION_REPEATABLE_READ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 레슨 "격리 수준과 이상 현상"의 주장을 PostgreSQL 16에서 검증한다.
 * 커넥션 두 개로 트랜잭션 T1, T2를 직접 열고 순서를 정해 실행한다.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class IsolationLevelTest {

    @Autowired DataSource dataSource;

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("create table if not exists iso_account (id int primary key, balance int not null)");
            s.execute("delete from iso_account");
            s.execute("insert into iso_account values (1, 100)");
        }
    }

    @Test
    @DisplayName("PostgreSQL의 기본 격리 수준은 READ COMMITTED")
    void defaultIsReadCommitted() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("show default_transaction_isolation")) {
            rs.next();
            assertThat(rs.getString(1)).isEqualTo("read committed");
        }
    }

    @Test
    @DisplayName("Dirty Read 없음: READ UNCOMMITTED로 열어도 다른 트랜잭션의 커밋 전 값은 보이지 않는다 (PostgreSQL)")
    void noDirtyRead() throws SQLException {
        try (Connection t1 = tx(TRANSACTION_READ_COMMITTED); Connection t2 = tx(TRANSACTION_READ_UNCOMMITTED)) {
            update(t1, "update iso_account set balance = 999 where id = 1"); // 커밋 전

            assertThat(balance(t2)).isEqualTo(100);
            t1.rollback();
            t2.commit();
        }
    }

    @Test
    @DisplayName("READ COMMITTED: 같은 트랜잭션에서 두 번 읽으면 그 사이 커밋된 값이 보인다 (Non-Repeatable Read)")
    void nonRepeatableReadAtReadCommitted() throws SQLException {
        try (Connection t1 = tx(TRANSACTION_READ_COMMITTED); Connection t2 = tx(TRANSACTION_READ_COMMITTED)) {
            assertThat(balance(t1)).isEqualTo(100);

            update(t2, "update iso_account set balance = 200 where id = 1");
            t2.commit();

            assertThat(balance(t1)).isEqualTo(200);
            t1.commit();
        }
    }

    @Test
    @DisplayName("REPEATABLE READ: 트랜잭션 시작 시점의 스냅샷을 계속 본다")
    void repeatableReadKeepsSnapshot() throws SQLException {
        try (Connection t1 = tx(TRANSACTION_REPEATABLE_READ); Connection t2 = tx(TRANSACTION_READ_COMMITTED)) {
            assertThat(balance(t1)).isEqualTo(100);

            update(t2, "update iso_account set balance = 200 where id = 1");
            t2.commit();

            assertThat(balance(t1)).isEqualTo(100);
            t1.commit();
        }
    }

    @Test
    @DisplayName("READ COMMITTED: 같은 조건으로 다시 세면 그 사이 추가된 행이 보인다 (Phantom Read)")
    void phantomAtReadCommitted() throws SQLException {
        try (Connection t1 = tx(TRANSACTION_READ_COMMITTED); Connection t2 = tx(TRANSACTION_READ_COMMITTED)) {
            assertThat(count(t1)).isEqualTo(1);

            update(t2, "insert into iso_account values (2, 50)");
            t2.commit();

            assertThat(count(t1)).isEqualTo(2);
            t1.commit();
        }
    }

    @Test
    @DisplayName("PostgreSQL의 REPEATABLE READ는 Phantom Read도 막는다 (SQL 표준은 허용)")
    void noPhantomAtRepeatableReadInPostgres() throws SQLException {
        try (Connection t1 = tx(TRANSACTION_REPEATABLE_READ); Connection t2 = tx(TRANSACTION_READ_COMMITTED)) {
            assertThat(count(t1)).isEqualTo(1);

            update(t2, "insert into iso_account values (2, 50)");
            t2.commit();

            assertThat(count(t1)).isEqualTo(1);
            t1.commit();
        }
    }

    @Test
    @DisplayName("READ COMMITTED에서 앱이 읽고 계산해서 쓰면 갱신 손실: +50, +30 했는데 130")
    void lostUpdateWithReadModifyWrite() throws SQLException {
        try (Connection t1 = tx(TRANSACTION_READ_COMMITTED); Connection t2 = tx(TRANSACTION_READ_COMMITTED)) {
            int b1 = balance(t1); // 100
            int b2 = balance(t2); // 100

            update(t1, "update iso_account set balance = " + (b1 + 50) + " where id = 1");
            t1.commit();
            update(t2, "update iso_account set balance = " + (b2 + 30) + " where id = 1");
            t2.commit();
        }
        assertThat(currentBalance()).isEqualTo(130);
    }

    @Test
    @DisplayName("REPEATABLE READ에서 같은 행을 먼저 커밋한 트랜잭션이 있으면 나중 UPDATE는 직렬화 오류(40001)로 실패한다")
    void repeatableReadDetectsConcurrentUpdate() throws SQLException {
        try (Connection t1 = tx(TRANSACTION_REPEATABLE_READ); Connection t2 = tx(TRANSACTION_REPEATABLE_READ)) {
            int b1 = balance(t1);
            int b2 = balance(t2);

            update(t1, "update iso_account set balance = " + (b1 + 50) + " where id = 1");
            t1.commit();

            assertThatThrownBy(() -> update(t2, "update iso_account set balance = " + (b2 + 30) + " where id = 1"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("40001"));
            t2.rollback();
        }
        assertThat(currentBalance()).isEqualTo(150);
    }

    @Test
    @DisplayName("원자적 UPDATE(balance = balance + n)는 READ COMMITTED에서도 정확: 뒤 트랜잭션은 앞 트랜잭션 커밋까지 기다렸다가 최신 값에 더한다")
    void atomicUpdateWaitsAndApplies() throws Exception {
        try (Connection t1 = tx(TRANSACTION_READ_COMMITTED); Connection t2 = tx(TRANSACTION_READ_COMMITTED)) {
            update(t1, "update iso_account set balance = balance + 50 where id = 1"); // 행 락을 잡고 커밋 전

            CompletableFuture<Void> second = CompletableFuture.runAsync(() -> {
                try {
                    update(t2, "update iso_account set balance = balance + 30 where id = 1");
                    t2.commit();
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            });
            Thread.sleep(300);
            assertThat(second.isDone()).as("T1이 커밋하기 전에는 T2가 기다린다").isFalse();

            t1.commit();
            second.get(10, TimeUnit.SECONDS);
        }
        assertThat(currentBalance()).isEqualTo(180);
    }

    @Test
    @DisplayName("REPEATABLE READ에서는 원자적 UPDATE도 앞 트랜잭션이 커밋하면 직렬화 오류(40001)로 실패한다 (재시도 필요)")
    void atomicUpdateAtRepeatableReadFails() throws Exception {
        try (Connection t1 = tx(TRANSACTION_REPEATABLE_READ); Connection t2 = tx(TRANSACTION_REPEATABLE_READ)) {
            update(t1, "update iso_account set balance = balance + 50 where id = 1");

            CompletableFuture<String> second = CompletableFuture.supplyAsync(() -> {
                try {
                    update(t2, "update iso_account set balance = balance + 30 where id = 1");
                    return "ok";
                } catch (SQLException e) {
                    return e.getSQLState();
                }
            });
            Thread.sleep(300);
            assertThat(second.isDone()).isFalse();

            t1.commit();
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo("40001");
            t2.rollback();
        }
        assertThat(currentBalance()).isEqualTo(150);
    }

    private Connection tx(int isolation) throws SQLException {
        Connection c = dataSource.getConnection();
        c.setAutoCommit(false);
        c.setTransactionIsolation(isolation);
        return c;
    }

    private static int balance(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("select balance from iso_account where id = 1")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static int count(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("select count(*) from iso_account where balance >= 0")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void update(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate(sql);
        }
    }

    private int currentBalance() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            return balance(c);
        }
    }
}
