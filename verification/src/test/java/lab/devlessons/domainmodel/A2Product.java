package lab.devlessons.domainmodel;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 레슨 "도메인 모델과 트랜잭션 스크립트"의 상품. 두 방식이 함께 쓴다 */
@Entity
@Table(name = "a2_product")
public class A2Product {

    @Id
    @GeneratedValue
    private Long id;
    private int stock;

    protected A2Product() {
    }

    public A2Product(int stock) {
        this.stock = stock;
    }

    public Long getId() {
        return id;
    }

    public int getStock() {
        return stock;
    }

    /** 트랜잭션 스크립트가 쓰는 세터 */
    public void setStock(int stock) {
        this.stock = stock;
    }

    /** 도메인 모델이 쓰는 메서드 */
    void restock(int quantity) {
        this.stock += quantity;
    }
}
