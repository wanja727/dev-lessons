package lab.devlessons.cascade;

import jakarta.persistence.*;

@Entity
@Table(name = "cas_item")
public class CasItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private int quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private CasOrder order;

    protected CasItem() {
    }

    public CasItem(String name, int quantity) {
        this.name = name;
        this.quantity = quantity;
    }

    void setOrder(CasOrder order) {
        this.order = order;
    }

    public Long getId() {
        return id;
    }
}
