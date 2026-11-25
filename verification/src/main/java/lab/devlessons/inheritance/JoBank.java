package lab.devlessons.inheritance;

import jakarta.persistence.*;

@Entity
@Table(name = "jo_bank")
public class JoBank extends JoPayment {

    private String bankCode;

    protected JoBank() {
    }

    public JoBank(long amount, String bankCode) {
        super(amount);
        this.bankCode = bankCode;
    }
}
