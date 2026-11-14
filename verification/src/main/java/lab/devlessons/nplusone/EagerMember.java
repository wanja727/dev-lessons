package lab.devlessons.nplusone;

import jakarta.persistence.*;

@Entity
@Table(name = "eager_member")
public class EagerMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private EagerTeam team;

    protected EagerMember() {
    }

    public EagerMember(String name, EagerTeam team) {
        this.name = name;
        this.team = team;
        team.getMembers().add(this);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public EagerTeam getTeam() {
        return team;
    }
}
