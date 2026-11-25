package lab.devlessons.configprofiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.validation.annotation.Validated;

/**
 * 레슨 "설정과 프로파일"(S5)의 주장을 작은 스프링 부트 앱으로 확인한다.
 * 설정 파일은 이 테스트만의 이름(src/test/resources/s5shop.yml, s5shop-local.yml)을 써서
 * 저장소의 application.yml과 섞이지 않게 한다.
 * 환경 변수는 실제로 바꿀 수 없어서, 운영체제 환경 변수 자리(systemEnvironment)에 맵을 넣은 Environment로 흉내 낸다.
 */
class ConfigProfilesTest {

    private static final String LESSON = "config-profiles";

    /** 레슨의 코드 props */
    @ConfigurationProperties("payment.pg")
    @Validated
    public record PgProperties(@NotBlank String url, Duration timeout, String secret) {
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({PropertyPlaceholderAutoConfiguration.class, ConfigurationPropertiesAutoConfiguration.class,
            ValidationAutoConfiguration.class})
    @EnableConfigurationProperties(PgProperties.class)
    static class ShopApp {
    }

    public interface PgClient {
    }

    static class FakePgClient implements PgClient {
    }

    static class RealPgClient implements PgClient {
    }

    @Configuration(proxyBeanMethods = false)
    static class PgClientConfig {
        @Bean
        @Profile("local")
        PgClient fakePgClient() {
            return new FakePgClient();
        }

        @Bean
        @Profile("!local")
        PgClient realPgClient() {
            return new RealPgClient();
        }
    }

    static class TypoValue {
        @Value("${payment.pg.timout:3s}")
        Duration timeout;
    }

    static class MissingValue {
        @Value("${payment.pg.timout}")
        Duration timeout;
    }

    static class RelaxedValue {
        @Value("${mail.retry-count:3}")
        int retry;
    }

    /** 운영체제 환경 변수 자리에 맵을 넣은 Environment */
    static class FakeOsEnvironment extends StandardEnvironment {
        static final Map<String, Object> VARS = new HashMap<>();

        @Override
        protected void customizePropertySources(MutablePropertySources sources) {
            super.customizePropertySources(sources);
            sources.replace(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                    new SystemEnvironmentPropertySource(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, new HashMap<>(VARS)));
        }
    }

    private static ConfigurableApplicationContext run(Map<String, Object> env, List<Class<?>> sources, String... args) {
        FakeOsEnvironment.VARS.clear();
        FakeOsEnvironment.VARS.putAll(env);
        return new SpringApplicationBuilder(sources.toArray(Class<?>[]::new))
                .environment(new FakeOsEnvironment())
                .web(WebApplicationType.NONE)
                .properties("spring.config.name=s5shop", "spring.main.banner-mode=off", "spring.main.log-startup-info=false")
                .run(args);
    }

    private static String url(ConfigurableApplicationContext ctx) {
        return ctx.getBean(PgProperties.class).url();
    }

    @Test
    @DisplayName("같은 키는 실행 인자, 환경 변수, 프로파일 파일, 기본 파일 순으로 이긴다")
    void precedence() {
        Map<String, Object> env = Map.of("PAYMENT_PG_URL", "https://env.example.com");
        List<Class<?>> app = List.of(ShopApp.class);
        List<String> lines = new ArrayList<>();
        try (var ctx = run(env, app, "--spring.profiles.active=local", "--payment.pg.url=https://arg.example.com")) {
            assertThat(url(ctx)).isEqualTo("https://arg.example.com");
            lines.add("네 곳 모두 → " + url(ctx));
        }
        try (var ctx = run(env, app, "--spring.profiles.active=local")) {
            assertThat(url(ctx)).isEqualTo("https://env.example.com");
            lines.add("실행 인자를 빼면 → " + url(ctx));
        }
        try (var ctx = run(Map.of(), app, "--spring.profiles.active=local")) {
            assertThat(url(ctx)).isEqualTo("http://localhost:9000");
            lines.add("환경 변수도 빼면 → " + url(ctx));
        }
        try (var ctx = run(Map.of(), app)) {
            assertThat(url(ctx)).isEqualTo("https://pg.example.com");
            lines.add("프로파일도 끄면 → " + url(ctx));
        }
        CiOutput.record(LESSON, "precedence", String.join("\n", lines));
    }

    @Test
    @DisplayName("이기는 곳에 없는 키는 아래쪽 값이 남는다. 환경 변수 PAYMENT_PG_URL은 payment.pg.url로 읽힌다")
    void perKeyAndRelaxedEnv() {
        try (var ctx = run(Map.of("PAYMENT_PG_TIMEOUT", "5s"), List.of(ShopApp.class), "--payment.pg.url=https://arg.example.com")) {
            PgProperties p = ctx.getBean(PgProperties.class);

            assertThat(p.url()).isEqualTo("https://arg.example.com");
            assertThat(p.timeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(p.secret()).isEqualTo("none");
            CiOutput.record(LESSON, "per-key", "실행 인자: --payment.pg.url=https://arg.example.com\n환경 변수: PAYMENT_PG_TIMEOUT=5s\n"
                    + "→ url=" + p.url() + "\n→ timeout=" + p.timeout() + " (환경 변수)\n→ secret=" + p.secret() + " (파일의 기본값)");
        }
    }

    @Test
    @DisplayName("Environment의 출처 목록은 실행 인자, 시스템 속성, 환경 변수, 설정 파일 순이다")
    void propertySourceOrder() {
        try (var ctx = run(Map.of(), List.of(ShopApp.class), "--spring.profiles.active=local", "--x=1")) {
            List<String> names = new ArrayList<>();
            for (PropertySource<?> ps : ((ConfigurableEnvironment) ctx.getEnvironment()).getPropertySources()) {
                names.add(ps.getName());
            }
            int args = names.indexOf("commandLineArgs");
            int sys = names.indexOf("systemProperties");
            int envIdx = names.indexOf("systemEnvironment");
            int profileFile = indexContaining(names, "s5shop-local.yml");
            int baseFile = indexContaining(names, "s5shop.yml");

            assertThat(args).isLessThan(sys);
            assertThat(sys).isLessThan(envIdx);
            assertThat(envIdx).isLessThan(profileFile);
            assertThat(profileFile).isLessThan(baseFile);
            List<String> shown = names.stream()
                    .filter(n -> !n.equals("configurationProperties") && !n.startsWith("random") && !n.equals("defaultProperties")
                            && !n.equals("servletConfigInitParams") && !n.equals("servletContextInitParams"))
                    .map(n -> n.replaceAll("Config resource 'class path resource \\[(.*)]' via location 'optional:classpath:/'", "$1"))
                    .toList();
            CiOutput.record(LESSON, "sources", String.join("\n", shown));
        }
    }

    private static int indexContaining(List<String> names, String part) {
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).contains(part)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    @DisplayName("@Profile(\"local\") 빈은 local일 때만, @Profile(\"!local\") 빈은 그 밖일 때 만들어진다")
    void profileBeans() {
        try (var local = run(Map.of(), List.of(ShopApp.class, PgClientConfig.class), "--spring.profiles.active=local");
                var none = run(Map.of(), List.of(ShopApp.class, PgClientConfig.class))) {
            assertThat(local.getBean(PgClient.class)).isInstanceOf(FakePgClient.class);
            assertThat(none.getBean(PgClient.class)).isInstanceOf(RealPgClient.class);
            CiOutput.record(LESSON, "profile-bean", "spring.profiles.active=local → " + local.getBean(PgClient.class).getClass().getSimpleName()
                    + "\n프로파일 없음 → " + none.getBean(PgClient.class).getClass().getSimpleName());
        }
    }

    @Test
    @DisplayName("@ConfigurationProperties record는 2s를 Duration으로 받고, 바꿀 수 없는 값이나 @NotBlank 위반이면 뜨지 않는다")
    void configurationPropertiesFailFast() {
        try (var ctx = run(Map.of(), List.of(ShopApp.class))) {
            assertThat(ctx.getBean(PgProperties.class).timeout()).isEqualTo(Duration.ofSeconds(2));
        }
        List<String> lines = new ArrayList<>();
        assertThatThrownBy(() -> run(Map.of(), List.of(ShopApp.class), "--payment.pg.timeout=2초"))
                .satisfies(e -> lines.add("payment.pg.timeout=2초 → " + reason(e)));
        assertThatThrownBy(() -> run(Map.of(), List.of(ShopApp.class), "--payment.pg.url="))
                .satisfies(e -> lines.add("payment.pg.url= (빈 값) → " + reason(e)));
        assertThat(lines.get(0)).contains("Duration");
        assertThat(lines.get(1)).contains("url");
        CiOutput.record(LESSON, "fail-fast", String.join("\n", lines));
    }

    private static String reason(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable t = e;
        String last = "";
        while (t != null) {
            last = t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()).lines().findFirst().orElse("");
            if (t.getMessage() != null && (t.getMessage().contains("Failed to convert") || t.getMessage().contains("must not be blank")
                    || t.getMessage().contains("비어 있을 수 없습니다"))) {
                sb.append(last);
                break;
            }
            t = t.getCause();
        }
        return sb.length() > 0 ? sb.toString() : last;
    }

    @Test
    @DisplayName("@Value 키 이름이 틀리면 기본값이 있으면 조용히 기본값이, 없으면 Could not resolve placeholder로 뜨지 않는다")
    void valueTypo() {
        try (var ctx = run(Map.of(), List.of(ShopApp.class, TypoValue.class))) {
            assertThat(ctx.getBean(TypoValue.class).timeout).isEqualTo(Duration.ofSeconds(3));
        }
        assertThatThrownBy(() -> run(Map.of(), List.of(ShopApp.class, MissingValue.class)))
                .satisfies(e -> {
                    Throwable t = e;
                    while (t.getCause() != null) {
                        t = t.getCause();
                    }
                    assertThat(t.getMessage()).contains("Could not resolve placeholder 'payment.pg.timout'");
                    CiOutput.record(LESSON, "value-typo", "@Value(\"${payment.pg.timout:3s}\") → 3초 (파일의 2초가 아니라 기본값)\n"
                            + "@Value(\"${payment.pg.timout}\") → " + t.getClass().getSimpleName() + ": "
                            + t.getMessage().lines().findFirst().orElse(""));
                });
    }

    @Test
    @DisplayName("@Value(\"${mail.retry-count:3}\")는 파일의 mail.retryCount: 5를 읽는다")
    void valueRelaxedBinding() {
        try (var ctx = run(Map.of(), List.of(ShopApp.class, RelaxedValue.class))) {
            int retry = ctx.getBean(RelaxedValue.class).retry;

            assertThat(retry).isEqualTo(5);
            CiOutput.record(LESSON, "value-relaxed", "파일: mail.retryCount: 5\n@Value(\"${mail.retry-count:3}\") → " + retry);
        }
    }
}
