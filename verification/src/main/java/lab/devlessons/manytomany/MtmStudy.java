package lab.devlessons.manytomany;

import jakarta.persistence.*;

/** 레슨 "다대다와 매핑 테이블"(D18)의 스터디 */
@Entity
@Table(name = "mtm_study")
public class MtmStudy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    protected MtmStudy() {
    }

    public MtmStudy(String title) {
        this.title = title;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }
}
