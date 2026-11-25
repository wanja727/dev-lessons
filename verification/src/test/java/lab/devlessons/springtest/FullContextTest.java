package lab.devlessons.springtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 레슨 "스프링 테스트"(T3). @SpringBootTest는 컨트롤러, 서비스, 리포지토리를 모두 띄운다.
 *
 * <p>레슨의 컨트롤러, 서비스, 설정은 이 테스트 클래스 안에 둔다. 다른 레슨의 @SpringBootTest가
 * 컴포넌트 스캔으로 가져가지 않게 하려는 것이다(스프링 부트의 TestTypeExcludeFilter가 테스트 클래스 안의 클래스를 뺀다).
 */
@SpringBootTest(classes = FullContextTest.FullApp.class)
@Import(PostgresContainerConfig.class)
class FullContextTest {

    static final String LESSON = "spring-test";

    /** 회원 기능 전체: 자동 설정, 이 패키지의 엔티티와 리포지토리, 컨트롤러와 서비스 */
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = StMember.class)
    @EnableJpaRepositories(basePackageClasses = StMember.class)
    @Import({MemberController.class, MemberService.class, NotFoundAdvice.class})
    static class FullApp {
    }

    /** 웹 계층만: 컨트롤러와 예외 처리 */
    @Configuration(proxyBeanMethods = false)
    @Import({MemberController.class, NotFoundAdvice.class})
    static class WebApp {
    }

    /** JPA만: 이 패키지의 엔티티와 리포지토리 */
    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = StMember.class)
    @EnableJpaRepositories(basePackageClasses = StMember.class)
    static class JpaApp {
    }

    public static class NoMember extends RuntimeException {
        public NoMember(Long id) {
            super("없는 회원: " + id);
        }
    }

    @Service
    public static class MemberService {
        private final StMemberRepository members;
        private final StTeamRepository teams;

        public MemberService(StMemberRepository members, StTeamRepository teams) {
            this.members = members;
            this.teams = teams;
        }

        @Transactional
        public Long join(String email, String teamName) {
            StTeam team = teams.save(new StTeam(teamName));
            return members.save(new StMember(email, team)).getId();
        }

        @Transactional(readOnly = true)
        public StMember find(Long id) {
            return members.findById(id).orElseThrow(() -> new NoMember(id));
        }
    }

    @RestController
    public static class MemberController {
        private final MemberService service;

        public MemberController(MemberService service) {
            this.service = service;
        }

        @GetMapping("/members/{id}")
        public String email(@PathVariable Long id) {
            return service.find(id).getEmail();
        }
    }

    @RestControllerAdvice
    public static class NotFoundAdvice {
        @ExceptionHandler(NoMember.class)
        @ResponseStatus(HttpStatus.NOT_FOUND)
        public String notFound(NoMember e) {
            return e.getMessage();
        }
    }

    @Autowired ApplicationContext ctx;

    @Test
    @DisplayName("@SpringBootTest의 컨텍스트에는 컨트롤러, 서비스, 리포지토리와 자동 설정의 빈이 모두 있다")
    void fullContextHasEverything() {
        assertThat(ctx.getBeanNamesForType(MemberController.class)).hasSize(1);
        assertThat(ctx.getBeanNamesForType(MemberService.class)).hasSize(1);
        assertThat(ctx.getBeanNamesForType(StMemberRepository.class)).hasSize(1);
        int count = ctx.getBeanDefinitionCount();
        assertThat(count).isGreaterThan(200);
        CiOutput.record(LESSON, "full-beans", summary("@SpringBootTest", ctx));
    }

    static String summary(String label, ApplicationContext ctx) {
        return """
                %s
                  빈의 수 → %d
                  MemberController → %s
                  MemberService → %s
                  StMemberRepository → %s
                  DataSource → %s""".formatted(label, ctx.getBeanDefinitionCount(),
                has(ctx, MemberController.class), has(ctx, MemberService.class),
                has(ctx, StMemberRepository.class), has(ctx, javax.sql.DataSource.class));
    }

    static String has(ApplicationContext ctx, Class<?> type) {
        String[] names = ctx.getBeanNamesForType(type);
        if (names.length == 0) {
            return "없음";
        }
        Object bean = ctx.getBean(names[0]);
        boolean mock = org.mockito.Mockito.mockingDetails(bean).isMock();
        return mock ? "있음 (목)" : "있음" + (names.length > 1 ? " " + Arrays.toString(names) : "");
    }
}
