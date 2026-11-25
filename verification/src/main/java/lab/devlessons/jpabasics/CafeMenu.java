package lab.devlessons.jpabasics;

import jakarta.persistence.*;

/**
 * 엔티티: DB 테이블 cafe_menu의 한 행을 나타내는 자바 클래스.
 * 필드 이름(name)과 컬럼 이름(menu_name)을 일부러 다르게 둬서 JPQL과 SQL의 차이를 보여준다.
 */
@Entity
@Table(name = "cafe_menu")
public class CafeMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // id는 DB가 만든다
    private Long id;

    @Column(name = "menu_name", nullable = false, unique = true)
    private String name;

    private int price;

    private boolean soldOut;        // 카멜 케이스 필드는 sold_out 컬럼이 된다

    protected CafeMenu() {          // JPA가 조회 결과로 객체를 만들 때 쓰는 기본 생성자
    }

    public CafeMenu(String name, int price) {
        this.name = name;
        this.price = price;
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
}
