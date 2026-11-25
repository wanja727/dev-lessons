package lab.devlessons.mvcc;

import static java.sql.Connection.TRANSACTION_READ_COMMITTED;
import static java.sql.Connection.TRANSACTION_REPEATABLE_READ;
import static lab.devlessons.support.Jdbc.exec;
import static lab.devlessons.support.Jdbc.number;
import static lab.devlessons.support.Jdbc.rows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
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

/**
 * 레슨 "MVCC"(D13)의 주장을 PostgreSQL 16에서 검증한다.
 * 계좌 테이블 mv_account 하나. 숨은 열(xmin, xmax, ctid)과 통계로 행의 버전을 본다.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class MvccTest {

    private static final String LESSON = "mvcc";

    @Autowired DataSource ds;

    @BeforeEach
    void setUp() {
        exec(ds, "drop table if exists mv_account",
                "create table mv_account (owner text primary key, balance int not null) with (autovacuum_enabled = false)",
                "insert into mv_account values ('kim', 100)");
    }

    private Connection tx(int level) throws SQLException {
        Connection c = ds.getConnection();
        c.setAutoCommit(false);
        c.setTransactionIsolation(level);
        return c;
    }

    private static final String HIDDEN = "select xmin, xmax, ctid, balance from mv_account where owner = 'kim'";

    @Test
    @DisplayName("A가 UPDATE하고 커밋 전에, B의 일반 SELECT는 기다리지 않고 옛 값 100을 읽는다. FOR UPDATE는 기다린다")
    void readersDoNotWait() throws Exception {
        List<String> log = new ArrayList<>();
        try (Connection a = tx(TRANSACTION_READ_COMMITTED); Connection b = tx(TRANSACTION_READ_COMMITTED);
             Connection c = tx(TRANSACTION_READ_COMMITTED)) {
            exec(a, "update mv_account set balance = 50 where owner = 'kim'");
            log.add("A: update balance = 50 (커밋 전, 행을 잠금)");
            long t = System.nanoTime();
            long seen = number(b, "select balance from mv_account where owner = 'kim'");
            long ms = (System.nanoTime() - t) / 1_000_000;
            log.add("B: 일반 select → " + seen + " (" + ms + "ms)");
            CompletableFuture<Long> forUpdate = CompletableFuture.supplyAsync(() -> {
                try {
                    return number(c, "select balance from mv_account where owner = 'kim' for update");
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            });
            Thread.sleep(500);
            boolean waiting = !forUpdate.isDone();
            log.add("C: select ... for update → " + (waiting ? "기다림" : "바로 끝남"));
            a.commit();
            log.add("A: commit");
            log.add("C: → " + forUpdate.get(5, TimeUnit.SECONDS) + " (A가 끝난 뒤)");
            b.commit();
            c.commit();
            assertThat(seen).isEqualTo(100);
            assertThat(ms).isLessThan(500);
            assertThat(waiting).isTrue();
        }
        CiOutput.record(LESSON, "no-wait", String.join("\n", log));
    }

    @Test
    @DisplayName("UPDATE하면 ctid가 바뀐 새 버전이 생기고 xmin은 A의 트랜잭션 id다. 커밋 전 B는 옛 버전(xmax에 A의 id)을 본다")
    void hiddenColumns() throws SQLException {
        List<String> log = new ArrayList<>();
        log.add("(xmin | xmax | ctid | balance)");
        log.add("UPDATE 전: " + rows(ds, HIDDEN).get(0));
        try (Connection a = tx(TRANSACTION_READ_COMMITTED); Connection b = tx(TRANSACTION_READ_COMMITTED)) {
            exec(a, "update mv_account set balance = 50 where owner = 'kim'");
            String aXid = rows(a, "select pg_current_xact_id()").get(0);
            String aSees = rows(a, HIDDEN).get(0);
            String bSees = rows(b, HIDDEN).get(0);
            log.add("A의 트랜잭션 id: " + aXid);
            log.add("커밋 전 A가 보는 것: " + aSees);
            log.add("커밋 전 B가 보는 것: " + bSees);
            a.commit();
            b.commit();
            assertThat(aSees).startsWith(aXid + " | 0 |");
            assertThat(bSees).contains("| " + aXid + " |").endsWith("| 100");
        }
        String after = rows(ds, HIDDEN).get(0);
        log.add("커밋 뒤: " + after);
        CiOutput.record(LESSON, "hidden", String.join("\n", log));
        assertThat(after).endsWith("| 50");
    }

    @Test
    @DisplayName("같은 행을 1000번 UPDATE하면 죽은 행이 생기고, VACUUM하면 0이 된다")
    void deadTuplesAndVacuum() throws SQLException {
        for (int i = 0; i < 1000; i++) {
            exec(ds, "update mv_account set balance = balance + 1 where owner = 'kim'");
        }
        long dead = deadTuples();
        exec(ds, "vacuum mv_account");
        long afterVacuum = deadTuples();

        assertThat(dead).isPositive();
        assertThat(afterVacuum).isZero();
        CiOutput.record(LESSON, "dead", "UPDATE 1000번 뒤 죽은 행(n_dead_tup): " + dead + "\nVACUUM 뒤: " + afterVacuum);
    }

    private long deadTuples() throws SQLException {
        try (Connection c = ds.getConnection()) {
            exec(c, "select pg_stat_force_next_flush()");
            return number(c, "select n_dead_tup from pg_stat_user_tables where relname = 'mv_account'");
        }
    }

    @Test
    @DisplayName("열린 REPEATABLE READ 트랜잭션이 있으면 VACUUM이 죽은 행을 치우지 못하고, 끝내면 치운다")
    void longTransactionBlocksVacuum() throws SQLException {
        String blocked;
        String freed;
        long sizeBlocked;
        try (Connection longTx = tx(TRANSACTION_REPEATABLE_READ)) {
            number(longTx, "select balance from mv_account where owner = 'kim'");
            for (int i = 0; i < 1000; i++) {
                exec(ds, "update mv_account set balance = balance + 1 where owner = 'kim'");
            }
            blocked = vacuumVerbose();
            sizeBlocked = number(ds, "select pg_relation_size('mv_account')");
            longTx.commit();
        }
        freed = vacuumVerbose();
        assertThat(blocked).containsPattern("are dead but not yet removable|dead row versions cannot be removed yet");
        CiOutput.record(LESSON, "long-tx", "[긴 트랜잭션이 열려 있을 때 VACUUM]\n" + blocked
                + "\n테이블 크기: " + sizeBlocked + " bytes\n\n[긴 트랜잭션을 끝낸 뒤 VACUUM]\n" + freed);
    }

    /** VACUUM VERBOSE의 메시지 가운데 행 버전에 관한 줄 */
    private String vacuumVerbose() throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("vacuum (verbose) mv_account");
            List<String> lines = new ArrayList<>();
            for (SQLWarning w = s.getWarnings(); w != null; w = w.getNextWarning()) {
                w.getMessage().lines().filter(l -> l.contains("tuples:"))
                        .map(String::trim).forEach(lines::add);
            }
            return String.join("\n", lines);
        }
    }

    @Test
    @DisplayName("REPEATABLE READ의 B가 기다리던 행을 A가 커밋하면 40001이고, A가 롤백하면 B는 성공한다")
    void writeWriteUnderRepeatableRead() throws Exception {
        String committed = rrScenario(true);
        exec(ds, "update mv_account set balance = 100");
        String rolledBack = rrScenario(false);

        assertThat(committed).contains("40001");
        assertThat(rolledBack).contains("성공");
        CiOutput.record(LESSON, "write-write", "[A가 커밋한 경우]\n" + committed + "\n\n[A가 롤백한 경우]\n" + rolledBack);
    }

    private String rrScenario(boolean commitA) throws Exception {
        try (Connection a = tx(TRANSACTION_READ_COMMITTED); Connection b = tx(TRANSACTION_REPEATABLE_READ)) {
            number(b, "select balance from mv_account where owner = 'kim'");
            exec(a, "update mv_account set balance = 50 where owner = 'kim'");
            CompletableFuture<String> bUpdate = CompletableFuture.supplyAsync(() -> {
                try {
                    exec(b, "update mv_account set balance = balance + 10 where owner = 'kim'");
                    b.commit();
                    return "B: update → 성공, 결과 " + number(ds, "select balance from mv_account where owner = 'kim'");
                } catch (SQLException e) {
                    try {
                        b.rollback();
                    } catch (SQLException ignored) {
                        // 무시
                    }
                    return "B: update → " + e.getSQLState() + " " + e.getMessage().lines().findFirst().orElse("");
                }
            });
            Thread.sleep(300);
            boolean waited = !bUpdate.isDone();
            if (commitA) {
                a.commit();
            } else {
                a.rollback();
            }
            String result = bUpdate.get(5, TimeUnit.SECONDS);
            return "B(REPEATABLE READ): select → 100\nA: update balance = 50\nB: update → " + (waited ? "기다림" : "바로")
                    + "\nA: " + (commitA ? "commit" : "rollback") + "\n" + result;
        }
    }

    @Test
    @DisplayName("idle_in_transaction_session_timeout이 지나면 트랜잭션을 연 채 놀던 세션이 끊긴다")
    void idleInTransactionTimeout() throws Exception {
        String result;
        try (Connection c = ds.getConnection()) {
            exec(c, "set idle_in_transaction_session_timeout = '500ms'");
            c.setAutoCommit(false);
            number(c, "select balance from mv_account where owner = 'kim'");
            Thread.sleep(1500);
            Throwable t = catchThrowable(() -> number(c, "select 1"));
            result = t == null ? "끊기지 않음" : t.getClass().getSimpleName() + ": " + t.getMessage().lines().findFirst().orElse("");
            try {
                c.setAutoCommit(true);
            } catch (SQLException ignored) {
                // 이미 끊긴 연결
            }
        }
        assertThat(result).isNotEqualTo("끊기지 않음");
        CiOutput.record(LESSON, "idle", "set idle_in_transaction_session_timeout = '500ms'\nbegin; select ...; (1.5초 동안 아무것도 안 함)\nselect 1 →\n" + result);
    }
}
