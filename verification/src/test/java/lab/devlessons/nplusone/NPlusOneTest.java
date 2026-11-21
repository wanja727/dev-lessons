package lab.devlessons.nplusone;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lab.devlessons.support.PostgresContainerConfig;
import lab.devlessons.support.SqlCapture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;

/**
 * 레슨 "JPA N+1 문제"의 주장을 실제 PostgreSQL에서 SQL 개수로 검증한다.
 * 데이터: 팀 10개, 팀마다 멤버 3명.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class NPlusOneTest {

    private static final int TEAMS = 10;

    @Autowired TestEntityManager em;
    @Autowired TeamRepository teamRepository;
    @Autowired EagerTeamRepository eagerTeamRepository;
    @Autowired BatchTeamRepository batchTeamRepository;

    @BeforeEach
    void setUp() {
        for (int i = 1; i <= TEAMS; i++) {
            Team team = em.persist(new Team("팀" + i));
            EagerTeam eagerTeam = em.persist(new EagerTeam("팀" + i));
            BatchTeam batchTeam = em.persist(new BatchTeam("팀" + i));
            for (int j = 1; j <= 3; j++) {
                em.persist(new Member("멤버" + i + "-" + j, team));
                em.persist(new EagerMember("멤버" + i + "-" + j, eagerTeam));
                em.persist(new BatchMember("멤버" + i + "-" + j, batchTeam));
            }
        }
        em.flush();
        em.clear(); // 영속성 컨텍스트를 비워야 이후 조회가 실제 SQL로 나간다
        SqlCapture.clear();
    }

    @Test
    @DisplayName("LAZY: 팀 10개 조회 후 멤버에 접근하면 쿼리는 1 + N = 11번")
    void lazyAccess() {
        List<Team> teams = teamRepository.findAll();
        teams.forEach(t -> t.getMembers().size());

        assertThat(SqlCapture.count()).isEqualTo(1 + TEAMS);
    }

    @Test
    @DisplayName("LAZY: 멤버에 접근하지 않으면 쿼리는 1번")
    void lazyWithoutAccess() {
        teamRepository.findAll();

        assertThat(SqlCapture.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("EAGER: 멤버에 접근하지 않아도 쿼리는 11번. EAGER는 N+1을 해결하지 못한다")
    void eagerDoesNotFix() {
        eagerTeamRepository.findAll();

        assertThat(SqlCapture.count()).isEqualTo(1 + TEAMS);
    }

    @Test
    @DisplayName("fetch join: 쿼리 1번, 중복 없이 팀 10개 (Hibernate 6은 distinct 없이 중복 제거)")
    void fetchJoin() {
        List<Team> teams = teamRepository.findAllWithMembers();
        teams.forEach(t -> t.getMembers().size());

        assertThat(SqlCapture.count()).isEqualTo(1);
        assertThat(teams).hasSize(TEAMS);
        assertThat(teams).allSatisfy(t -> assertThat(t.getMembers()).hasSize(3));
    }

    @Test
    @DisplayName("@EntityGraph: 쿼리 1번, 중복 없이 팀 10개")
    void entityGraph() {
        List<Team> teams = teamRepository.findAllBy();
        teams.forEach(t -> t.getMembers().size());

        assertThat(SqlCapture.count()).isEqualTo(1);
        assertThat(teams).hasSize(TEAMS);
    }

    @Test
    @DisplayName("배치 페치(크기 5): 쿼리는 1 + ⌈10 / 5⌉ = 3번")
    void batchFetch() {
        List<BatchTeam> teams = batchTeamRepository.findAll();
        teams.forEach(t -> t.getMembers().size());

        assertThat(SqlCapture.count()).isEqualTo(3);
        SqlCapture.sqls().forEach(sql -> System.out.println("[batch] " + sql));
    }

    @Test
    @DisplayName("컬렉션 fetch join + 페이징: SQL에 페이징이 없고, 경고 후 메모리에서 자른다")
    void collectionFetchJoinPaging(CapturedOutput output) {
        List<Team> page = em.getEntityManager()
                .createQuery("select t from Team t left join fetch t.members order by t.id", Team.class)
                .setMaxResults(2)
                .getResultList();

        assertThat(page).hasSize(2);
        assertThat(page).allSatisfy(t -> assertThat(t.getMembers()).hasSize(3));
        assertThat(SqlCapture.count()).isEqualTo(1);
        assertThat(hasPagingClause(SqlCapture.sqls().get(0))).isFalse();
        assertThat(output).contains("HHH90003004");
    }

    @Test
    @DisplayName("대조군: fetch join 없이 페이징하면 SQL에 페이징이 붙는다")
    void pagingWithoutFetchJoin() {
        em.getEntityManager()
                .createQuery("select t from Team t order by t.id", Team.class)
                .setMaxResults(2)
                .getResultList();

        assertThat(hasPagingClause(SqlCapture.sqls().get(0))).isTrue();
    }

    @Test
    @DisplayName("ToOne fetch join + 페이징: SQL에 페이징이 붙고 경고가 없다. 쿼리 1번")
    void toOneFetchJoinPaging(CapturedOutput output) {
        List<Member> page = em.getEntityManager()
                .createQuery("select m from Member m join fetch m.team order by m.id", Member.class)
                .setMaxResults(2)
                .getResultList();
        page.forEach(m -> m.getTeam().getName());

        assertThat(page).hasSize(2);
        assertThat(SqlCapture.count()).isEqualTo(1);
        assertThat(hasPagingClause(SqlCapture.sqls().get(0))).isTrue();
        assertThat(output).doesNotContain("HHH90003004");
    }

    @Test
    @DisplayName("팀만 페이징 + 배치 페치: 쿼리 2번, 첫 SQL에 페이징이 붙는다")
    void pagingWithBatchFetch() {
        List<BatchTeam> page = em.getEntityManager()
                .createQuery("select t from BatchTeam t order by t.id", BatchTeam.class)
                .setMaxResults(2)
                .getResultList();
        page.forEach(t -> t.getMembers().size());

        assertThat(page).hasSize(2);
        assertThat(page).allSatisfy(t -> assertThat(t.getMembers()).hasSize(3));
        assertThat(SqlCapture.count()).isEqualTo(2);
        assertThat(hasPagingClause(SqlCapture.sqls().get(0))).isTrue();
    }

    private static boolean hasPagingClause(String sql) {
        String s = sql.toLowerCase();
        return s.contains(" limit ") || s.contains("fetch first") || s.contains(" offset ");
    }
}
