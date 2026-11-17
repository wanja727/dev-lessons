package lab.devlessons.inventory;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 상품 재고. 버전 컬럼이 없다 (읽고-계산하고-쓰기, 조건부 UPDATE, 비관적 락에서 쓴다) */
@Entity
@Table(name = "inventory_item")
public class InventoryItem {

    @Id
    private Long id;

    private int quantity;

    protected InventoryItem() {
    }

    public InventoryItem(Long id, int quantity) {
        this.id = id;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public int getQuantity() {
        return quantity;
    }

    public void decrease() {
        this.quantity--;
    }
}
