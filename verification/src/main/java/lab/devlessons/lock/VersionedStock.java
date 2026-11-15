package lab.devlessons.lock;

import jakarta.persistence.*;

/** @Version이 있는 재고. 낙관적 락 */
@Entity
@Table(name = "versioned_stock")
public class VersionedStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int quantity;

    @Version
    private Long version;

    protected VersionedStock() {
    }

    public VersionedStock(int quantity) {
        this.quantity = quantity;
    }

    public void decrease(int n) {
        if (quantity < n) {
            throw new IllegalStateException("재고 부족");
        }
        quantity -= n;
    }

    public Long getId() {
        return id;
    }

    public int getQuantity() {
        return quantity;
    }

    public Long getVersion() {
        return version;
    }
}
