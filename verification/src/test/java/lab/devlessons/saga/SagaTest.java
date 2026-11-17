package lab.devlessons.saga;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import lab.devlessons.saga.SagaServices.SagaLog;
import lab.devlessons.saga.SagaServices.SagaPaymentService;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** 레슨 "분산 트랜잭션과 사가"의 주장을 실제 PostgreSQL에서 검증한다. 테스트에는 트랜잭션이 없다. */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class SagaTest {

    static final String LESSON = "saga";

    @Autowired OrderSagaOrchestrator orchestrator;
    @Autowired ChoreographySaga choreography;
    @Autowired SagaPaymentService payments;
    @Autowired SagaLog log;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS saga_order (id BIGSERIAL PRIMARY KEY, item VARCHAR(50) NOT NULL, amount BIGINT NOT NULL, status VARCHAR(20) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS saga_payment (id BIGSERIAL PRIMARY KEY, order_id BIGINT NOT NULL, amount BIGINT NOT NULL, status VARCHAR(20) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS saga_stock (item VARCHAR(50) PRIMARY KEY, quantity INT NOT NULL)");
        jdbc.execute("TRUNCATE saga_order, saga_payment, saga_stock");
        jdbc.update("INSERT INTO saga_stock VALUES ('커피', 1), ('케이크', 0)");
        log.clear();
        orchestrator.setAfterPayment(() -> { });
    }

    @AfterEach
    void tearDown() {
        orchestrator.setAfterPayment(() -> { });
    }

    private String orderStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM saga_order WHERE id = ?", String.class, id);
    }

    private int stockOf(String item) {
        return jdbc.queryForObject("SELECT quantity FROM saga_stock WHERE item = ?", Integer.class, item);
    }

    private String table(long id, String item) {
        return "saga_order.status = " + orderStatus(id) + "\n"
                + "saga_payment.status = " + payments.status(id) + "\n"
                + "saga_stock(" + item + ") = " + stockOf(item);
    }

    @Test
    @DisplayName("오케스트레이션: 모든 단계가 성공하면 주문 확정, 결제 승인, 재고 차감")
    void orchestrationHappyPath() {
        long id = orchestrator.placeOrder("커피", 4_500);

        assertThat(orderStatus(id)).isEqualTo("CONFIRMED");
        assertThat(payments.status(id)).isEqualTo("CHARGED");
        assertThat(stockOf("커피")).isZero();
        assertThat(log.steps()).containsExactly("주문 생성 (PENDING)", "결제 승인 (CHARGED)", "재고 차감", "주문 확정 (CONFIRMED)");
    }

    @Test
    @DisplayName("오케스트레이션: 재고 차감이 실패하면 앞 단계의 보상이 역순으로 실행된다. 기록은 지워지지 않고 취소·환불 상태로 남는다")
    void orchestrationCompensatesInReverse() {
        long id = orchestrator.placeOrder("케이크", 6_000);

        assertThat(log.steps()).containsExactly(
                "주문 생성 (PENDING)", "결제 승인 (CHARGED)", "재고 차감 실패", "보상: 결제 환불 (REFUNDED)", "보상: 주문 취소 (CANCELLED)");
        assertThat(orderStatus(id)).isEqualTo("CANCELLED");
        assertThat(payments.status(id)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM saga_payment", Long.class)).isEqualTo(1);

        CiOutput.record(LESSON, "compensate", "[오케스트레이션, 케이크 재고 0]\n"
                + String.join("\n", log.steps().stream().map(s -> "→ " + s).toList()) + "\n\n최종 상태\n" + table(id, "케이크"));
    }

    @Test
    @DisplayName("사가에는 격리가 없다: 보상 전까지 다른 요청은 중간 상태(결제 승인)를 본다")
    void sagaHasNoIsolation() {
        AtomicReference<String> seenByOthers = new AtomicReference<>();
        // 결제가 커밋된 뒤, 재고 차감 전: 다른 요청(예: 고객의 결제 내역 조회)이 결제 상태를 읽는다
        orchestrator.setAfterPayment(() -> seenByOthers.set(
                jdbc.queryForObject("SELECT status FROM saga_payment ORDER BY id DESC LIMIT 1", String.class)));

        long id = orchestrator.placeOrder("케이크", 6_000);

        assertThat(seenByOthers.get()).isEqualTo("CHARGED");
        assertThat(payments.status(id)).isEqualTo("REFUNDED");
        CiOutput.record(LESSON, "no-isolation", "[결제 뒤, 재고 차감 전에 다른 요청이 조회]\n"
                + "다른 요청이 본 결제 상태: " + seenByOthers.get() + "\n"
                + "사가가 끝난 뒤 결제 상태: " + payments.status(id));
    }

    @Test
    @DisplayName("보상은 멱등해야 한다: 환불 보상이 두 번 불려도 한 번만 환불된다")
    void compensationIsIdempotent() {
        long id = orchestrator.placeOrder("케이크", 6_000);
        log.clear();

        boolean again = payments.refund(id); // 재시도로 같은 보상이 또 옴

        assertThat(again).isFalse();
        assertThat(payments.status(id)).isEqualTo("REFUNDED");
        assertThat(log.steps()).containsExactly("보상: 결제 환불 요청 (이미 환불됨, 건너뜀)");
    }

    @Test
    @DisplayName("코레오그래피: 지휘자 없이 이벤트를 주고받아도 같은 보상 흐름과 최종 상태가 된다")
    void choreographyReachesSameState() {
        long ok = choreography.placeOrder("커피", 4_500);
        assertThat(choreography.events()).containsExactly("OrderCreated", "PaymentCompleted", "StockReserved");
        assertThat(orderStatus(ok)).isEqualTo("CONFIRMED");

        log.clear();
        long failed = choreography.placeOrder("케이크", 6_000);

        assertThat(choreography.events()).containsExactly("OrderCreated", "PaymentCompleted", "StockFailed", "PaymentRefunded");
        assertThat(orderStatus(failed)).isEqualTo("CANCELLED");
        assertThat(payments.status(failed)).isEqualTo("REFUNDED");
        CiOutput.record(LESSON, "choreography", "[코레오그래피, 케이크 재고 0]\n오간 이벤트: "
                + String.join(" → ", choreography.events()) + "\n각 서비스가 한 일: " + String.join(" → ", log.steps())
                + "\n\n최종 상태\n" + table(failed, "케이크"));
    }

    @Test
    @DisplayName("2PC: PostgreSQL은 기본 설정에서 두 단계 커밋(PREPARE TRANSACTION)이 꺼져 있다")
    void twoPhaseCommitIsOffByDefault() {
        String max = jdbc.queryForObject("SHOW max_prepared_transactions", String.class);

        assertThat(max).isEqualTo("0");
    }
}
