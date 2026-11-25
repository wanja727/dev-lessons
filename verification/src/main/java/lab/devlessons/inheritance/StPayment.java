package lab.devlessons.inheritance;

import jakarta.persistence.*;

/** 결제 수단의 부모. 상속 전략: SINGLE_TABLE */
@Entity
@Table(name = "st_payment")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public abstract class StPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long amount;

    protected StPayment() {
    }

    protected StPayment(long amount) {
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }
}
