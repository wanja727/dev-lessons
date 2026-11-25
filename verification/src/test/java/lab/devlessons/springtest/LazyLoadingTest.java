package lab.devlessons.springtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import lab.devlessons.springtest.FullContextTest.MemberService;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.hibernate.Hibernate;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.transaction.annotation.Transactional;

/**
 * 레슨 "스프링 테스트"(T3) 질문 3: 테스트의 @Transactional에 서비스가 합류해, 운영에서는 실패할 지연 로딩이 테스트에서는 성공한다.
 * 회원과 팀은 테스트의 트랜잭션 밖에서 미리 저장해(커밋) 둔다. 그래야 find가 DB에서 새로 읽어 팀이 프록시가 된다.
 */
@SpringBootTest(classes = FullContextTest.FullApp.class)
@Import(PostgresContainerConfig.class)
class LazyLoadingTest {

    @Autowired MemberService service;
    @Autowired JdbcTemplate jdbc;

    Long savedId;

    void saveMember() {
        savedId = service.join("kim@a.com", "A팀");
    }

    void deleteAll() {
        jdbc.update("delete from st_member");
        jdbc.update("delete from st_team");
    }

    @Nested
    @Transactional
    @DisplayName("테스트에 @Transactional이 있으면")
    class WithTestTransaction {
        @BeforeTransaction
        void setUp() {
            saveMember();
        }

        @AfterTransaction
        void tearDown() {
            deleteAll();
        }

        @Test
        @DisplayName("find가 끝난 뒤에도 팀의 지연 로딩이 성공한다")
        void lazyLoadingSucceeds() {
            StMember m = service.find(savedId);
            boolean before = Hibernate.isInitialized(m.getTeam());
            String name = m.getTeam().getName();

            assertThat(before).isFalse();
            assertThat(name).isEqualTo("A팀");
            CiOutput.record(FullContextTest.LESSON, "lazy-tx", """
                    (테스트 클래스에 @Transactional)
                    Member m = service.find(savedId)
                    팀을 읽기 전 Hibernate.isInitialized(team) → %s
                    m.getTeam().getName() → %s
                    테스트 결과 → 통과""".formatted(before, name));
        }
    }

    @Nested
    @DisplayName("테스트에 @Transactional이 없으면")
    class WithoutTestTransaction {
        @BeforeEach
        void setUp() {
            saveMember();
        }

        @AfterEach
        void tearDown() {
            deleteAll();
        }

        @Test
        @DisplayName("find가 끝나면 영속성 컨텍스트가 닫혀 LazyInitializationException이 난다")
        void lazyLoadingFails() {
            StMember m = service.find(savedId);
            Throwable e = catchThrowable(() -> m.getTeam().getName());

            assertThat(e).isInstanceOf(LazyInitializationException.class);
            CiOutput.record(FullContextTest.LESSON, "lazy-no-tx", """
                    (테스트에 @Transactional 없음)
                    Member m = service.find(savedId)
                    m.getTeam().getName()
                    → %s
                      %s""".formatted(e.getClass().getSimpleName(), e.getMessage()));
        }
    }
}
