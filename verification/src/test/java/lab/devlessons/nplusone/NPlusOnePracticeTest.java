package lab.devlessons.nplusone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * 레슨 "JPA N+1 문제"의 "실무에서는" 구역을 확인한다.
 * - 쿼리 수를 테스트에서 Hibernate 통계로 단정한다
 * - 컬렉션 fetch join에 페이징을 걸면 경고 대신 예외가 나게 하는 설정
 * 데이터: 팀 10개, 팀마다 멤버 3명.
 */
@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
class NPlusOnePracticeTest {

    private static final String LESSON = "jpa-n-plus-1";
    private static final int TEAMS = 10;

    @Autowired TestEntityManager em;
    @Autowired TeamRepository teamRepository;
    @Autowired EntityManagerFactory emf;

    Statistics stats;

    @BeforeEach
    void setUp() {
        for (int i = 1; i <= TEAMS; i++) {
            Team team = em.persist(new Team("팀" + i));
            for (int j = 1; j <= 3; j++) {
                em.persist(new Member("멤버" + i + "-" + j, team));
            }
        }
        em.flush();
        em.clear();
        stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
    }

    @Test
    @DisplayName("Hibernate 통계의 prepareStatementCount로 쿼리 수를 단정한다: findAll 뒤 멤버를 쓰면 11, @EntityGraph면 1")
    void countQueriesWithStatistics() {
        List<Team> teams = teamRepository.findAll();
        teams.forEach(t -> t.getMembers().size());
        long lazy = stats.getPrepareStatementCount();

        em.clear();
        stats.clear();
        List<Team> graphed = teamRepository.findAllBy();
        graphed.forEach(t -> t.getMembers().size());
        long graph = stats.getPrepareStatementCount();

        assertThat(lazy).isEqualTo(1 + TEAMS);
        assertThat(graph).isEqualTo(1);
        CiOutput.record(LESSON, "statistics", "팀 10개, 팀마다 멤버 3명\n"
                + "findAll() 뒤 멤버를 쓰면 → prepareStatementCount " + lazy + "\n"
                + "@EntityGraph findAllBy() → prepareStatementCount " + graph);
    }

    @Test
    @DisplayName("fail_on_pagination_over_collection_fetch=true면 컬렉션 fetch join + 페이징이 경고 대신 예외로 멈춘다")
    void failOnCollectionFetchPaging() {
        assertThatThrownBy(() -> em.getEntityManager()
                .createQuery("select t from Team t left join fetch t.members order by t.id", Team.class)
                .setMaxResults(2)
                .getResultList())
                .satisfies(e -> {
                    CiOutput.record(LESSON, "fail-on-paging", "fail_on_pagination_over_collection_fetch: true\n"
                            + "컬렉션 fetch join + setMaxResults(2)\n"
                            + "→ " + e.getClass().getSimpleName() + ": " + e.getMessage());
                })
                .hasMessageContaining("fail_on_pagination_over_collection_fetch");
    }
}
