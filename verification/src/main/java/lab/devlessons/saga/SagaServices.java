package lab.devlessons.saga;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 레슨 "분산 트랜잭션과 사가"의 참여 서비스 셋. 실제로는 서로 다른 서버와 DB지만,
 * 여기서는 테이블을 나누고 메서드마다 따로 커밋해서(로컬 트랜잭션) 같은 상황을 만든다.
 */
public final class SagaServices {

    private SagaServices() {
    }

    /** 사가가 지나간 단계를 순서대로 기록한다 */
    @Component
    public static class SagaLog {
        private final List<String> steps = new CopyOnWriteArrayList<>();

        public void add(String step) {
            steps.add(step);
        }

        public List<String> steps() {
            return List.copyOf(steps);
        }

        public void clear() {
            steps.clear();
        }
    }

    public static class OutOfStockException extends RuntimeException {
        public OutOfStockException(String item) {
            super("재고 없음: " + item);
        }
    }

    /** 주문 서비스: PENDING으로 만들고, 끝에 CONFIRMED 또는 CANCELLED로 바꾼다 */
    @Service
    public static class SagaOrderService {
        private final JdbcTemplate jdbc;
        private final SagaLog log;

        public SagaOrderService(JdbcTemplate jdbc, SagaLog log) {
            this.jdbc = jdbc;
            this.log = log;
        }

        @Transactional
        public long create(String item, long amount) {
            Long id = jdbc.queryForObject(
                    "INSERT INTO saga_order(item, amount, status) VALUES (?, ?, 'PENDING') RETURNING id", Long.class, item, amount);
            log.add("주문 생성 (PENDING)");
            return id;
        }

        @Transactional
        public void confirm(long orderId) {
            jdbc.update("UPDATE saga_order SET status = 'CONFIRMED' WHERE id = ?", orderId);
            log.add("주문 확정 (CONFIRMED)");
        }

        /** 보상: 주문을 지우지 않고 취소 상태로 남긴다 */
        @Transactional
        public void cancel(long orderId) {
            jdbc.update("UPDATE saga_order SET status = 'CANCELLED' WHERE id = ? AND status = 'PENDING'", orderId);
            log.add("보상: 주문 취소 (CANCELLED)");
        }
    }

    /** 결제 서비스 */
    @Service
    public static class SagaPaymentService {
        private final JdbcTemplate jdbc;
        private final SagaLog log;

        public SagaPaymentService(JdbcTemplate jdbc, SagaLog log) {
            this.jdbc = jdbc;
            this.log = log;
        }

        @Transactional
        public void charge(long orderId, long amount) {
            jdbc.update("INSERT INTO saga_payment(order_id, amount, status) VALUES (?, ?, 'CHARGED')", orderId, amount);
            log.add("결제 승인 (CHARGED)");
        }

        /**
         * 보상: 결제를 지우지 않고 환불 상태로 바꾼다. CHARGED일 때만 바꾸므로 두 번 불려도 한 번만 환불된다 (멱등).
         * 실제로 환불했으면 true.
         */
        @Transactional
        public boolean refund(long orderId) {
            int changed = jdbc.update(
                    "UPDATE saga_payment SET status = 'REFUNDED' WHERE order_id = ? AND status = 'CHARGED'", orderId);
            log.add(changed == 1 ? "보상: 결제 환불 (REFUNDED)" : "보상: 결제 환불 요청 (이미 환불됨, 건너뜀)");
            return changed == 1;
        }

        public String status(long orderId) {
            return jdbc.queryForObject("SELECT status FROM saga_payment WHERE order_id = ?", String.class, orderId);
        }
    }

    /** 재고 서비스 */
    @Service
    public static class SagaStockService {
        private final JdbcTemplate jdbc;
        private final SagaLog log;

        public SagaStockService(JdbcTemplate jdbc, SagaLog log) {
            this.jdbc = jdbc;
            this.log = log;
        }

        @Transactional
        public void reserve(String item) {
            int changed = jdbc.update("UPDATE saga_stock SET quantity = quantity - 1 WHERE item = ? AND quantity >= 1", item);
            if (changed == 0) {
                log.add("재고 차감 실패");
                throw new OutOfStockException(item);
            }
            log.add("재고 차감");
        }
    }
}
