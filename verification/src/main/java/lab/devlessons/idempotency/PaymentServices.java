package lab.devlessons.idempotency;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 레슨 "멱등성과 중복 요청 방지"의 세 가지 서버 구현. */
public final class PaymentServices {

    private PaymentServices() {
    }

    /** 1. 아무 대책 없음: 요청마다 결제를 저장한다 */
    @Service
    public static class NaivePaymentService {
        private final UnguardedPaymentRepository repository;

        public NaivePaymentService(UnguardedPaymentRepository repository) {
            this.repository = repository;
        }

        @Transactional
        public void pay(Long orderId, long amount) {
            repository.save(new UnguardedPayment(null, orderId, amount));
        }
    }

    /** 2. 먼저 조회하고 없으면 저장: 순차 재시도는 막지만 동시 요청은 못 막는다 */
    @Service
    public static class CheckThenInsertPaymentService {
        private final UnguardedPaymentRepository repository;
        /** 테스트에서 "조회와 저장 사이"를 재현하려고 끼워 넣는 지점 */
        private volatile Runnable afterCheck = () -> { };

        public CheckThenInsertPaymentService(UnguardedPaymentRepository repository) {
            this.repository = repository;
        }

        public void setAfterCheck(Runnable afterCheck) {
            this.afterCheck = afterCheck;
        }

        @Transactional
        public boolean pay(String key, Long orderId, long amount) {
            if (repository.existsByIdempotencyKey(key)) {
                return false; // 이미 처리됨
            }
            afterCheck.run();
            repository.save(new UnguardedPayment(key, orderId, amount));
            return true;
        }
    }

    /** 3. 멱등 키 + 유니크 제약: 저장을 시도하고, 중복이면 처음 결과를 돌려준다 */
    @Service
    public static class IdempotentPaymentService {
        private final PaymentRepository repository;

        public IdempotentPaymentService(PaymentRepository repository) {
            this.repository = repository;
        }

        /** 트랜잭션을 걸지 않는다: saveAndFlush가 자기 트랜잭션에서 INSERT하고, 실패하면 다시 조회한다 */
        public Payment pay(String key, Long orderId, long amount) {
            try {
                return repository.saveAndFlush(new Payment(key, orderId, amount));
            } catch (DataIntegrityViolationException duplicate) {
                Payment first = repository.findByIdempotencyKey(key).orElseThrow(() -> duplicate);
                if (first.getAmount() != amount || !first.getOrderId().equals(orderId)) {
                    throw new IllegalStateException("같은 멱등 키로 다른 내용의 요청이 왔습니다: " + key);
                }
                return first;
            }
        }
    }
}
