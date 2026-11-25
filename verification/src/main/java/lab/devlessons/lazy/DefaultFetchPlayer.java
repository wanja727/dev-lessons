package lab.devlessons.lazy;

import jakarta.persistence.*;

/** fetch를 적지 않은 @ManyToOne: 기본값이 EAGER라 선수를 조회하면 팀도 함께 가져온다 */
@Entity
@Table(name = "lazy_default_player")
public class DefaultFetchPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne
    @JoinColumn(name = "team_id")
    private LazyTeam team;

    protected DefaultFetchPlayer() {
    }

    public DefaultFetchPlayer(String name, LazyTeam team) {
        this.name = name;
        this.team = team;
    }

    public Long getId() {
        return id;
    }

    public LazyTeam getTeam() {
        return team;
    }
}
