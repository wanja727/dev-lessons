package lab.devlessons.autoconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 레슨 "스프링 부트의 자동 설정"(S4)의 주장을 자동 설정을 켠 작은 웹 앱으로 확인한다.
 * 이 저장소에는 JPA와 배치 스타터가 있어서, DB가 필요한 자동 설정은 exclude로 끄고 그렇다고 레슨에 적었다.
 * 컴포넌트 스캔은 켜지 않고 컨트롤러만 직접 넣는다.
 */
class AutoConfigTest {

    private static final String LESSON = "spring-boot-auto-config";

    public record Order(long orderId, String menuName, int quantity, LocalDate orderedOn) {
    }

    public record OrderSummary(long orderId, String menuName, int quantity) {
    }

    @RestController
    static class OrderController {
        @GetMapping("/orders/1")
        Order order() {
            return new Order(1, "latte", 2, LocalDate.of(2026, 11, 25));
        }

        @GetMapping("/orders/1/summary")
        OrderSummary summary() {
            return new OrderSummary(1, "latte", 2);
        }
    }

    /** DB가 필요한 자동 설정만 끈 앱. 나머지 자동 설정은 모두 켠다 */
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class,
            BatchAutoConfiguration.class, SqlInitializationAutoConfiguration.class})
    @Import(OrderController.class)
    static class ShopApp {
    }

    /** 레슨의 코드 predict. 내 ObjectMapper 빈 */
    @Configuration(proxyBeanMethods = false)
    static class JsonConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    /** 레슨의 코드 customizer */
    @Configuration(proxyBeanMethods = false)
    static class CustomizerConfig {
        @Bean
        Jackson2ObjectMapperBuilderCustomizer snakeCase() {
            return b -> b.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        }
    }

    /** DataSource 자동 설정을 켜 둔 앱 */
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class,
            BatchAutoConfiguration.class})
    static class DbApp {
    }

    private static ConfigurableApplicationContext run(Class<?>[] sources, String... props) {
        String[] all = new String[props.length + 3];
        all[0] = "server.port=0";
        all[1] = "spring.main.banner-mode=off";
        all[2] = "spring.main.log-startup-info=false";
        System.arraycopy(props, 0, all, 3, props.length);
        return new SpringApplicationBuilder(sources).properties(all).run();
    }

    private static String get(ConfigurableApplicationContext ctx, String path) throws Exception {
        int port = ((ServletWebServerApplicationContext) ctx).getWebServer().getPort();
        HttpResponse<String> res = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
        return res.body();
    }

    @Test
    @DisplayName("스프링 부트 3.5의 AutoConfiguration.imports에는 자동 설정 클래스가 백 개 넘게 적혀 있다")
    void importsFileListsManyClasses() {
        List<String> names = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).getCandidates();

        assertThat(names).hasSizeGreaterThan(100);
        assertThat(names).contains("org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration");
        CiOutput.record(LESSON, "imports", "AutoConfiguration.imports에 적힌 자동 설정 클래스: " + names.size() + "개\n(앞의 다섯 개)\n"
                + names.stream().limit(5).map(n -> n.replace("org.springframework.boot.autoconfigure.", "…"))
                        .collect(Collectors.joining("\n")));
    }

    @Test
    @DisplayName("컨트롤러만 둔 앱에서도 자동 설정이 만든 빈이 훨씬 많고, 그 안에 ObjectMapper와 dispatcherServlet이 있다")
    void autoConfiguredBeansOutnumberMine() {
        try (var ctx = run(new Class<?>[] {ShopApp.class})) {
            String[] all = ctx.getBeanDefinitionNames();
            long mine = Arrays.stream(all)
                    .filter(n -> ctx.getType(n) != null && ctx.getType(n).getName().startsWith("lab.devlessons"))
                    .count();

            assertThat(ctx.getBeansOfType(ObjectMapper.class)).containsKey("jacksonObjectMapper");
            assertThat(ctx.containsBean("dispatcherServlet")).isTrue();
            assertThat(all.length - mine).isGreaterThan(100);
            CiOutput.record(LESSON, "bean-count", "전체 빈: " + all.length + "개\n내가 만든 것(lab.devlessons 패키지): " + mine
                    + "개\n그 밖(자동 설정과 스프링 내부): " + (all.length - mine) + "개\n"
                    + "jacksonObjectMapper 있음? " + ctx.containsBean("jacksonObjectMapper")
                    + "\ndispatcherServlet 있음? " + ctx.containsBean("dispatcherServlet"));
        }
    }

    @Test
    @DisplayName("조건 평가 보고서: Redis 자동 설정은 클래스가 없어 빠지고 Jackson 자동 설정은 켜진다. exclude한 것은 exclusions에 나온다")
    void conditionReport() {
        try (var ctx = run(new Class<?>[] {ShopApp.class})) {
            ConditionEvaluationReport report = ConditionEvaluationReport.get(ctx.getBeanFactory());
            Map<String, ConditionEvaluationReport.ConditionAndOutcomes> byClass = report.getConditionAndOutcomesBySource();

            String redis = byClass.get(RedisAutoConfiguration.class.getName()).iterator().next().getOutcome().getMessage();
            String jackson = byClass.get("org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration")
                    .iterator().next().getOutcome().getMessage();
            assertThat(byClass.get(RedisAutoConfiguration.class.getName()).isFullMatch()).isFalse();
            assertThat(byClass.get("org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration").isFullMatch()).isTrue();
            assertThat(report.getExclusions()).contains(DataSourceAutoConfiguration.class.getName());
            CiOutput.record(LESSON, "report", "RedisAutoConfiguration (빠짐):\n  " + redis
                    + "\nJacksonAutoConfiguration (켜짐):\n  " + jackson
                    + "\nexclusions에 DataSourceAutoConfiguration 있음? "
                    + report.getExclusions().contains(DataSourceAutoConfiguration.class.getName()));
        }
    }

    @Test
    @DisplayName("내 ObjectMapper가 있으면 하나만 있고 그것이 쓰이며, spring.jackson 설정은 응답에 적용되지 않는다")
    void myObjectMapperWins() throws Exception {
        try (var ctx = run(new Class<?>[] {ShopApp.class, JsonConfig.class},
                "spring.jackson.property-naming-strategy=SNAKE_CASE")) {
            assertThat(ctx.getBeansOfType(ObjectMapper.class)).containsOnlyKeys("objectMapper");
            String body = get(ctx, "/orders/1/summary");
            String withDate = get(ctx, "/orders/1");

            assertThat(body).contains("\"orderId\"").doesNotContain("order_id");
            assertThat(withDate).contains("\"status\":500");
            CiOutput.record(LESSON, "my-mapper", "ObjectMapper 빈: " + ctx.getBeansOfType(ObjectMapper.class).keySet()
                    + "\nspring.jackson.property-naming-strategy=SNAKE_CASE\nGET /orders/1/summary → " + body);
            CiOutput.record(LESSON, "my-mapper-date", "내 new ObjectMapper()로 LocalDate가 든 Order를 돌려줌\nGET /orders/1 → "
                    + withDate.replaceAll("\"timestamp\":\\d+,", ""));
        }
    }

    @Test
    @DisplayName("설정 값만 적으면 자동 설정의 ObjectMapper가 order_id로 쓰고 날짜는 문자열이다")
    void propertyChangesAutoMapper() throws Exception {
        try (var ctx = run(new Class<?>[] {ShopApp.class}, "spring.jackson.property-naming-strategy=SNAKE_CASE")) {
            String body = get(ctx, "/orders/1");

            assertThat(body).contains("\"order_id\"").contains("\"ordered_on\":\"2026-11-25\"");
            CiOutput.record(LESSON, "property", "spring.jackson.property-naming-strategy=SNAKE_CASE (내 빈 없음)\nGET /orders/1 → " + body);
        }
    }

    @Test
    @DisplayName("커스터마이저를 쓰면 자동 설정이 물러나지 않아 날짜 모듈이 남는다")
    void customizerKeepsAutoConfig() throws Exception {
        try (var ctx = run(new Class<?>[] {ShopApp.class, CustomizerConfig.class})) {
            String body = get(ctx, "/orders/1");

            assertThat(ctx.getBeansOfType(ObjectMapper.class)).containsOnlyKeys("jacksonObjectMapper");
            assertThat(body).contains("\"order_id\"").contains("\"2026-11-25\"");
            CiOutput.record(LESSON, "customizer", "Jackson2ObjectMapperBuilderCustomizer로 SNAKE_CASE\nGET /orders/1 → " + body);
        }
    }

    @Test
    @DisplayName("내가 만든 new ObjectMapper()는 LocalDate를 쓰지 못하고, 자동 설정의 것은 문자열로 쓴다")
    void plainMapperLosesJavaTime() throws Exception {
        Order order = new Order(1, "latte", 2, LocalDate.of(2026, 11, 25));
        String plainError;
        try {
            new ObjectMapper().writeValueAsString(order);
            plainError = "(예외 없음)";
        } catch (Exception e) {
            plainError = e.getClass().getSimpleName() + ": " + e.getMessage().lines().findFirst().orElse("");
        }
        assertThat(plainError).contains("InvalidDefinitionException");
        try (var ctx = run(new Class<?>[] {ShopApp.class})) {
            String auto = ctx.getBean(ObjectMapper.class).writeValueAsString(order);
            assertThat(auto).contains("\"orderedOn\":\"2026-11-25\"");
            CiOutput.record(LESSON, "java-time", "new ObjectMapper() →\n  " + plainError + "\n자동 설정의 ObjectMapper →\n  " + auto);
        }
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("DB 주소 없이 DataSource 자동 설정을 켜면 뜨지 않고, 시작 로그에 'Failed to configure a DataSource'가 나온다")
    void dataSourceWithoutUrlFails(CapturedOutput output) {
        // 테스트 클래스패스에 내장 DB(H2)가 생겨도 레슨의 상황(내장 DB 없음)과 같게 둔다
        assertThatThrownBy(() -> run(new Class<?>[] {DbApp.class}, "spring.main.web-application-type=none",
                "spring.datasource.embedded-database-connection=none"))
                .satisfies(e -> {
                    Throwable t = e;
                    StringBuilder chain = new StringBuilder();
                    while (t != null) {
                        chain.append(t.getMessage()).append('\n');
                        t = t.getCause();
                    }
                    assertThat(chain.toString()).contains("Failed to determine a suitable driver class");
                });
        String log = output.getAll();
        assertThat(log).contains("Failed to configure a DataSource");
        String report = log.substring(log.indexOf("Failed to configure a DataSource"));
        report = report.lines().limit(8).collect(Collectors.joining("\n"));
        CiOutput.record(LESSON, "datasource", report.strip());
    }
}
