package lab.devlessons.dml;

import static lab.devlessons.support.Jdbc.exec;
import static lab.devlessons.support.Jdbc.number;
import static lab.devlessons.support.Jdbc.update;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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
 * 레슨 "대량 DML 안전하게 하기"(D15)를 다시 쓰며, 레슨에 붙일 출력과 새 주장을 남긴다. 예전 주장은 BulkDmlTest가 확인한다.
 * 주문 10,000건 가운데 홀수 id 5,000건이 PENDING이고, 이것을 EXPIRED로 바꾸는 작업이다.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class BulkDmlLessonTest {

    private static final String LESSON = "bulk-dml";

    @Autowired DataSource ds;

    @BeforeEach
    void setUp() {
        exec(ds, "drop table if exists bd_orders_bak, bd_orders, bd_point",
                "create table bd_orders (id int primary key, status text not null, created_at date not null)",
                "insert into bd_orders select g, case when g % 2 = 1 then 'PENDING' else 'PAID' end, "
                        + "date '2026-01-01' + (g % 300) from generate_series(1, 10000) g");
    }

    private long expired() {
        return number(ds, "select count(*) from bd_orders where status = 'EXPIRED'");
    }

    @Test
    @DisplayName("한 트랜잭션으로 5,000건을 바꾸다 마지막에 실패하면 바뀐 건수는 0이다")
    void oneTransaction() throws SQLException {
        String error;
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            int n = update(c, "update bd_orders set status = 'EXPIRED' where status = 'PENDING'");
            try {
                exec(c, "update bd_orders set id = 1 where id = 2");
                error = "없음";
            } catch (SQLException e) {
                error = e.getSQLState();
            }
            c.rollback();
            CiOutput.record(LESSON, "one-tx", "한 트랜잭션: update " + n + "건 → 마지막 문장에서 오류 " + error
                    + " → rollback\n남은 EXPIRED: " + expired() + "건");
        }
        assertThat(expired()).isZero();
    }

    @Test
    @DisplayName("id 1,000개 범위씩 커밋하다 넷째 묶음에서 실패하면 1,500건이 남고, 대상 조건이 있으면 처음부터 다시 돌려 5,000건이 된다")
    void batchesAndRerun() throws SQLException {
        List<String> log = new ArrayList<>();
        runBatches(log, 4);
        long afterFail = expired();
        log.add("실패 뒤 EXPIRED: " + afterFail + "건");
        log.add("--- 처음부터 다시 ---");
        runBatches(log, -1);
        long afterRerun = expired();
        log.add("다시 돌린 뒤 EXPIRED: " + afterRerun + "건");

        assertThat(afterFail).isEqualTo(1500);
        assertThat(afterRerun).isEqualTo(5000);
        CiOutput.record(LESSON, "batches", String.join("\n", log));
    }

    private void runBatches(List<String> log, int failAt) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            for (int b = 1; b <= 10; b++) {
                int from = (b - 1) * 1000 + 1;
                int to = b * 1000;
                if (b == failAt) {
                    c.rollback();
                    log.add(b + "번째 묶음(" + from + "~" + to + "): 실패, 이 묶음만 rollback");
                    return;
                }
                int n = update(c, "update bd_orders set status = 'EXPIRED' where id between " + from + " and " + to
                        + " and status = 'PENDING'");
                c.commit();
                if (failAt > 0 || b <= 4 || b == 10) {
                    log.add(b + "번째 묶음(" + from + "~" + to + "): " + n + "건, commit");
                }
            }
        }
    }

    @Test
    @DisplayName("값을 더하는 작업을 대상 조건 없이 다시 돌리면 이미 더한 회원에게 두 번 더해진다. 지급 기록으로 거르면 한 번이다")
    void rerunWithoutCondition() throws SQLException {
        exec(ds, "create table bd_point (member_id int primary key, point int not null, paid boolean not null default false)",
                "insert into bd_point select g, 0 from generate_series(1, 3000) g");
        // 1,000명씩 하다 둘째 묶음 뒤 실패
        for (int b = 1; b <= 2; b++) {
            exec(ds, "update bd_point set point = point + 1000 where member_id between " + ((b - 1) * 1000 + 1) + " and " + (b * 1000));
        }
        // 처음부터 다시: 조건 없이
        for (int b = 1; b <= 3; b++) {
            exec(ds, "update bd_point set point = point + 1000 where member_id between " + ((b - 1) * 1000 + 1) + " and " + (b * 1000));
        }
        long doubled = number(ds, "select count(*) from bd_point where point = 2000");

        exec(ds, "update bd_point set point = 0, paid = false");
        for (int b = 1; b <= 2; b++) {
            exec(ds, "update bd_point set point = point + 1000, paid = true where member_id between " + ((b - 1) * 1000 + 1)
                    + " and " + (b * 1000) + " and not paid");
        }
        for (int b = 1; b <= 3; b++) {
            exec(ds, "update bd_point set point = point + 1000, paid = true where member_id between " + ((b - 1) * 1000 + 1)
                    + " and " + (b * 1000) + " and not paid");
        }
        long doubledSafe = number(ds, "select count(*) from bd_point where point = 2000");

        assertThat(doubled).isEqualTo(2000);
        assertThat(doubledSafe).isZero();
        CiOutput.record(LESSON, "point", "회원 3,000명에게 1000점씩, 1,000명씩 하다 둘째 묶음 뒤 실패하고 처음부터 다시\n"
                + "[조건 없이] 2000점을 받은 회원: " + doubled + "명\n[and not paid 조건] 2000점을 받은 회원: " + doubledSafe + "명");
    }

    @Test
    @DisplayName("괄호를 빠뜨린 조건은 미리 센 건수보다 많이 바꾸고, 건수를 비교해 커밋 전에 알아챈다")
    void wrongCondition() throws SQLException {
        long target = number(ds, "select count(*) from bd_orders where status = 'PENDING' and (created_at < '2026-03-01' or id < 100)");
        int changed;
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            changed = update(c, "update bd_orders set status = 'EXPIRED' where status = 'PENDING' and created_at < '2026-03-01' or id < 100");
            c.rollback();
        }
        assertThat(changed).isGreaterThan((int) target);
        CiOutput.record(LESSON, "wrong", "미리 센 대상: " + target + "건\n"
                + "괄호를 빠뜨린 UPDATE가 바꾼 건수: " + changed + "건\n→ 다르므로 rollback");
    }

    @Test
    @DisplayName("백업 테이블 건수 = 대상 건수, UPDATE 건수 = 대상 건수, 끝난 뒤 대상 0건, 백업으로 되돌리면 원래와 같다")
    void backupVerifyRestore() throws SQLException {
        List<String> log = new ArrayList<>();
        long target = number(ds, "select count(*) from bd_orders where status = 'PENDING'");
        log.add("1. 대상 건수: " + target);
        exec(ds, "create table bd_orders_bak as select * from bd_orders where status = 'PENDING'");
        log.add("2. 백업 테이블 건수: " + number(ds, "select count(*) from bd_orders_bak"));
        int changed;
        try (Connection c = ds.getConnection()) {
            changed = update(c, "update bd_orders set status = 'EXPIRED' where status = 'PENDING'");
        }
        log.add("3. UPDATE가 바꾼 건수: " + changed);
        long left = number(ds, "select count(*) from bd_orders where status = 'PENDING'");
        log.add("4. 끝난 뒤 대상 건수: " + left);
        exec(ds, "update bd_orders o set status = b.status from bd_orders_bak b where b.id = o.id");
        long diff = number(ds, "select count(*) from bd_orders o join bd_orders_bak b on b.id = o.id where o.status <> b.status");
        log.add("5. 백업으로 되돌린 뒤 백업과 다른 행: " + diff + ", PENDING " + number(ds, "select count(*) from bd_orders where status = 'PENDING'") + "건");

        assertThat(changed).isEqualTo((int) target);
        assertThat(left).isZero();
        assertThat(diff).isZero();
        CiOutput.record(LESSON, "steps", String.join("\n", log));
    }

    @Test
    @DisplayName("커밋 전 대량 UPDATE가 잡은 행을 고치려는 요청은 lock_timeout으로 실패한다")
    void lockTimeout() throws SQLException {
        String result;
        try (Connection bulk = ds.getConnection(); Connection request = ds.getConnection()) {
            bulk.setAutoCommit(false);
            update(bulk, "update bd_orders set status = 'EXPIRED' where status = 'PENDING'");
            exec(request, "set lock_timeout = '1s'");
            long t = System.nanoTime();
            try {
                exec(request, "update bd_orders set status = 'PAID' where id = 7");
                result = "성공";
            } catch (SQLException e) {
                result = e.getSQLState() + " " + e.getMessage().lines().findFirst().orElse("") + " (" + (System.nanoTime() - t) / 1_000_000 + "ms)";
            }
            bulk.rollback();
        }
        assertThat(result).startsWith("55P03");
        CiOutput.record(LESSON, "lock", "대량 UPDATE(커밋 전)가 PENDING 5,000건을 잡은 동안\n결제 요청의 update ... where id = 7 →\n" + result);
    }
}
