package lab.devlessons.enumlesson;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** @Enumerated를 붙이지 않은 엔티티. 기본값 ORDINAL로 저장된다 */
@Entity
@Table(name = "enum_order")
public class OrdinalOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private OrderStatus status;

    protected OrdinalOrder() {
    }

    public OrdinalOrder(OrderStatus status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public OrderStatus getStatus() {
        return status;
    }
}
