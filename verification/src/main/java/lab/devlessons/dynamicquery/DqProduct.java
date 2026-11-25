package lab.devlessons.dynamicquery;

import jakarta.persistence.*;

/** 동적 쿼리 레슨의 상품 */
@Entity
@Table(name = "dq_product")
public class DqProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private int price;

    private String category;

    protected DqProduct() {
    }

    public DqProduct(String name, int price, String category) {
        this.name = name;
        this.price = price;
        this.category = category;
    }

    public String getName() {
        return name;
    }
}
