package lab.devlessons.association;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/** 일대다 단방향: 팀이 FK를 관리하지만 FK는 멤버 테이블에 있다 */
@Entity
@Table(name = "uni_team")
public class UniTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @OneToMany
    @JoinColumn(name = "uni_team_id")
    private List<UniMember> members = new ArrayList<>();

    protected UniTeam() {
    }

    public UniTeam(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public List<UniMember> getMembers() {
        return members;
    }
}
