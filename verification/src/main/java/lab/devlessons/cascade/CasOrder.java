package lab.devlessons.cascade;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/** cascade ALL과 orphanRemoval을 붙인 주문 */
@Entity
@Table(name = "cas_order")
public class CasOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;

    @OneToMany(mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<CasItem> items = new ArrayList<>();

    protected CasOrder() {
    }

    public CasOrder(String code) {
        this.code = code;
    }

    /** 편의 메서드: 양쪽을 맞춘다 */
    public void addItem(CasItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public Long getId() {
        return id;
    }

    public List<CasItem> getItems() {
        return items;
    }

    public void replaceItems(List<CasItem> newItems) {
        this.items = newItems;
    }
}
