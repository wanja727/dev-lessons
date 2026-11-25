package lab.devlessons.inheritance;

import jakarta.persistence.*;

@Entity
@Table(name = "tp_bank")
public class TpBank extends TpPayment {

    private String bankCode;

    protected TpBank() {
    }

    public TpBank(long amount, String bankCode) {
        super(amount);
        this.bankCode = bankCode;
    }
}
