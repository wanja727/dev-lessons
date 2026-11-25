package lab.devlessons.osiv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** spring.jpa.open-in-view=true (스프링 부트의 기본값) */
@SpringBootTest(properties = "spring.jpa.open-in-view=true")
@AutoConfigureMockMvc
@Import(PostgresContainerConfig.class)
class OsivOnTest {

    @Autowired MockMvc mvc;
    @Autowired OsivTeamRepository teams;
    @Autowired OsivPlayerRepository players;
    Long playerId;

    @BeforeEach
    void setUp() {
        playerId = OsivFixture.create(teams, players);
    }

    @Test
    @DisplayName("OSIV 켜짐: 트랜잭션이 끝나도 영속성 컨텍스트가 요청 끝까지 열려 있어 컨트롤러에서 지연 로딩이 된다")
    void lazyLoadingInControllerWorks() throws Exception {
        mvc.perform(get("/osiv/players/{id}/team-name", playerId))
                .andExpect(status().isOk())
                .andExpect(content().string("레드"));
        CiOutput.record("osiv", "on-team-name", "OSIV 켜짐: GET /players/{id}/team-name → 200 \"레드\"");
    }

    @Test
    @DisplayName("OSIV 켜짐: 서비스 트랜잭션이 끝나도 요청이 끝날 때까지 커넥션을 쥐고 있다 (사용 중 1개)")
    void connectionHeldUntilRequestEnds() throws Exception {
        mvc.perform(get("/osiv/players/{id}/connections-after-service", playerId))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
        CiOutput.record("osiv", "on-connections", "OSIV 켜짐: 서비스가 끝난 직후 이 요청이 쥔 커넥션 1개");
    }

    @Test
    @DisplayName("OSIV 켜짐: 컨트롤러에서 바꾼 엔티티가 영속 상태로 남아, 상관없는 쓰기 트랜잭션이 커밋될 때 함께 반영된다")
    void controllerChangeFlushedByLaterTransaction() throws Exception {
        mvc.perform(post("/osiv/players/{id}/rename-then-visit", playerId).param("name", "바뀐이름"))
                .andExpect(status().isOk());

        assertThat(players.findById(playerId).orElseThrow().getName()).isEqualTo("바뀐이름");
        CiOutput.record("osiv", "on-rename", "OSIV 켜짐: 요청 뒤 DB의 선수 이름 = 바뀐이름");
    }

    @Test
    @DisplayName("OSIV 켜짐: 컨트롤러에서 바꾸기만 하고 쓰기 트랜잭션을 부르지 않으면 저장되지 않는다")
    void controllerChangeWithoutWriteTxNotFlushed() throws Exception {
        mvc.perform(post("/osiv/players/{id}/rename-only", playerId).param("name", "바뀐이름"))
                .andExpect(status().isOk());

        assertThat(players.findById(playerId).orElseThrow().getName()).isEqualTo("kim");
    }
}
