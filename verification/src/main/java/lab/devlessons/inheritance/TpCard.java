package lab.devlessons.inheritance;

import jakarta.persistence.*;

@Entity
@Table(name = "tp_card")
public class TpCard extends TpPayment {

    private String cardNumber;

    protected TpCard() {
    }

    public TpCard(long amount, String cardNumber) {
        super(amount);
        this.cardNumber = cardNumber;
    }
}
