package lab.devlessons.springbeans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lab.devlessons.springbeans.common.MailSender;
import lab.devlessons.springbeans.shop.OrderService;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

/**
 * 레슨 "스프링 빈과 의존성 주입"(S2)의 주장을 작은 스프링 컨테이너로 확인한다.
 * 경우마다 필요한 클래스만 등록한 AnnotationConfigApplicationContext를 띄운다. DB와 웹은 켜지 않는다.
 */
class SpringBeansLessonTest {

    private static final String LESSON = "spring-beans";
    static final List<String> LOG = new ArrayList<>();

    // ── 예제: 커피 주문 ──
    public interface NoticeSender {
        String send(String to, String msg);
    }

    @Repository
    static class CoffeeMenuRepository {
        CoffeeMenuRepository() {
            LOG.add("CoffeeMenuRepository 생성");
        }

        int price(String menu) {
            return "latte".equals(menu) ? 5000 : 4500;
        }
    }

    // 중첩 클래스는 빈 이름 앞에 바깥 클래스 이름이 붙어서, 최상위 클래스일 때의 이름을 직접 적는다
    @Component("smsNoticeSender")
    static class SmsNoticeSender implements NoticeSender {
        SmsNoticeSender() {
            LOG.add("SmsNoticeSender 생성");
        }

        public String send(String to, String msg) {
            return "[SMS → " + to + "] " + msg;
        }
    }

    @Component("mailNoticeSender")
    static class MailNoticeSender implements NoticeSender {
        public String send(String to, String msg) {
            return "[메일 → " + to + "] " + msg;
        }
    }

    @Service
    static class CoffeeService {
        private final CoffeeMenuRepository menu;
        private final NoticeSender sender;

        CoffeeService(CoffeeMenuRepository menu, NoticeSender sender) {
            this.menu = menu;
            this.sender = sender;
            LOG.add("CoffeeService 생성 (CoffeeMenuRepository, NoticeSender를 받음)");
        }

        String order(String user, String item) {
            return sender.send(user, item + " " + menu.price(item) + "원 주문 완료");
        }

        NoticeSender sender() {
            return sender;
        }
    }

    @Controller
    static class CoffeeController {
        final CoffeeService service;

        CoffeeController(CoffeeService service) {
            this.service = service;
            LOG.add("CoffeeController 생성 (CoffeeService를 받음)");
        }
    }

    @Service
    static class FieldInjectedService {
        @Autowired
        private NoticeSender sender;

        NoticeSender sender() {
            return sender;
        }
    }

    // ── 고르는 규칙 ──
    @Component("primarySmsSender")
    @Primary
    static class PrimarySmsSender extends SmsNoticeSender {
    }

    @Service
    static class QualifiedCoffeeService {
        final NoticeSender sender;

        QualifiedCoffeeService(@Qualifier("mailNoticeSender") NoticeSender sender) {
            this.sender = sender;
        }
    }

    @Service
    static class NamedCoffeeService {
        final NoticeSender sender;

        NamedCoffeeService(NoticeSender mailNoticeSender) {
            this.sender = mailNoticeSender;
        }
    }

    @Service
    static class AllSendersService {
        final List<NoticeSender> senders;

        AllSendersService(List<NoticeSender> senders) {
            this.senders = senders;
        }
    }

    // ── 순환 ──
    @Component
    static class CircularA {
        CircularA(CircularB b) {
        }
    }

    @Component
    static class CircularB {
        CircularB(CircularA a) {
        }
    }

    // ── @Configuration과 @Bean ──
    static class Report {
        final Clock clock;

        Report(Clock clock) {
            this.clock = clock;
        }
    }

    @Configuration
    static class ClockConfig {
        @Bean
        Clock clock() {
            return Clock.systemDefaultZone();
        }

        @Bean
        Report daily() {
            return new Report(clock());
        }

        @Bean
        Report weekly() {
            return new Report(clock());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class LiteClockConfig {
        @Bean
        Clock clock() {
            return Clock.systemDefaultZone();
        }

        @Bean
        Report daily() {
            return new Report(clock());
        }

        @Bean
        Report weekly() {
            return new Report(clock());
        }
    }

    private static AnnotationConfigApplicationContext context(Class<?>... classes) {
        return new AnnotationConfigApplicationContext(classes);
    }

    private static Throwable rootCause(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t;
    }

    @Test
    @DisplayName("컨테이너는 받을 빈을 먼저 만들고 그것을 넣어 다음 빈을 만든다")
    void creationOrder() {
        LOG.clear();
        try (var ctx = context(CoffeeController.class, CoffeeService.class, SmsNoticeSender.class,
                CoffeeMenuRepository.class)) {
            assertThat(LOG.indexOf("CoffeeMenuRepository 생성"))
                    .isLessThan(LOG.indexOf("CoffeeService 생성 (CoffeeMenuRepository, NoticeSender를 받음)"));
            assertThat(LOG.indexOf("SmsNoticeSender 생성"))
                    .isLessThan(LOG.indexOf("CoffeeService 생성 (CoffeeMenuRepository, NoticeSender를 받음)"));
            assertThat(LOG.indexOf("CoffeeService 생성 (CoffeeMenuRepository, NoticeSender를 받음)"))
                    .isLessThan(LOG.indexOf("CoffeeController 생성 (CoffeeService를 받음)"));
            assertThat(ctx.getBean(CoffeeController.class).service).isSameAs(ctx.getBean(CoffeeService.class));
            CiOutput.record(LESSON, "creation", String.join("\n", LOG));
        }
    }

    @Test
    @DisplayName("new로 만든 필드 주입 객체의 필드는 null이다")
    void newObjectIsNotInjected() {
        try (var ctx = context(FieldInjectedService.class, SmsNoticeSender.class)) {
            assertThat(ctx.getBean(FieldInjectedService.class).sender()).isNotNull();
            FieldInjectedService manual = new FieldInjectedService();
            assertThat(manual.sender()).isNull();
            assertThatThrownBy(() -> manual.sender().send("kim", "hi")).isInstanceOf(NullPointerException.class);
            CiOutput.record(LESSON, "field", "컨테이너가 만든 객체의 sender: "
                    + ctx.getBean(FieldInjectedService.class).sender().getClass().getSimpleName()
                    + "\nnew로 만든 객체의 sender: " + manual.sender());
        }
    }

    @Test
    @DisplayName("생성자 주입은 스프링 없이 가짜를 넣어 쓸 수 있다")
    void constructorInjectionWithoutSpring() {
        NoticeSender fake = (to, msg) -> "가짜:" + to + ":" + msg;
        CoffeeService service = new CoffeeService(new CoffeeMenuRepository(), fake);

        assertThat(service.order("kim", "latte")).isEqualTo("가짜:kim:latte 5000원 주문 완료");
    }

    @Test
    @DisplayName("같은 타입의 빈이 둘이고 하나를 받으면 NoUniqueBeanDefinitionException으로 뜨지 않는다")
    void twoCandidatesFail() {
        assertThatThrownBy(() -> context(CoffeeService.class, CoffeeMenuRepository.class,
                SmsNoticeSender.class, MailNoticeSender.class))
                .isInstanceOf(UnsatisfiedDependencyException.class)
                .satisfies(e -> {
                    assertThat(rootCause(e)).isInstanceOf(NoUniqueBeanDefinitionException.class);
                    String msg = rootCause(e).getMessage().replace("lab.devlessons.springbeans.SpringBeansLessonTest$", "");
                    assertThat(msg).contains("expected single matching bean but found 2");
                    CiOutput.record(LESSON, "no-unique", rootCause(e).getClass().getSimpleName() + ":\n" + msg);
                });
    }

    @Test
    @DisplayName("@Primary, @Qualifier, 매개변수 이름, List로 받으면 들어가는 빈이 정해진다. @Qualifier가 @Primary보다 먼저다")
    void choosingRules() {
        try (var ctx = context(CoffeeService.class, CoffeeMenuRepository.class, PrimarySmsSender.class,
                MailNoticeSender.class, QualifiedCoffeeService.class, AllSendersService.class)) {
            assertThat(ctx.getBean(CoffeeService.class).sender()).isInstanceOf(PrimarySmsSender.class);
            assertThat(ctx.getBean(QualifiedCoffeeService.class).sender).isInstanceOf(MailNoticeSender.class);
            assertThat(ctx.getBean(AllSendersService.class).senders).hasSize(2);
        }
        try (var ctx = context(NamedCoffeeService.class, SmsNoticeSender.class, MailNoticeSender.class)) {
            assertThat(ctx.getBean(NamedCoffeeService.class).sender).isInstanceOf(MailNoticeSender.class);
        }
        try (var ctx = context(CoffeeService.class, CoffeeMenuRepository.class, PrimarySmsSender.class,
                MailNoticeSender.class, QualifiedCoffeeService.class, AllSendersService.class)) {
            String out = "아무 표시 없음 → 앱이 뜨지 않음 (NoUniqueBeanDefinitionException)\n"
                    + "@Primary를 문자 쪽에 → " + ctx.getBean(CoffeeService.class).sender().getClass().getSimpleName() + "\n"
                    + "@Qualifier(\"mailNoticeSender\") → " + ctx.getBean(QualifiedCoffeeService.class).sender.getClass().getSimpleName() + "\n"
                    + "List<NoticeSender> → " + ctx.getBean(AllSendersService.class).senders.stream()
                            .map(s -> s.getClass().getSimpleName()).collect(Collectors.joining(", "));
            CiOutput.record(LESSON, "choose", out);
        }
    }

    @Test
    @DisplayName("빈 이름은 클래스 이름의 첫 글자를 소문자로 바꾼 것이다")
    void beanNames() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.scan("lab.devlessons.springbeans.shop");
            ctx.refresh();
            assertThat(ctx.getBeanNamesForType(OrderService.class)).containsExactly("orderService");
        }
    }

    @Test
    @DisplayName("받아야 할 빈이 없으면 NoSuchBeanDefinitionException, 생성자 순환이면 순환 예외로 뜨지 않는다")
    void missingAndCircular() {
        assertThatThrownBy(() -> context(CoffeeService.class, CoffeeMenuRepository.class))
                .satisfies(e -> {
                    assertThat(rootCause(e)).isInstanceOf(NoSuchBeanDefinitionException.class);
                    CiOutput.record(LESSON, "missing", rootCause(e).getClass().getSimpleName() + ":\n"
                            + rootCause(e).getMessage().replace("lab.devlessons.springbeans.SpringBeansLessonTest$", ""));
                });
        assertThatThrownBy(() -> context(CircularA.class, CircularB.class))
                .satisfies(e -> assertThat(rootCause(e)).isInstanceOf(BeanCurrentlyInCreationException.class));
    }

    @Test
    @DisplayName("스캔 범위 밖 패키지의 @Component는 빈이 되지 않는다")
    void scanRange() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.scan("lab.devlessons.springbeans.shop");
            ctx.refresh();
            assertThat(ctx.getBeansOfType(OrderService.class)).hasSize(1);
            assertThat(ctx.getBeansOfType(MailSender.class)).isEmpty();
            CiOutput.record(LESSON, "scan", "스캔한 패키지: lab.devlessons.springbeans.shop\n"
                    + "OrderService (shop 안) → 빈 " + ctx.getBeansOfType(OrderService.class).size() + "개\n"
                    + "MailSender (common, 밖) → 빈 " + ctx.getBeansOfType(MailSender.class).size() + "개");
        }
    }

    @Test
    @DisplayName("싱글톤 빈은 몇 번 꺼내도 같다. @Configuration은 @Bean 메서드를 여러 번 불러도 같은 빈을, proxyBeanMethods=false면 다른 객체를 준다")
    void singletonAndConfiguration() {
        try (var ctx = context(CoffeeService.class, CoffeeMenuRepository.class, SmsNoticeSender.class)) {
            assertThat(ctx.getBean(CoffeeService.class)).isSameAs(ctx.getBean(CoffeeService.class));
            String scopes = Arrays.stream(ctx.getBeanDefinitionNames())
                    .filter(n -> ctx.getType(n) != null && ctx.getType(n).getName().contains("SpringBeansLessonTest"))
                    .map(n -> ctx.getType(n).getSimpleName() + " → "
                            + (ctx.getBeanDefinition(n).getScope().isEmpty() ? "singleton" : ctx.getBeanDefinition(n).getScope()))
                    .sorted()
                    .collect(Collectors.joining("\n"));
            CiOutput.record(LESSON, "scopes", scopes);
        }
        try (var ctx = context(ClockConfig.class)) {
            Report daily = ctx.getBean("daily", Report.class);
            Report weekly = ctx.getBean("weekly", Report.class);
            assertThat(daily.clock).isSameAs(weekly.clock).isSameAs(ctx.getBean(Clock.class));
            assertThat(ctx.getBean(ClockConfig.class).getClass().getName()).contains("SpringCGLIB");
        }
        try (var ctx = context(LiteClockConfig.class)) {
            Report daily = ctx.getBean("daily", Report.class);
            Report weekly = ctx.getBean("weekly", Report.class);
            assertThat(daily.clock).isNotSameAs(weekly.clock);
        }
        CiOutput.record(LESSON, "config", "@Configuration: daily와 weekly의 Clock이 같은 객체 → true\n"
                + "@Configuration(proxyBeanMethods = false): 같은 객체 → false");
    }

    @Test
    @DisplayName("Clock을 빈으로 받으면 테스트에서 고정된 시각을 넣을 수 있다")
    void clockBeanCanBeFixedInTest() {
        Clock fixed = Clock.fixed(Instant.parse("2026-11-25T00:00:00Z"), ZoneId.of("UTC"));
        Report report = new Report(fixed);

        assertThat(Instant.now(report.clock)).isEqualTo(Instant.parse("2026-11-25T00:00:00Z"));
    }
}
