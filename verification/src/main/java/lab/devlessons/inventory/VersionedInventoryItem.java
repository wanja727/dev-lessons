package lab.devlessons.inventory;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** 버전 컬럼이 있는 재고 (낙관적 락) */
@Entity
@Table(name = "inventory_item_versioned")
public class VersionedInventoryItem {

    @Id
    private Long id;

    private int quantity;

    @Version
    private long version;

    protected VersionedInventoryItem() {
    }

    public VersionedInventoryItem(Long id, int quantity) {
        this.id = id;
        this.quantity = quantity;
    }

    public int getQuantity() {
        return quantity;
    }

    public void decrease() {
        this.quantity--;
    }
}
