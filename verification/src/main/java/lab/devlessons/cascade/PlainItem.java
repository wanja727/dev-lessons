package lab.devlessons.cascade;

import jakarta.persistence.*;

@Entity
@Table(name = "plain_item")
public class PlainItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private int quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private PlainOrder order;

    protected PlainItem() {
    }

    public PlainItem(String name, int quantity) {
        this.name = name;
        this.quantity = quantity;
    }

    void setOrder(PlainOrder order) {
        this.order = order;
    }

    public Long getId() {
        return id;
    }
}
