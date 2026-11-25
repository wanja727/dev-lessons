package lab.devlessons.osiv;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OsivService {

    private final OsivPlayerRepository players;

    public OsivService(OsivPlayerRepository players) {
        this.players = players;
    }

    /** 엔티티를 그대로 컨트롤러로 돌려준다 (team은 LAZY) */
    @Transactional
    public OsivPlayer findPlayer(Long id) {
        return players.findById(id).orElseThrow();
    }

    /** 화면에 필요한 값만 담은 DTO */
    public record PlayerView(String name, String teamName) {
    }

    /** OSIV를 끈 앱의 해결: 트랜잭션 안에서 지연 로딩까지 끝내고 DTO로 돌려준다 */
    @Transactional(readOnly = true)
    public PlayerView findView(Long id) {
        OsivPlayer p = players.findById(id).orElseThrow();
        return new PlayerView(p.getName(), p.getTeam().getName());
    }

    /** 플레이어와 상관없는 쓰기 트랜잭션. 커밋할 때 영속성 컨텍스트 전체를 flush한다 */
    @Transactional
    public long recordVisit() {
        return players.count();
    }
}
