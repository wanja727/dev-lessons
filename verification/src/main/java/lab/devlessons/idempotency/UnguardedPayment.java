package lab.devlessons.idempotency;

import jakarta.persistence.*;

/** 같은 결제 정보를 담지만 키에 유니크 제약이 없는 테이블. "조회 후 저장"만으로 막을 수 있는지 보여주기 위해 쓴다. */
@Entity
@Table(name = "unguarded_payment")
public class UnguardedPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    private Long orderId;

    private long amount;

    protected UnguardedPayment() {
    }

    public UnguardedPayment(String idempotencyKey, Long orderId, long amount) {
        this.idempotencyKey = idempotencyKey;
        this.orderId = orderId;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }
}
