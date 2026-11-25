package lab.devlessons.domainmodel;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 도메인 모델의 주문: 세터가 없고, 상태를 바꾸는 길은 메서드뿐이다. 주문 상품은 이 애그리거트 안에 있다 */
@Entity
@Table(name = "a2_model_order")
public class ModelOrder {

    @Id
    @GeneratedValue
    private Long id;
    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.PAID;
    /** 다른 애그리거트(회원)는 id로 가리킨다 */
    private Long memberId;
    private long totalPrice;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id")
    private List<ModelOrderLine> lines = new ArrayList<>();

    protected ModelOrder() {
    }

    public ModelOrder(Long memberId) {
        this.memberId = memberId;
    }

    public void addLine(A2Product product, int quantity, long price) {
        lines.add(new ModelOrderLine(product, quantity, price));
        totalPrice += price * quantity;
    }

    public void ship() {
        status = OrderStatus.SHIPPED;
    }

    public void cancel() {
        if (status == OrderStatus.SHIPPED) {
            throw new CannotCancel(status);
        }
        status = OrderStatus.CANCELED;
        lines.forEach(ModelOrderLine::restock);
    }

    public Long getId() {
        return id;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Long getMemberId() {
        return memberId;
    }

    public long getTotalPrice() {
        return totalPrice;
    }

    public List<ModelOrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
