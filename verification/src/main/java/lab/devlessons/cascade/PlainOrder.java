package lab.devlessons.cascade;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/** cascade를 적지 않은 주문 */
@Entity
@Table(name = "plain_order")
public class PlainOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;

    @OneToMany(mappedBy = "order")
    private List<PlainItem> items = new ArrayList<>();

    protected PlainOrder() {
    }

    public PlainOrder(String code) {
        this.code = code;
    }

    /** 편의 메서드: 양쪽을 맞춘다 */
    public void addItem(PlainItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public Long getId() {
        return id;
    }

    public List<PlainItem> getItems() {
        return items;
    }

    public void replaceItems(List<PlainItem> newItems) {
        this.items = newItems;
    }
}
