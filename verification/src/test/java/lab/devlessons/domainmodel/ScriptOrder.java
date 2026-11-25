package lab.devlessons.domainmodel;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 트랜잭션 스크립트의 주문: 값만 들고 세터를 연다. 규칙은 서비스에 있다 */
@Entity
@Table(name = "a2_script_order")
public class ScriptOrder {

    @Id
    @GeneratedValue
    private Long id;
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    @ManyToOne(fetch = FetchType.LAZY)
    private A2Product product;
    private int quantity;

    protected ScriptOrder() {
    }

    public ScriptOrder(OrderStatus status, A2Product product, int quantity) {
        this.status = status;
        this.product = product;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public A2Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }
}
