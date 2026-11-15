package lab.devlessons.lazy;

import jakarta.persistence.*;

/** team은 지연 로딩: 플레이어를 조회하면 팀 자리에는 프록시가 들어간다 */
@Entity
@Table(name = "lazy_player")
public class LazyPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private LazyTeam team;

    protected LazyPlayer() {
    }

    public LazyPlayer(String name, LazyTeam team) {
        this.name = name;
        this.team = team;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LazyTeam getTeam() {
        return team;
    }
}
