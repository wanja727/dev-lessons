package lab.devlessons.osiv;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.*;

/** 컨트롤러에는 트랜잭션이 없다. 서비스가 돌려준 엔티티를 트랜잭션 밖에서 다룬다 */
@RestController
@RequestMapping("/osiv/players/{id}")
public class OsivController {

    private final OsivService service;
    private final DataSource dataSource;

    public OsivController(OsivService service, DataSource dataSource) {
        this.service = service;
        this.dataSource = dataSource;
    }

    /** 트랜잭션이 끝난 뒤 LAZY 연관을 읽는다 */
    @GetMapping("/team-name")
    public String teamName(@PathVariable Long id) {
        OsivPlayer player = service.findPlayer(id);
        return player.getTeam().getName();
    }

    /** 서비스가 DTO를 돌려주면 컨트롤러는 엔티티를 만지지 않는다 */
    @GetMapping("/team-name-dto")
    public String teamNameFromDto(@PathVariable Long id) {
        return service.findView(id).teamName();
    }

    /** 컨트롤러에서 엔티티를 바꾸기만 하고 쓰기 트랜잭션은 부르지 않는다 */
    @PostMapping("/rename-only")
    public String renameOnly(@PathVariable Long id, @RequestParam String name) {
        service.findPlayer(id).rename(name);
        return "ok";
    }

    /** 서비스 트랜잭션이 끝난 뒤, 이 요청이 아직 쥐고 있는 DB 커넥션 수 */
    @GetMapping("/connections-after-service")
    public int connectionsAfterService(@PathVariable Long id) throws SQLException {
        service.findPlayer(id);
        return dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections();
    }

    /** 컨트롤러에서 엔티티를 바꾼 뒤, 상관없는 쓰기 트랜잭션을 부른다 */
    @PostMapping("/rename-then-visit")
    public String renameThenVisit(@PathVariable Long id, @RequestParam String name) {
        OsivPlayer player = service.findPlayer(id);
        player.rename(name);          // 화면 표시용으로 잠깐 바꿨을 뿐
        service.recordVisit();
        return "ok";
    }
}
