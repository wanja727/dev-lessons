package lab.devlessons.transaction;

import jakarta.persistence.*;

@Entity
@Table(name = "purchase_order")
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    protected PurchaseOrder() {
    }

    public PurchaseOrder(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }
}
