package lab.devlessons.dbdeadlock;

import static lab.devlessons.support.Jdbc.exec;
import static lab.devlessons.support.Jdbc.number;
import static lab.devlessons.support.Jdbc.rows;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 레슨 "DB 데드락"(D11)의 주장을 PostgreSQL 16에서 검증한다.
 * 계좌 kim(id 1)과 lee(id 2). 이체 하나는 보내는 계좌와 받는 계좌, 두 행을 차례로 고친다.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class DbDeadlockTest {

    private static final String LESSON = "db-deadlock";

    @Autowired DataSource ds;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    @BeforeEach
    void setUp() {
        exec(ds, "drop table if exists dl_order, dl_account cascade",
                "create table dl_account (id int primary key, owner text not null, balance int not null)",
                "insert into dl_account values (1, 'kim', 1000), (2, 'lee', 1000)");
    }

    private Connection tx() throws SQLException {
        Connection c = ds.getConnection();
        c.setAutoCommit(false);
        return c;
    }

    /** 두 이체가 각자 첫 UPDATE를 끝낸 뒤 둘째 UPDATE를 보내게 맞춘다. 결과: 각 쪽의 "성공" 또는 "SQLState 메시지" */
    private List<String> crossTransfer(String setup) throws Exception {
        CyclicBarrier firstDone = new CyclicBarrier(2);
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<Void> a = CompletableFuture.runAsync(() -> transfer("A(kim→lee)", 1, 2, setup, firstDone, log));
        CompletableFuture<Void> b = CompletableFuture.runAsync(() -> transfer("B(lee→kim)", 2, 1, setup, firstDone, log));
        a.get(20, TimeUnit.SECONDS);
        b.get(20, TimeUnit.SECONDS);
        return log;
    }

    private void transfer(String name, int from, int to, String setup, CyclicBarrier firstDone, List<String> log) {
        long t = System.nanoTime();
        try (Connection c = tx()) {
            if (setup != null) {
                exec(c, setup);
            }
            try {
                exec(c, "update dl_account set balance = balance - 100 where id = " + from);
                firstDone.await(5, TimeUnit.SECONDS);
                exec(c, "update dl_account set balance = balance + 100 where id = " + to);
                c.commit();
                log.add(name + ": 성공, commit (" + (System.nanoTime() - t) / 1_000_000 + "ms)");
            } catch (SQLException e) {
                c.rollback();
                log.add(name + ": " + e.getSQLState() + " " + e.getMessage().lines().findFirst().orElse("")
                        + " (" + (System.nanoTime() - t) / 1_000_000 + "ms)");
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("서로 반대 순서로 두 행을 UPDATE하면 1초쯤 뒤 한쪽이 40P01로 실패하고 다른 쪽은 커밋한다. 실패한 쪽은 전부 롤백되어 잔액 합이 그대로다")
    void deadlockDetected() throws Exception {
        List<String> log = crossTransfer(null);
        List<String> balances = rows(ds, "select owner, balance from dl_account order by id");
        long sum = number(ds, "select sum(balance) from dl_account");

        assertThat(log).hasSize(2);
        assertThat(log).filteredOn(l -> l.contains("40P01")).hasSize(1);
        assertThat(log).filteredOn(l -> l.contains("성공")).hasSize(1);
        assertThat(sum).isEqualTo(2000);
        Collections.sort(log);
        CiOutput.record(LESSON, "deadlock", String.join("\n", log) + "\n\n잔액 (owner | balance): " + balances + "\n합계: " + sum);
    }

    @Test
    @DisplayName("deadlock_timeout을 200ms로 줄이면 오류까지 걸리는 시간이 준다")
    void deadlockTimeout() throws Exception {
        List<String> log = crossTransfer("set deadlock_timeout = '200ms'");
        String failed = log.stream().filter(l -> l.contains("40P01")).findFirst().orElseThrow();
        long ms = Long.parseLong(failed.replaceAll(".*\\((\\d+)ms\\)$", "$1"));
        assertThat(ms).isLessThan(900);
        CiOutput.record(LESSON, "timeout", "deadlock_timeout = 200ms일 때 실패한 쪽: " + failed);
    }

    @Test
    @DisplayName("반대 방향 이체를 동시에 20번씩 돌리면, 순서 없이 잠그면 데드락이 나고 id 순으로 잠그면 0번이다")
    void orderedLocking() throws Exception {
        int unordered = runMany(false);
        exec(ds, "update dl_account set balance = 1000");
        int ordered = runMany(true);

        assertThat(unordered).isGreaterThan(0);
        assertThat(ordered).isZero();
        CiOutput.record(LESSON, "ordered", "반대 방향 이체 두 개를 동시에 20번\n[보내는 계좌부터 잠금] 데드락 " + unordered
                + "번\n[id가 작은 계좌부터 잠금] 데드락 " + ordered + "번");
    }

    private int runMany(boolean ordered) throws Exception {
        AtomicInteger deadlocks = new AtomicInteger();
        for (int i = 0; i < 20; i++) {
            CyclicBarrier start = new CyclicBarrier(2);
            CompletableFuture<Void> a = CompletableFuture.runAsync(() -> lockedTransfer(1, 2, ordered, start, deadlocks));
            CompletableFuture<Void> b = CompletableFuture.runAsync(() -> lockedTransfer(2, 1, ordered, start, deadlocks));
            a.get(20, TimeUnit.SECONDS);
            b.get(20, TimeUnit.SECONDS);
        }
        return deadlocks.get();
    }

    private void lockedTransfer(int from, int to, boolean ordered, CyclicBarrier start, AtomicInteger deadlocks) {
        try (Connection c = tx()) {
            exec(c, "set deadlock_timeout = '100ms'");
            start.await(5, TimeUnit.SECONDS);
            try {
                if (ordered) {
                    rows(c, "select id from dl_account where id in (" + from + ", " + to + ") order by id for update");
                } else {
                    rows(c, "select id from dl_account where id = " + from + " for update");
                    Thread.sleep(50);
                    rows(c, "select id from dl_account where id = " + to + " for update");
                }
                exec(c, "update dl_account set balance = balance - 100 where id = " + from,
                        "update dl_account set balance = balance + 100 where id = " + to);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                if ("40P01".equals(e.getSQLState())) {
                    deadlocks.incrementAndGet();
                } else {
                    throw e;
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("스프링은 40P01을 PessimisticLockingFailureException으로 바꾸고, 트랜잭션 바깥에서 다시 실행하면 두 이체가 모두 반영된다")
    void springExceptionAndRetry() throws Exception {
        TransactionTemplate tt = new TransactionTemplate(txManager);
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger retries = new AtomicInteger();
        CyclicBarrier firstDone = new CyclicBarrier(2);
        Runnable ab = () -> withRetry(tt, 1, 2, firstDone, seen, retries);
        Runnable ba = () -> withRetry(tt, 2, 1, firstDone, seen, retries);
        CompletableFuture<Void> fa = CompletableFuture.runAsync(ab);
        CompletableFuture<Void> fb = CompletableFuture.runAsync(ba);
        fa.get(30, TimeUnit.SECONDS);
        fb.get(30, TimeUnit.SECONDS);
        List<String> balances = rows(ds, "select owner, balance from dl_account order by id");

        assertThat(seen).isNotEmpty().allMatch(s -> s.equals("org.springframework.dao.PessimisticLockingFailureException"));
        assertThat(balances).containsExactly("kim | 1000", "lee | 1000");
        CiOutput.record(LESSON, "spring", "데드락으로 받은 스프링 예외: " + seen.get(0)
                + "\n다시 시도한 횟수: " + retries.get()
                + "\n끝난 뒤 잔액 (두 이체 모두 반영): " + balances);
    }

    private void withRetry(TransactionTemplate tt, int from, int to, CyclicBarrier firstDone, List<String> seen, AtomicInteger retries) {
        for (int attempt = 1; ; attempt++) {
            boolean first = attempt == 1;
            try {
                tt.executeWithoutResult(s -> {
                    jdbc.update("update dl_account set balance = balance - 100 where id = ?", from);
                    if (first) {
                        try {
                            firstDone.await(5, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }
                    jdbc.update("update dl_account set balance = balance + 100 where id = ?", to);
                });
                return;
            } catch (PessimisticLockingFailureException e) {
                seen.add(e.getClass().getName());
                retries.incrementAndGet();
                if (attempt == 3) {
                    throw e;
                }
            }
        }
    }

    @Test
    @DisplayName("외래 키가 있는 자식을 넣은 트랜잭션이 끝나기 전에, 다른 트랜잭션이 그 부모를 지우려 하면 기다린다")
    void foreignKeyLock() throws Exception {
        exec(ds, "create table dl_order (id int primary key, account_id int references dl_account(id))");
        long waited;
        try (Connection a = tx(); Connection b = tx()) {
            exec(a, "insert into dl_order values (1, 1)");
            long t = System.nanoTime();
            CompletableFuture<String> del = CompletableFuture.supplyAsync(() -> {
                try {
                    exec(b, "delete from dl_account where id = 1");
                    return "ok";
                } catch (SQLException e) {
                    return e.getSQLState();
                }
            });
            Thread.sleep(500);
            assertThat(del).isNotDone();
            a.commit();
            String result = del.get(5, TimeUnit.SECONDS);
            waited = (System.nanoTime() - t) / 1_000_000;
            b.rollback();
            assertThat(result).isEqualTo("23503");
        }
        CiOutput.record(LESSON, "fk", "A: insert 주문(account_id 1), 아직 커밋 전\nB: delete 계좌 1 → 기다림\nA: commit\nB: "
                + waited + "ms 기다린 뒤 23503(외래 키 위반)으로 실패");
    }
}
