package lab.devlessons.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lab.devlessons.outbox.OrderServices.AfterCommitOrderService;
import lab.devlessons.outbox.OrderServices.DirectPublishOrderService;
import lab.devlessons.outbox.OrderServices.OutboxOrderService;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 레슨 "트랜잭셔널 아웃박스"의 주장을 실제 PostgreSQL에서 검증한다. 테스트에는 트랜잭션이 없다. */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class TransactionalOutboxTest {

    static final String LESSON = "transactional-outbox";

    @Autowired DirectPublishOrderService direct;
    @Autowired AfterCommitOrderService afterCommit;
    @Autowired OutboxOrderService outboxService;
    @Autowired OutboxRelay relay;
    @Autowired OutboxOrderRepository orders;
    @Autowired OutboxEventRepository outbox;
    @Autowired FakeBroker broker;

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
        orders.deleteAll();
        broker.clear();
        relay.setAfterSend(() -> { });
    }

    @AfterEach
    void tearDown() {
        broker.clear();
        relay.setAfterSend(() -> { });
    }

    private String state() {
        return "order 테이블: " + orders.count() + "행\n"
                + "outbox_event 테이블: " + outbox.count() + "행 (안 보낸 행 " + outbox.countByPublishedAtIsNull() + ")\n"
                + "브로커가 받은 메시지: " + broker.received().size() + "개";
    }

    @Test
    @DisplayName("트랜잭션 안에서 바로 보내면, 롤백돼도 메시지는 이미 나가 있다 (없는 주문의 메시지)")
    void directPublishLeaksOnRollback() {
        assertThatThrownBy(() -> direct.place("커피", true)).isInstanceOf(IllegalStateException.class);

        assertThat(orders.count()).isZero();
        assertThat(broker.received()).hasSize(1);
        CiOutput.record(LESSON, "direct-rollback", "[바로 발행 + 롤백]\n" + state());
    }

    @Test
    @DisplayName("트랜잭션 안에서 바로 보내면, 브로커가 죽었을 때 주문도 함께 실패한다")
    void directPublishFailsOrderWhenBrokerDown() {
        broker.setDown(true);

        assertThatThrownBy(() -> direct.place("커피", false)).isInstanceOf(FakeBroker.BrokerUnavailableException.class);

        assertThat(orders.count()).isZero();
    }

    @Test
    @DisplayName("AFTER_COMMIT 리스너: 롤백되면 메시지를 보내지 않는다")
    void afterCommitSkipsOnRollback() {
        assertThatThrownBy(() -> afterCommit.place("커피", true)).isInstanceOf(IllegalStateException.class);

        assertThat(orders.count()).isZero();
        assertThat(broker.received()).isEmpty();
    }

    @Test
    @DisplayName("AFTER_COMMIT 리스너: 커밋되면 커밋 뒤에 메시지를 보낸다")
    void afterCommitPublishesAfterCommit() {
        afterCommit.place("커피", false);

        assertThat(orders.count()).isEqualTo(1);
        assertThat(broker.received()).hasSize(1);
    }

    @Test
    @DisplayName("AFTER_COMMIT 리스너: 커밋 뒤 보내기가 실패하면 주문은 남고 메시지는 사라진다")
    void afterCommitLosesMessageWhenBrokerDown() {
        broker.setDown(true);

        String outcome;
        try {
            afterCommit.place("커피", false);
            outcome = "place() 호출: 예외 없이 끝남";
        } catch (RuntimeException e) {
            outcome = "place() 호출: " + e.getClass().getSimpleName() + " (이미 커밋된 뒤)";
        }
        broker.setDown(false);

        assertThat(orders.count()).isEqualTo(1);
        assertThat(broker.received()).isEmpty();
        CiOutput.record(LESSON, "after-commit-lost", "[AFTER_COMMIT + 브로커 장애]\n" + outcome + "\n" + state());
    }

    @Test
    @DisplayName("아웃박스: 롤백되면 주문과 아웃박스 행이 함께 사라진다")
    void outboxRollsBackTogether() {
        assertThatThrownBy(() -> outboxService.place("커피", true)).isInstanceOf(IllegalStateException.class);
        relay.publishPending();

        assertThat(orders.count()).isZero();
        assertThat(outbox.count()).isZero();
        assertThat(broker.received()).isEmpty();
    }

    @Test
    @DisplayName("아웃박스: 커밋되면 주문과 아웃박스 행이 함께 남고, 릴레이가 돌기 전에는 메시지가 나가지 않는다")
    void outboxCommitsTogether() {
        outboxService.place("커피", false);

        assertThat(orders.count()).isEqualTo(1);
        assertThat(outbox.countByPublishedAtIsNull()).isEqualTo(1);
        assertThat(broker.received()).isEmpty();

        assertThat(relay.publishPending()).isEqualTo(1);
        assertThat(broker.received()).hasSize(1);
        assertThat(outbox.countByPublishedAtIsNull()).isZero();
    }

    @Test
    @DisplayName("아웃박스: 브로커가 죽어 있어도 주문은 성공하고, 브로커가 살아나면 릴레이가 보낸다")
    void outboxSurvivesBrokerOutage() {
        StringBuilder log = new StringBuilder("[아웃박스 + 브로커 장애]\n");
        broker.setDown(true);
        outboxService.place("커피", false);
        outboxService.place("케이크", false);
        log.append("주문 2건 커밋 (브로커 장애 중)\n");

        assertThat(relay.publishPending()).isZero();
        log.append("릴레이 1회차: 보냄 0개\n").append(state()).append("\n");
        assertThat(orders.count()).isEqualTo(2);
        assertThat(outbox.countByPublishedAtIsNull()).isEqualTo(2);

        broker.setDown(false);
        assertThat(relay.publishPending()).isEqualTo(2);
        log.append("브로커 복구 → 릴레이 2회차: 보냄 2개\n").append(state());
        assertThat(broker.received()).hasSize(2);
        assertThat(outbox.countByPublishedAtIsNull()).isZero();
        CiOutput.record(LESSON, "outbox-outage", log.toString());
    }

    @Test
    @DisplayName("아웃박스: 릴레이는 저장된 순서대로 보낸다")
    void relayKeepsOrder() {
        outboxService.place("1번", false);
        outboxService.place("2번", false);
        outboxService.place("3번", false);

        relay.publishPending();

        List<FakeBroker.Message> received = broker.received();
        assertThat(received).hasSize(3);
        assertThat(received.get(0).payload()).contains("1번");
        assertThat(received.get(1).payload()).contains("2번");
        assertThat(received.get(2).payload()).contains("3번");
    }

    @Test
    @DisplayName("아웃박스: 릴레이가 보낸 뒤 표시하기 전에 죽으면 같은 메시지를 다시 보낸다 (최소 한 번). 받는 쪽은 event_id로 거른다")
    void relayCrashSendsDuplicate() {
        outboxService.place("커피", false);
        relay.setAfterSend(() -> {
            throw new IllegalStateException("릴레이 프로세스 종료");
        });
        assertThatThrownBy(() -> relay.publishPending()).isInstanceOf(IllegalStateException.class);
        relay.setAfterSend(() -> { });

        relay.publishPending(); // 재시작한 릴레이

        List<FakeBroker.Message> received = broker.received();
        assertThat(received).hasSize(2);
        assertThat(received.get(0).eventId()).isEqualTo(received.get(1).eventId());

        // 멱등 컨슈머: 처리한 event_id를 기억하고 이미 본 id는 건너뛴다
        Set<String> processed = new HashSet<>();
        int handled = 0;
        for (FakeBroker.Message m : received) {
            if (processed.add(m.eventId())) {
                handled++;
            }
        }
        assertThat(handled).isEqualTo(1);

        CiOutput.record(LESSON, "relay-duplicate", "[릴레이가 보낸 뒤 표시 전에 죽음 → 재시작]\n"
                + "브로커가 받은 메시지: " + received.size() + "개\n"
                + "event_id: " + received.stream().map(m -> m.eventId().substring(0, 8) + "…").collect(Collectors.joining(", ")) + "\n"
                + "event_id로 거른 컨슈머가 처리한 수: " + handled);
    }
}
