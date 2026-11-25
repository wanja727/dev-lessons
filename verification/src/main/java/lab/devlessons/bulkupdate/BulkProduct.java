package lab.devlessons.bulkupdate;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 벌크 연산 레슨의 상품. 버전과 수정 시각 콜백이 벌크 연산에서 빠지는지 보려고 둔다 */
@Entity
@Table(name = "bulk_product")
public class BulkProduct {

    @Id
    private Long id;

    private String name;

    private int price;

    @Version
    private long version;

    private LocalDateTime updatedAt;

    protected BulkProduct() {
    }

    public BulkProduct(Long id, String name, int price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public void changePrice(int price) {
        this.price = price;
    }

    public void rename(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getPrice() {
        return price;
    }

    public long getVersion() {
        return version;
    }
}
