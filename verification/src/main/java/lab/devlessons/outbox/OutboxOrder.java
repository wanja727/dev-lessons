package lab.devlessons.outbox;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 주문. 주문이 저장되면 "주문됨" 이벤트를 다른 시스템(배송, 알림)에 알려야 한다. */
@Entity
@Table(name = "outbox_order")
public class OutboxOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String item;

    protected OutboxOrder() {
    }

    public OutboxOrder(String item) {
        this.item = item;
    }

    public Long getId() {
        return id;
    }

    public String getItem() {
        return item;
    }
}
