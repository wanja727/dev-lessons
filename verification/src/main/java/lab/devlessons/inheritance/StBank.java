package lab.devlessons.inheritance;

import jakarta.persistence.*;

@Entity
public class StBank extends StPayment {

    private String bankCode;

    protected StBank() {
    }

    public StBank(long amount, String bankCode) {
        super(amount);
        this.bankCode = bankCode;
    }
}
