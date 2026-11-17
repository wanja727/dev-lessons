package lab.devlessons.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** 레슨 "결제 시스템 흐름"의 주장을 실제 PostgreSQL과 가짜 PG로 검증한다. 테스트에는 트랜잭션이 없다. */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class PaymentFlowTest {

    static final String LESSON = "payment-flow";

    @Autowired JdbcTemplate jdbc;
    final FakePg pg = new FakePg();
    final ExecutorService io = Executors.newCachedThreadPool();

    @BeforeEach
    void setUp() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS pay_payment (
                    order_id VARCHAR(40) PRIMARY KEY,
                    amount BIGINT NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    captured_amount BIGINT NOT NULL DEFAULT 0,
                    canceled_amount BIGINT NOT NULL DEFAULT 0,
                    CHECK (canceled_amount <= amount))""");
        jdbc.execute("TRUNCATE pay_payment");
        pg.clear();
    }

    // ---- 우리 서버의 결제 처리 ----

    void ready(String orderId, long amount) {
        jdbc.update("INSERT INTO pay_payment(order_id, amount, status) VALUES (?, ?, 'READY')", orderId, amount);
    }

    /** 승인 요청. 타임아웃이면 결과를 모르니 UNKNOWN으로 두고, netCancelOnTimeout이면 망 취소를 보낸다 */
    String approve(String orderId, long amount, long timeoutMs, boolean netCancelOnTimeout) {
        CompletableFuture<String> call = CompletableFuture.supplyAsync(() -> pg.approve(orderId, amount), io);
        try {
            call.get(timeoutMs, TimeUnit.MILLISECONDS);
            transition(orderId, "READY", "AUTHORIZED");
            return "AUTHORIZED";
        } catch (TimeoutException e) {
            if (netCancelOnTimeout) {
                transition(orderId, "READY", "UNKNOWN");
                pg.netCancel(orderId);                     // 결과를 모르는 승인은 취소해 버린다
                transition(orderId, "UNKNOWN", "CANCELED");
                return "CANCELED";
            }
            transition(orderId, "READY", "FAILED");          // 잘못된 처리: 타임아웃을 실패로 단정
            return "FAILED";
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    void transition(String orderId, String from, String to) {
        int n = jdbc.update("UPDATE pay_payment SET status = ? WHERE order_id = ? AND status = ?", to, orderId, from);
        if (n == 0) {
            throw new IllegalStateException(from + " 상태가 아니라서 " + to + "(으)로 바꿀 수 없음");
        }
    }

    /** 매입: 승인된 결제를 실제 청구로 확정한다. 승인 금액을 넘을 수 없다 */
    void capture(String orderId, long amount) {
        int n = jdbc.update("UPDATE pay_payment SET status = 'CAPTURED', captured_amount = ? "
                + "WHERE order_id = ? AND status = 'AUTHORIZED' AND ? <= amount", amount, orderId, amount);
        if (n == 0) {
            throw new IllegalStateException("매입할 수 없음");
        }
    }

    /** 부분 취소: 누적 취소액이 결제 금액을 넘지 않을 때만 */
    boolean partialCancel(String orderId, long amount) {
        return jdbc.update("UPDATE pay_payment SET canceled_amount = canceled_amount + ? "
                + "WHERE order_id = ? AND status = 'CAPTURED' AND canceled_amount + ? <= amount", amount, orderId, amount) == 1;
    }

    String status(String orderId) {
        return jdbc.queryForObject("SELECT status FROM pay_payment WHERE order_id = ?", String.class, orderId);
    }

    /** 대사: 우리 기록과 PG 거래 내역을 주문 번호로 맞춰 보고 다른 점을 찾는다 */
    List<String> reconcile() {
        Map<String, Map<String, Object>> ours = new HashMap<>();
        jdbc.queryForList("SELECT order_id, amount, status FROM pay_payment").forEach(r -> ours.put((String) r.get("order_id"), r));
        List<String> diffs = new ArrayList<>();
        for (FakePg.SettlementLine line : pg.settlement()) {
            Map<String, Object> mine = ours.remove(line.orderId());
            boolean pgCharged = line.status() == FakePg.Status.APPROVED;
            if (mine == null) {
                diffs.add(line.orderId() + ": PG에만 있음 (" + line.status() + ")");
                continue;
            }
            String st = (String) mine.get("status");
            boolean weCharged = st.equals("AUTHORIZED") || st.equals("CAPTURED");
            if (pgCharged != weCharged) {
                diffs.add(line.orderId() + ": PG " + line.status() + " / 우리 " + st);
            } else if (pgCharged && ((Number) mine.get("amount")).longValue() != line.amount()) {
                diffs.add(line.orderId() + ": 금액 다름 PG " + line.amount() + " / 우리 " + mine.get("amount"));
            }
        }
        ours.forEach((id, r) -> {
            String st = (String) r.get("status");
            if (st.equals("AUTHORIZED") || st.equals("CAPTURED")) {
                diffs.add(id + ": 우리에게만 있음 (" + st + ")");
            }
        });
        diffs.sort(String::compareTo);
        return diffs;
    }

    // ---- 테스트 ----

    @Test
    @DisplayName("정상 흐름: 준비 → 승인 → 매입. 매입은 승인 금액을 넘을 수 없고, 승인 전에는 매입할 수 없다")
    void approveThenCapture() {
        ready("order-1", 10_000);
        ready("order-2", 10_000);

        assertThatThrownBy(() -> capture("order-2", 10_000)).hasMessageContaining("매입할 수 없음"); // 승인 전
        assertThat(approve("order-1", 10_000, 1_000, true)).isEqualTo("AUTHORIZED");
        assertThatThrownBy(() -> capture("order-1", 12_000)).hasMessageContaining("매입할 수 없음"); // 승인 금액 초과
        capture("order-1", 10_000);

        assertThat(status("order-1")).isEqualTo("CAPTURED");
        assertThat(status("order-2")).isEqualTo("READY");
        assertThat(pg.status("order-1")).isEqualTo(FakePg.Status.APPROVED);
    }

    @Test
    @DisplayName("승인 응답이 타임아웃일 때 실패로 단정하면 PG에서는 승인된 채 남고(고객 돈이 묶임), 대사에서 어긋남이 드러난다")
    void timeoutAsFailureLeavesOrphanApproval() {
        pg.responseDelayMillis = 500;
        ready("order-1", 10_000);

        String result = approve("order-1", 10_000, 200, false);

        assertThat(result).isEqualTo("FAILED");
        assertThat(pg.status("order-1")).isEqualTo(FakePg.Status.APPROVED);
        assertThat(reconcile()).containsExactly("order-1: PG APPROVED / 우리 FAILED");
        CiOutput.record(LESSON, "orphan", "[PG 응답 500ms, 우리 타임아웃 200ms, 타임아웃을 실패로 처리]\n"
                + "우리 DB: " + status("order-1") + " (고객에게 \"결제 실패\" 안내)\n"
                + "PG: " + pg.status("order-1") + " (고객 카드에서는 승인됨)\n"
                + "대사 결과: " + reconcile());
    }

    @Test
    @DisplayName("망 취소: 타임아웃이면 결과를 모르는 승인을 바로 취소해 양쪽을 '결제 없음'으로 맞춘다")
    void netCancelOnTimeout() {
        pg.responseDelayMillis = 500;
        ready("order-1", 10_000);

        String result = approve("order-1", 10_000, 200, true);

        assertThat(result).isEqualTo("CANCELED");
        assertThat(pg.status("order-1")).isEqualTo(FakePg.Status.CANCELED);
        assertThat(reconcile()).isEmpty();
        CiOutput.record(LESSON, "net-cancel", "[PG 응답 500ms, 우리 타임아웃 200ms, 타임아웃이면 망 취소]\n"
                + "우리 DB: READY → UNKNOWN → " + status("order-1") + "\n"
                + "PG: " + pg.status("order-1") + "\n"
                + "대사 결과: 어긋남 " + reconcile().size() + "건");
    }

    @Test
    @DisplayName("부분 취소: 누적 취소액은 결제 금액을 넘을 수 없다 (3,000 + 7,000은 되고, 1원 더는 거절)")
    void partialCancels() {
        ready("order-1", 10_000);
        approve("order-1", 10_000, 1_000, true);
        capture("order-1", 10_000);

        boolean first = partialCancel("order-1", 3_000);
        boolean second = partialCancel("order-1", 7_000);
        boolean third = partialCancel("order-1", 1);

        assertThat(first).isTrue();
        assertThat(second).isTrue();
        assertThat(third).isFalse();
        assertThatThrownBy(() -> jdbc.update("UPDATE pay_payment SET canceled_amount = 10001 WHERE order_id = 'order-1'"))
                .hasMessageContaining("check"); // 조건을 빼먹은 코드도 DB 제약이 막는다
    }

    @Test
    @DisplayName("부분 취소 두 건이 동시에 와도(6,000 + 6,000 > 10,000) 하나만 성공한다")
    void concurrentPartialCancels() throws Exception {
        ready("order-1", 10_000);
        approve("order-1", 10_000, 1_000, true);
        capture("order-1", 10_000);
        CyclicBarrier together = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        List<Future<Boolean>> fs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            fs.add(pool.submit(() -> {
                together.await();
                return partialCancel("order-1", 6_000);
            }));
        }
        int ok = 0;
        for (Future<Boolean> f : fs) {
            if (f.get(10, TimeUnit.SECONDS)) {
                ok++;
            }
        }
        pool.shutdown();

        long canceled = jdbc.queryForObject("SELECT canceled_amount FROM pay_payment WHERE order_id = 'order-1'", Long.class);
        assertThat(ok).isEqualTo(1);
        assertThat(canceled).isEqualTo(6_000);
        CiOutput.record(LESSON, "partial-cancel", "[결제 10,000원, 부분 취소 6,000원 두 건이 동시에]\n"
                + "성공 " + ok + "건, 누적 취소액 " + canceled + "원\n"
                + "(UPDATE ... SET canceled_amount = canceled_amount + 6000 WHERE canceled_amount + 6000 <= amount)");
    }

    @Test
    @DisplayName("대사: 우리 기록과 PG 내역을 주문 번호로 맞춰 보면 금액 차이, 한쪽에만 있는 거래를 찾는다")
    void reconciliationFindsDifferences() {
        jdbc.update("INSERT INTO pay_payment(order_id, amount, status, captured_amount) VALUES ('order-1', 10000, 'CAPTURED', 10000)");
        jdbc.update("INSERT INTO pay_payment(order_id, amount, status, captured_amount) VALUES ('order-2', 5000, 'CAPTURED', 5000)");
        jdbc.update("INSERT INTO pay_payment(order_id, amount, status, captured_amount) VALUES ('order-3', 7000, 'CAPTURED', 7000)");
        pg.put("order-1", 10_000, FakePg.Status.APPROVED);  // 일치
        pg.put("order-2", 4_500, FakePg.Status.APPROVED);   // 금액 다름
        pg.put("order-4", 3_000, FakePg.Status.APPROVED);   // PG에만 있음
        // order-3은 우리에게만 있음

        List<String> diffs = reconcile();

        assertThat(diffs).containsExactly(
                "order-2: 금액 다름 PG 4500 / 우리 5000",
                "order-3: 우리에게만 있음 (CAPTURED)",
                "order-4: PG에만 있음 (APPROVED)");
        CiOutput.record(LESSON, "reconcile", "[우리 기록 3건, PG 내역 3건]\n" + String.join("\n", diffs));
    }
}
