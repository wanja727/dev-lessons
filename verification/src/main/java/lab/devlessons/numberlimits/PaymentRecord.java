package lab.devlessons.numberlimits;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * 레슨 "숫자 타입의 한계"(J19)의 실무 구역. 금액을 BigDecimal로, 열을 NUMERIC(19, 2)로 둔다.
 */
@Entity
@Table(name = "nl_payment")
public class PaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;

    protected PaymentRecord() {
    }

    public PaymentRecord(BigDecimal amount) {
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
