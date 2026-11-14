package lab.devlessons.idempotency;

import jakarta.persistence.*;

/** 멱등 키에 유니크 제약이 있는 결제 테이블. */
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    private Long orderId;

    private long amount;

    protected Payment() {
    }

    public Payment(String idempotencyKey, Long orderId, long amount) {
        this.idempotencyKey = idempotencyKey;
        this.orderId = orderId;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Long getOrderId() {
        return orderId;
    }

    public long getAmount() {
        return amount;
    }
}
