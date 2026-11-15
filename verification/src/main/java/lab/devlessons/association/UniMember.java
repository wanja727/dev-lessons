package lab.devlessons.association;

import jakarta.persistence.*;

/** 일대다 단방향의 멤버. 팀을 모르지만 테이블에는 uni_team_id FK가 있다 */
@Entity
@Table(name = "uni_member")
public class UniMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    protected UniMember() {
    }

    public UniMember(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }
}
