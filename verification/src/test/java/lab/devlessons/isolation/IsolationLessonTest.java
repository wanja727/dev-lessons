package lab.devlessons.isolation;

import static java.sql.Connection.TRANSACTION_READ_COMMITTED;
import static java.sql.Connection.TRANSACTION_READ_UNCOMMITTED;
import static java.sql.Connection.TRANSACTION_REPEATABLE_READ;
import static java.sql.Connection.TRANSACTION_SERIALIZABLE;
import static lab.devlessons.support.Jdbc.exec;
import static lab.devlessons.support.Jdbc.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 레슨 "격리 수준과 이상 현상"(D9)을 다시 쓰며, 레슨에 붙일 실제 출력과 새 주장을 남긴다.
 * 예전 주장은 IsolationLevelTest가 그대로 확인한다. 연결 두 개로 A와 B를 열고 문장 순서를 맞춘다.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class IsolationLessonTest {

    private static final String LESSON = "isolation-level";

    @Autowired DataSource ds;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    @BeforeEach
    void setUp() {
        exec(ds, "drop table if exists il_account, il_coupon, il_doctor",
                "create table il_account (owner text primary key, balance int not null)",
                "insert into il_account values ('kim', 100)",
                "create table il_coupon (id int primary key, amount int not null)",
                "insert into il_coupon values (1, 2000), (2, 500)",
                "create table il_doctor (name text primary key, on_call boolean not null)",
                "insert into il_doctor values ('alice', true), ('bob', true)");
    }

    private Connection tx(int level) throws SQLException {
        Connection c = ds.getConnection();
        c.setAutoCommit(false);
        c.setTransactionIsolation(level);
        return c;
    }

    private long balance(Connection c) throws SQLException {
        return number(c, "select balance from il_account where owner = 'kim'");
    }

    @Test
    @DisplayName("READ COMMITTED: A가 읽은 뒤 B가 50으로 바꾸고 커밋하면 A의 두 번째 읽기는 50이다. REPEATABLE READ면 100 그대로다")
    void readCommittedVsRepeatableRead() throws SQLException {
        List<String> rc = scenario(TRANSACTION_READ_COMMITTED);
        exec(ds, "update il_account set balance = 100");
        List<String> rr = scenario(TRANSACTION_REPEATABLE_READ);

        assertThat(rc.get(rc.size() - 1)).endsWith("50");
        assertThat(rr.get(rr.size() - 1)).endsWith("100");
        CiOutput.record(LESSON, "read-committed", String.join("\n", rc));
        CiOutput.record(LESSON, "repeatable-read", String.join("\n", rr));
    }

    private List<String> scenario(int level) throws SQLException {
        List<String> log = new ArrayList<>();
        try (Connection a = tx(level); Connection b = tx(TRANSACTION_READ_COMMITTED)) {
            log.add("A: select balance → " + balance(a));
            exec(b, "update il_account set balance = 50 where owner = 'kim'");
            b.commit();
            log.add("B: update balance = 50, commit");
            log.add("A: select balance → " + balance(a));
            a.commit();
        }
        return log;
    }

    @Test
    @DisplayName("READ UNCOMMITTED로 열어도 B의 커밋 전 값은 보이지 않는다(PostgreSQL)")
    void noDirtyRead() throws SQLException {
        String seen;
        try (Connection a = tx(TRANSACTION_READ_UNCOMMITTED); Connection b = tx(TRANSACTION_READ_COMMITTED)) {
            exec(b, "update il_account set balance = 999 where owner = 'kim'");
            seen = "B: update balance = 999 (커밋 전)\nA(READ UNCOMMITTED): select balance → " + balance(a);
            b.rollback();
            a.commit();
        }
        assertThat(seen).endsWith("100");
        CiOutput.record(LESSON, "dirty", seen + "\nB: rollback");
    }

    @Test
    @DisplayName("Phantom Read: READ COMMITTED는 다시 센 수가 늘고, REPEATABLE READ는 그대로다")
    void phantom() throws SQLException {
        String rc = phantomScenario(TRANSACTION_READ_COMMITTED, 3);
        exec(ds, "delete from il_coupon where id = 3");
        String rr = phantomScenario(TRANSACTION_REPEATABLE_READ, 3);

        assertThat(rc).endsWith("→ 2");
        assertThat(rr).endsWith("→ 1");
        CiOutput.record(LESSON, "phantom", "[READ COMMITTED]\n" + rc + "\n\n[REPEATABLE READ]\n" + rr);
    }

    private String phantomScenario(int level, int newId) throws SQLException {
        String sql = "select count(*) from il_coupon where amount > 1000";
        try (Connection a = tx(level); Connection b = tx(TRANSACTION_READ_COMMITTED)) {
            long first = number(a, sql);
            exec(b, "insert into il_coupon values (" + newId + ", 3000)");
            b.commit();
            long second = number(a, sql);
            a.commit();
            return "A: count(amount > 1000) → " + first + "\nB: insert 3000원 쿠폰, commit\nA: count(amount > 1000) → " + second;
        }
    }

    @Test
    @DisplayName("REPEATABLE READ에서 B가 먼저 고치고 커밋한 행을 A가 UPDATE하면 40001로 실패한다")
    void serializationFailure() throws SQLException {
        String error;
        try (Connection a = tx(TRANSACTION_REPEATABLE_READ); Connection b = tx(TRANSACTION_READ_COMMITTED)) {
            balance(a);
            exec(b, "update il_account set balance = 50 where owner = 'kim'");
            b.commit();
            SQLException e = (SQLException) catchThrowable(() -> exec(a, "update il_account set balance = balance + 10 where owner = 'kim'"));
            error = e.getSQLState() + " " + e.getMessage().lines().findFirst().orElse("");
            a.rollback();
        }
        assertThat(error).startsWith("40001");
        CiOutput.record(LESSON, "serialization", "A(REPEATABLE READ): select balance → 100\nB: update balance = 50, commit\n"
                + "A: update balance = balance + 10\n→ " + error);
    }

    @Test
    @DisplayName("갱신 손실: READ COMMITTED에서 읽고 계산해서 쓰면 130, 원자적 UPDATE는 A의 커밋을 기다렸다가 180")
    void lostUpdateVsAtomic() throws Exception {
        try (Connection a = tx(TRANSACTION_READ_COMMITTED); Connection b = tx(TRANSACTION_READ_COMMITTED)) {
            long ra = balance(a);
            long rb = balance(b);
            exec(a, "update il_account set balance = " + (ra + 50) + " where owner = 'kim'");
            a.commit();
            exec(b, "update il_account set balance = " + (rb + 30) + " where owner = 'kim'");
            b.commit();
        }
        long lost = number(ds, "select balance from il_account");

        exec(ds, "update il_account set balance = 100");
        long waitedMs;
        try (Connection a = tx(TRANSACTION_READ_COMMITTED); Connection b = tx(TRANSACTION_READ_COMMITTED)) {
            exec(a, "update il_account set balance = balance + 50 where owner = 'kim'");
            long t = System.nanoTime();
            CompletableFuture<Void> bUpdate = CompletableFuture.runAsync(() -> {
                try {
                    exec(b, "update il_account set balance = balance + 30 where owner = 'kim'");
                    b.commit();
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            });
            Thread.sleep(500);
            assertThat(bUpdate).isNotDone();
            a.commit();
            bUpdate.get(5, TimeUnit.SECONDS);
            waitedMs = (System.nanoTime() - t) / 1_000_000;
        }
        long atomic = number(ds, "select balance from il_account");

        assertThat(lost).isEqualTo(130);
        assertThat(atomic).isEqualTo(180);
        CiOutput.record(LESSON, "lost-update", String.join("\n",
                "[읽고 계산해서 쓰기] A가 100을 읽고 150을, B가 100을 읽고 130을 씀 → 결과 " + lost,
                "[원자적 UPDATE] A: +50, B: +30 → 결과 " + atomic + " (B는 A의 커밋까지 약 " + waitedMs + "ms 기다림)"));
    }

    @Test
    @DisplayName("쓰기 치우침: 당직 둘이 동시에 빠지면 REPEATABLE READ에서는 둘 다 빠지고, SERIALIZABLE에서는 한쪽이 40001로 실패한다")
    void writeSkew() throws SQLException {
        String rr = writeSkewScenario(TRANSACTION_REPEATABLE_READ);
        exec(ds, "update il_doctor set on_call = true");
        String sz = writeSkewScenario(TRANSACTION_SERIALIZABLE);

        assertThat(rr).endsWith("당직 0명");
        assertThat(sz).contains("40001").endsWith("당직 1명");
        CiOutput.record(LESSON, "write-skew", "[REPEATABLE READ]\n" + rr + "\n\n[SERIALIZABLE]\n" + sz);
    }

    private String writeSkewScenario(int level) throws SQLException {
        List<String> log = new ArrayList<>();
        String count = "select count(*) from il_doctor where on_call";
        try (Connection a = tx(level); Connection b = tx(level)) {
            log.add("alice: 당직 수 → " + number(a, count) + ", 2명 이상이니 빠진다");
            log.add("bob: 당직 수 → " + number(b, count) + ", 2명 이상이니 빠진다");
            exec(a, "update il_doctor set on_call = false where name = 'alice'");
            a.commit();
            log.add("alice: on_call = false, commit 성공");
            try {
                exec(b, "update il_doctor set on_call = false where name = 'bob'");
                b.commit();
                log.add("bob: on_call = false, commit 성공");
            } catch (SQLException e) {
                log.add("bob: on_call = false에서 실패 " + e.getSQLState() + " " + e.getMessage().lines().findFirst().orElse(""));
            }
        }
        log.add("결과: 당직 " + number(ds, count) + "명");
        return String.join("\n", log);
    }

    @Test
    @DisplayName("스프링 TransactionTemplate에 REPEATABLE_READ를 주면 그 연결의 격리 수준이 바뀌고, 40001은 ConcurrencyFailureException 계열 예외가 된다")
    void springIsolation() throws Exception {
        TransactionTemplate rr = new TransactionTemplate(txManager);
        rr.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        String inside = rr.execute(s -> jdbc.queryForObject("show transaction_isolation", String.class));
        String outside = jdbc.queryForObject("show transaction_isolation", String.class);

        Throwable thrown;
        try (Connection b = tx(TRANSACTION_READ_COMMITTED)) {
            thrown = catchThrowable(() -> rr.executeWithoutResult(s -> {
                jdbc.queryForObject("select balance from il_account where owner = 'kim'", Long.class);
                try {
                    exec(b, "update il_account set balance = 50 where owner = 'kim'");
                    b.commit();
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
                jdbc.update("update il_account set balance = balance + 10 where owner = 'kim'");
            }));
        }

        assertThat(inside).isEqualTo("repeatable read");
        assertThat(outside).isEqualTo("read committed");
        assertThat(thrown).isInstanceOf(ConcurrencyFailureException.class);
        CiOutput.record(LESSON, "spring", String.join("\n",
                "트랜잭션 안: " + inside,
                "트랜잭션 밖: " + outside,
                "40001을 받은 스프링 예외: " + thrown.getClass().getName()));
    }
}
