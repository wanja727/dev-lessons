package lab.devlessons.association;

import jakarta.persistence.*;

/** 연관관계의 주인 (FK team_id를 가진 쪽) */
@Entity
@Table(name = "assoc_member")
public class AssocMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private AssocTeam team;

    protected AssocMember() {
    }

    public AssocMember(String name) {
        this.name = name;
    }

    /** 주인 쪽만 설정한다 (FK는 저장되지만 메모리의 team.members는 그대로) */
    public void assignTeamOnly(AssocTeam team) {
        this.team = team;
    }

    /** 연관관계 편의 메서드: 양쪽을 함께 맞춘다 */
    public void changeTeam(AssocTeam team) {
        if (this.team != null) {
            this.team.getMembers().remove(this);
        }
        this.team = team;
        if (team != null) {
            team.getMembers().add(this);
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public AssocTeam getTeam() {
        return team;
    }
}
