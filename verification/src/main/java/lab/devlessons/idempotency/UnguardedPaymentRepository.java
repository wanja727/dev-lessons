package lab.devlessons.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UnguardedPaymentRepository extends JpaRepository<UnguardedPayment, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    long countByIdempotencyKey(String idempotencyKey);

    long countByOrderId(Long orderId);
}
