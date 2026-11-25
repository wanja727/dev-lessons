package lab.devlessons.inheritance;

import jakarta.persistence.*;

/** 결제 수단의 부모. 상속 전략: JOINED */
@Entity
@Table(name = "jo_payment")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class JoPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long amount;

    protected JoPayment() {
    }

    protected JoPayment(long amount) {
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }
}
