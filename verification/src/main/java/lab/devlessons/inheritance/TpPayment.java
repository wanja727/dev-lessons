package lab.devlessons.inheritance;

import jakarta.persistence.*;

/** 결제 수단의 부모. 상속 전략: TABLE_PER_CLASS */
@Entity
@Table(name = "tp_payment")
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class TpPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tp_seq")
    @SequenceGenerator(name = "tp_seq", sequenceName = "tp_payment_seq")
    private Long id;

    private long amount;

    protected TpPayment() {
    }

    protected TpPayment(long amount) {
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }
}
