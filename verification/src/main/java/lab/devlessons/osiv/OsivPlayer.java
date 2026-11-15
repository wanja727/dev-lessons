package lab.devlessons.osiv;

import jakarta.persistence.*;

@Entity
@Table(name = "osiv_player")
public class OsivPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private OsivTeam team;

    protected OsivPlayer() {
    }

    public OsivPlayer(String name, OsivTeam team) {
        this.name = name;
        this.team = team;
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

    public OsivTeam getTeam() {
        return team;
    }
}
