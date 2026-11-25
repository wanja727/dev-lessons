package lab.devlessons.inheritance;

import jakarta.persistence.*;

@Entity
@Table(name = "jo_card")
public class JoCard extends JoPayment {

    private String cardNumber;

    protected JoCard() {
    }

    public JoCard(long amount, String cardNumber) {
        super(amount);
        this.cardNumber = cardNumber;
    }
}
