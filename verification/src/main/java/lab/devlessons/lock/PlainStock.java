package lab.devlessons.lock;

import jakarta.persistence.*;

/** 버전 컬럼이 없는 재고. 락 없이 쓰거나 비관적 락으로 쓴다 */
@Entity
@Table(name = "plain_stock")
public class PlainStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int quantity;

    protected PlainStock() {
    }

    public PlainStock(int quantity) {
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
}
