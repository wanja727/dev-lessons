package lab.devlessons.outbox;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 레슨 "트랜잭셔널 아웃박스": 주문을 저장하고 "주문됨" 메시지를 보내는 세 가지 방법.
 * failAfterPublish가 true면 메시지를 보낸(또는 기록한) 뒤 예외를 던져 트랜잭션을 롤백한다. 예: 뒤이은 재고 확인 실패.
 */
public final class OrderServices {

    private OrderServices() {
    }

    static String payload(OutboxOrder order) {
        return "{\"orderId\":" + order.getId() + ",\"item\":\"" + order.getItem() + "\"}";
    }

    /** 1. 트랜잭션 안에서 바로 브로커로 보낸다 */
    @Service
    public static class DirectPublishOrderService {
        private final OutboxOrderRepository orders;
        private final FakeBroker broker;

        public DirectPublishOrderService(OutboxOrderRepository orders, FakeBroker broker) {
            this.orders = orders;
            this.broker = broker;
        }

        @Transactional
        public Long place(String item, boolean failAfterPublish) {
            OutboxOrder order = orders.saveAndFlush(new OutboxOrder(item));
            broker.send(new FakeBroker.Message(null, "OrderPlaced", payload(order)));
            if (failAfterPublish) {
                throw new IllegalStateException("재고 부족: 주문 롤백");
            }
            return order.getId();
        }
    }

    /** 2. 이벤트만 발행하고, 커밋된 뒤에 리스너가 브로커로 보낸다 */
    @Service
    public static class AfterCommitOrderService {
        private final OutboxOrderRepository orders;
        private final ApplicationEventPublisher events;

        public AfterCommitOrderService(OutboxOrderRepository orders, ApplicationEventPublisher events) {
            this.orders = orders;
            this.events = events;
        }

        @Transactional
        public Long place(String item, boolean failAfterPublish) {
            OutboxOrder order = orders.saveAndFlush(new OutboxOrder(item));
            events.publishEvent(new OrderPlaced(order.getId(), payload(order)));
            if (failAfterPublish) {
                throw new IllegalStateException("재고 부족: 주문 롤백");
            }
            return order.getId();
        }
    }

    public record OrderPlaced(Long orderId, String payload) {
    }

    @Component
    public static class OrderPlacedListener {
        private final FakeBroker broker;

        public OrderPlacedListener(FakeBroker broker) {
            this.broker = broker;
        }

        /** 커밋이 성공한 뒤에만 실행된다. 롤백되면 실행되지 않는다 */
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void on(OrderPlaced event) {
            broker.send(new FakeBroker.Message(null, "OrderPlaced", event.payload()));
        }
    }

    /** 3. 아웃박스: 메시지를 업무 데이터와 같은 트랜잭션으로 테이블에 기록한다. 보내기는 릴레이가 한다 */
    @Service
    public static class OutboxOrderService {
        private final OutboxOrderRepository orders;
        private final OutboxEventRepository outbox;

        public OutboxOrderService(OutboxOrderRepository orders, OutboxEventRepository outbox) {
            this.orders = orders;
            this.outbox = outbox;
        }

        @Transactional
        public Long place(String item, boolean failAfterPublish) {
            OutboxOrder order = orders.saveAndFlush(new OutboxOrder(item));
            outbox.save(new OutboxEvent("OrderPlaced", payload(order)));
            if (failAfterPublish) {
                throw new IllegalStateException("재고 부족: 주문 롤백");
            }
            return order.getId();
        }
    }
}
