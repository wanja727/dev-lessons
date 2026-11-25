package lab.devlessons.cicd;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.context.LifecycleAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.Yaml;

/**
 * 레슨 "CI/CD와 무중단 배포"(T6). 처리 중인 요청이 있을 때 스프링 부트 서버를 끄면 어떻게 되는지 본다.
 * 웹에 필요한 자동 설정만 켠 작은 앱을 내장 톰캣으로 띄운다 (본보기: support/MiniWeb).
 */
class GracefulShutdownTest {

    static final String LESSON = "ci-cd-deploy";

    @RestController
    public static class PayController {
        @GetMapping("/orders/1/pay")
        public String pay() throws InterruptedException {
            Thread.sleep(3000);
            return "paid";
        }

        @GetMapping("/hello")
        public String hello() {
            return "hi";
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
            ServletWebServerFactoryAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class,
            LifecycleAutoConfiguration.class,
    })
    @Import(PayController.class)
    static class App {
    }

    static ConfigurableApplicationContext start(String... properties) {
        List<String> props = new ArrayList<>(List.of("server.port=0", "spring.main.banner-mode=off"));
        props.addAll(Arrays.asList(properties));
        return new SpringApplicationBuilder(App.class).properties(props.toArray(String[]::new)).run();
    }

    static int port(ConfigurableApplicationContext ctx) {
        return ((ServletWebServerApplicationContext) ctx).getWebServer().getPort();
    }

    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    static String call(int port, String path, Duration timeout) {
        long t = System.nanoTime();
        try {
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .timeout(timeout).GET().build(), HttpResponse.BodyHandlers.ofString(UTF_8));
            return "%d %s (%.1f초)".formatted(r.statusCode(), r.body(), secondsSince(t));
        } catch (Exception e) {
            return "%s (%.1f초)".formatted(e.getClass().getSimpleName(), secondsSince(t));
        }
    }

    static double secondsSince(long nanos) {
        return (System.nanoTime() - nanos) / 1e9;
    }

    record Scenario(String slow, String late, double closeSeconds) {
    }

    /** 3초 걸리는 요청을 보내고 1초 뒤에 서버를 끈다. 끄기 시작한 직후 새 요청도 하나 보낸다 */
    static Scenario run(String... properties) throws Exception {
        ConfigurableApplicationContext ctx = start(properties);
        int port = port(ctx);
        CompletableFuture<String> slow = CompletableFuture.supplyAsync(
                () -> call(port, "/orders/1/pay", Duration.ofSeconds(10)));
        TimeUnit.MILLISECONDS.sleep(1000);
        long t = System.nanoTime();
        CompletableFuture<Void> closing = CompletableFuture.runAsync(ctx::close);
        TimeUnit.MILLISECONDS.sleep(300);
        String late = call(port, "/hello", Duration.ofSeconds(2));
        closing.get(40, TimeUnit.SECONDS);
        double closeSeconds = secondsSince(t);
        return new Scenario(slow.get(15, TimeUnit.SECONDS), late, closeSeconds);
    }

    @Test
    @DisplayName("기본 설정(graceful)에서는 처리 중인 3초 요청이 끝까지 처리되고, 끄는 중에 온 새 요청은 처리되지 않는다")
    void gracefulByDefault() throws Exception {
        Scenario s = run();

        assertThat(s.slow()).startsWith("200 paid");
        assertThat(s.late()).doesNotStartWith("200");
        CiOutput.record(LESSON, "graceful", """
                (스프링 부트 3.5 기본 설정)
                0초: GET /orders/1/pay 보냄 (3초 걸림)
                1초: 서버 끄기 시작
                1.3초: GET /hello 보냄

                /orders/1/pay → %s
                /hello → %s
                서버가 꺼지는 데 걸린 시간 → %.1f초""".formatted(s.slow(), s.late(), s.closeSeconds()));
    }

    @Test
    @DisplayName("server.shutdown=immediate면 처리 중인 요청의 연결이 끊긴다")
    void immediateCutsRequest() throws Exception {
        Scenario s = run("server.shutdown=immediate");

        assertThat(s.slow()).doesNotStartWith("200");
        CiOutput.record(LESSON, "immediate", """
                (server.shutdown=immediate)
                0초: GET /orders/1/pay 보냄 (3초 걸림)
                1초: 서버 끄기 시작

                /orders/1/pay → %s
                서버가 꺼지는 데 걸린 시간 → %.1f초""".formatted(s.slow(), s.closeSeconds()));
    }

    @Test
    @DisplayName("timeout-per-shutdown-phase=1s면 3초 요청을 다 기다리지 않고 끊는다")
    void shutdownTimeoutCutsLongRequest() throws Exception {
        Scenario s = run("spring.lifecycle.timeout-per-shutdown-phase=1s");

        assertThat(s.slow()).doesNotStartWith("200");
        CiOutput.record(LESSON, "timeout", """
                (spring.lifecycle.timeout-per-shutdown-phase=1s)
                0초: GET /orders/1/pay 보냄 (3초 걸림)
                1초: 서버 끄기 시작

                /orders/1/pay → %s
                서버가 꺼지는 데 걸린 시간 → %.1f초""".formatted(s.slow(), s.closeSeconds()));
    }

    @Test
    @DisplayName("새로 띄운 서버의 첫 요청은 그 뒤 요청들의 중앙값보다 느리다")
    void firstRequestIsSlow() {
        try (ConfigurableApplicationContext ctx = start()) {
            int port = port(ctx);
            List<Double> ms = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                long t = System.nanoTime();
                call(port, "/hello", Duration.ofSeconds(5));
                ms.add((System.nanoTime() - t) / 1e6);
            }
            double first = ms.get(0);
            List<Double> rest = new ArrayList<>(ms.subList(1, ms.size()));
            rest.sort(Double::compare);
            double median = rest.get(rest.size() / 2);

            assertThat(first).isGreaterThan(median);
            CiOutput.record(LESSON, "warmup", """
                    (서버를 띄우고 GET /hello를 100번)
                    첫 요청 → %.1f ms
                    2~100번째 요청의 중앙값 → %.1f ms""".formatted(first, median));
        }
    }

    @Test
    @DisplayName("이 저장소의 워크플로: 배포 잡은 빌드 잡이 성공해야 main에서만 돌고, 검증 워크플로는 verification 경로에서만 돈다")
    @SuppressWarnings("unchecked")
    void repositoryWorkflows() throws IOException {
        Path workflows = Path.of("..", ".github", "workflows");
        assertThat(Files.isDirectory(workflows)).isTrue();
        Map<String, Object> pages = load(workflows.resolve("pages.yml"));
        Map<String, Object> verify = load(workflows.resolve("verify.yml"));

        Map<String, Object> deploy = (Map<String, Object>) ((Map<String, Object>) pages.get("jobs")).get("deploy");
        Map<String, Object> verifyOn = (Map<String, Object>) on(verify);
        Map<String, Object> push = (Map<String, Object>) verifyOn.get("push");

        assertThat(deploy.get("needs")).isEqualTo("build");
        assertThat((String) deploy.get("if")).contains("refs/heads/main");
        assertThat((List<String>) push.get("paths")).contains("verification/**");
        assertThat((List<String>) push.get("branches")).containsExactly("main");
        CiOutput.record(LESSON, "workflows", """
                pages.yml의 deploy 잡
                  needs → %s
                  if → %s
                verify.yml의 push 조건
                  branches → %s
                  paths → %s""".formatted(deploy.get("needs"), deploy.get("if"),
                push.get("branches"), push.get("paths")));
    }

    /** SnakeYAML(YAML 1.1)은 키 on을 true로 읽는다 */
    static Object on(Map<String, Object> workflow) {
        Object v = workflow.get("on");
        return v != null ? v : ((Map<Object, Object>) (Map<?, ?>) workflow).get(Boolean.TRUE);
    }

    static Map<String, Object> load(Path file) throws IOException {
        try (Reader r = new FileReader(file.toFile(), UTF_8)) {
            return new Yaml().load(r);
        }
    }
}
