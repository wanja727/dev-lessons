package lab.devlessons.domainmodel;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 주문 상품. 바깥에서 바꾸는 공개 메서드가 없다. 주문(루트)을 거쳐서만 생기고 바뀐다 */
@Entity
@Table(name = "a2_model_order_line")
public class ModelOrderLine {

    @Id
    @GeneratedValue
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    private A2Product product;
    private int quantity;
    private long price;

    protected ModelOrderLine() {
    }

    ModelOrderLine(A2Product product, int quantity, long price) {
        this.product = product;
        this.quantity = quantity;
        this.price = price;
    }

    void restock() {
        product.restock(quantity);
    }

    public int getQuantity() {
        return quantity;
    }

    public long getPrice() {
        return price;
    }
}
