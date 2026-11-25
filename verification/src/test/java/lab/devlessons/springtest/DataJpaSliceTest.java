package lab.devlessons.springtest;

import static org.assertj.core.api.Assertions.assertThat;

import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

/** 레슨 "스프링 테스트"(T3) 질문 2와 3: @DataJpaTest는 JPA만 띄우고 테스트마다 롤백한다 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
@ContextConfiguration(classes = FullContextTest.JpaApp.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DataJpaSliceTest {

    @Autowired StMemberRepository repo;
    @Autowired TestEntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationContext ctx;

    @Test
    @Order(1)
    @DisplayName("같은 이메일로 두 번 save해도 flush하지 않고 롤백되면 테스트는 통과한다")
    void duplicateWithoutFlushPasses() {
        repo.save(new StMember("kim@a.com", null));
        repo.save(new StMember("kim@a.com", null));

        Long rows = jdbc.queryForObject("select count(*) from st_member", Long.class);
        assertThat(rows).isZero();
        assertThat(ctx.getBeanNamesForType(FullContextTest.MemberController.class)).isEmpty();
        assertThat(ctx.getBeanNamesForType(FullContextTest.MemberService.class)).isEmpty();
        CiOutput.record(FullContextTest.LESSON, "jpa-beans", FullContextTest.summary("@DataJpaTest", ctx));
        CiOutput.record(FullContextTest.LESSON, "no-flush", """
                repo.save(new Member("kim@a.com"))
                repo.save(new Member("kim@a.com"))
                (flush 없이 테스트 끝)

                지금 DB의 st_member 행 수 → %d (INSERT가 아직 안 나감)
                테스트 결과 → 통과""".formatted(rows));
    }

    @Test
    @Order(2)
    @DisplayName("같은 코드에 flush를 부르면 유니크 제약 위반이 난다")
    void duplicateWithFlushFails() {
        repo.save(new StMember("lee@a.com", null));
        repo.save(new StMember("lee@a.com", null));

        Throwable e = org.assertj.core.api.Assertions.catchThrowable(em::flush);

        assertThat(e).isNotNull();
        String root = rootMessage(e);
        assertThat(root).contains("duplicate key");
        CiOutput.record(FullContextTest.LESSON, "flush", """
                repo.save(new Member("lee@a.com"))
                repo.save(new Member("lee@a.com"))
                em.flush()
                → %s
                  %s""".formatted(e.getClass().getSimpleName(), root.lines().findFirst().orElse(root)));
    }

    @Test
    @Order(3)
    @DisplayName("앞 테스트에서 저장한 행은 롤백되어 다음 테스트에서 보이지 않는다")
    void previousTestRolledBack() {
        repo.save(new StMember("park@a.com", null));
        em.flush();
        Long inside = jdbc.queryForObject("select count(*) from st_member", Long.class);
        assertThat(inside).isEqualTo(1);
        Long earlier = jdbc.queryForObject(
                "select count(*) from st_member where email in ('kim@a.com', 'lee@a.com')", Long.class);
        assertThat(earlier).isZero();
        CiOutput.record(FullContextTest.LESSON, "rollback", """
                (테스트 1, 2가 kim@a.com, lee@a.com을 저장하고 끝남)
                테스트 3에서 kim, lee의 행 수 → %d
                테스트 3에서 방금 저장하고 flush한 행 수 → %d""".formatted(earlier, inside));
    }

    static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage();
    }
}
