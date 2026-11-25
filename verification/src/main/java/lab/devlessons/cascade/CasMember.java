package lab.devlessons.cascade;

import jakarta.persistence.*;

/** 흔한 실수: 다 쪽(@ManyToOne)에 cascade ALL을 걸었다 */
@Entity
@Table(name = "cas_member")
public class CasMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "team_id")
    private CasTeam team;

    protected CasMember() {
    }

    public CasMember(String name, CasTeam team) {
        this.name = name;
        this.team = team;
    }

    public Long getId() {
        return id;
    }
}
