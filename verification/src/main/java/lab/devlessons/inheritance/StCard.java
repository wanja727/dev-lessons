package lab.devlessons.inheritance;

import jakarta.persistence.*;

@Entity
public class StCard extends StPayment {

    private String cardNumber;

    protected StCard() {
    }

    public StCard(long amount, String cardNumber) {
        super(amount);
        this.cardNumber = cardNumber;
    }
}
