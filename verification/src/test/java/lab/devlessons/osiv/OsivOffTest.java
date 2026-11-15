package lab.devlessons.osiv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lab.devlessons.support.PostgresContainerConfig;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** spring.jpa.open-in-view=false (이 저장소의 application.yml 기본값) */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfig.class)
class OsivOffTest {

    @Autowired MockMvc mvc;
    @Autowired OsivTeamRepository teams;
    @Autowired OsivPlayerRepository players;
    Long playerId;

    @BeforeEach
    void setUp() {
        playerId = OsivFixture.create(teams, players);
    }

    @Test
    @DisplayName("OSIV 꺼짐: 컨트롤러에서 LAZY 연관을 읽으면 LazyInitializationException")
    void lazyLoadingInControllerFails() {
        assertThatThrownBy(() -> mvc.perform(get("/osiv/players/{id}/team-name", playerId)))
                .hasRootCauseInstanceOf(LazyInitializationException.class);
    }

    @Test
    @DisplayName("OSIV 꺼짐: 서비스 트랜잭션이 끝나면 커넥션을 바로 풀에 돌려준다 (사용 중 0개)")
    void connectionReleasedAfterService() throws Exception {
        mvc.perform(get("/osiv/players/{id}/connections-after-service", playerId))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));
    }

    @Test
    @DisplayName("OSIV 꺼짐: 컨트롤러에서 바꾼 엔티티는 준영속이라 다른 쓰기 트랜잭션이 커밋돼도 반영되지 않는다")
    void controllerChangeNotFlushed() throws Exception {
        mvc.perform(post("/osiv/players/{id}/rename-then-visit", playerId).param("name", "바뀐이름"))
                .andExpect(status().isOk());

        assertThat(players.findById(playerId).orElseThrow().getName()).isEqualTo("kim");
    }
}
