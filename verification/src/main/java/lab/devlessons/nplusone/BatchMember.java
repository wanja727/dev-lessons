package lab.devlessons.nplusone;

import jakarta.persistence.*;

@Entity
@Table(name = "batch_member")
public class BatchMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private BatchTeam team;

    protected BatchMember() {
    }

    public BatchMember(String name, BatchTeam team) {
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

    public BatchTeam getTeam() {
        return team;
    }
}
