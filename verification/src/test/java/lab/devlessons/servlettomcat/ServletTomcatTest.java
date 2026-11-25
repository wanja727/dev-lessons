package lab.devlessons.servlettomcat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.embedded.EmbeddedWebServerFactoryCustomizerAutoConfiguration;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** 레슨 "서블릿과 톰캣"(S1)의 주장을 내장 톰캣으로 띄운 작은 앱으로 확인한다. DB 설정은 켜지 않는다. */
class ServletTomcatTest {

    private static final String LESSON = "servlet-tomcat";

    /** 레슨의 코드 thread와 같다 */
    @RestController
    static class OrderController {

        static final Map<String, CountDownLatch> GATES = new ConcurrentHashMap<>();

        @GetMapping("/orders/{id}")
        String order(@PathVariable long id) {
            return "order " + id + " on " + Thread.currentThread().getName();
        }

        /** 두 요청이 서로를 기다리게 해서 동시에 처리 중인 것을 보장한다 */
        @GetMapping("/together/{gate}")
        String together(@PathVariable String gate) throws InterruptedException {
            CountDownLatch latch = GATES.computeIfAbsent(gate, g -> new CountDownLatch(2));
            latch.countDown();
            latch.await(5, TimeUnit.SECONDS);
            return Thread.currentThread().getName();
        }

        /** 레슨의 코드 predict와 같다 */
        @GetMapping("/slow/{id}")
        String slow(@PathVariable long id) throws InterruptedException {
            long entered = System.nanoTime();
            Thread.sleep(1000);
            return entered + " " + Thread.currentThread().getName();
        }

        @GetMapping("/very-slow")
        String verySlow() throws InterruptedException {
            Thread.sleep(3000);
            return "late";
        }

        @GetMapping("/same-thread")
        String sameThread(@RequestAttribute("filterThread") String filterThread) {
            return "필터: " + filterThread + "\n컨트롤러: " + Thread.currentThread().getName();
        }
    }

    /** 필터에서 본 스레드 이름을 요청에 담아 컨트롤러로 넘긴다 */
    @Configuration(proxyBeanMethods = false)
    static class ThreadFilterConfig {
        @Bean
        Filter threadFilter() {
            return (ServletRequest req, ServletResponse res, FilterChain chain) -> {
                req.setAttribute("filterThread", Thread.currentThread().getName());
                chain.doFilter(req, res);
            };
        }
    }

    /** server.tomcat.* 설정을 톰캣에 넣는 자동 설정. 보통 앱에서는 @SpringBootApplication이 켠다 */
    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(EmbeddedWebServerFactoryCustomizerAutoConfiguration.class)
    static class TomcatSettings {
    }

    /** init과 service가 불린 횟수를 센다 */
    static class CountingServlet extends HttpServlet {
        static final AtomicInteger INITS = new AtomicInteger();
        static final AtomicInteger SERVICES = new AtomicInteger();
        static final Set<Integer> INSTANCES = ConcurrentHashMap.newKeySet();

        @Override
        public void init() {
            INITS.incrementAndGet();
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
            SERVICES.incrementAndGet();
            INSTANCES.add(System.identityHashCode(this));
            res.getWriter().write("hello");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CountingServletConfig {
        @Bean
        ServletRegistrationBean<CountingServlet> counting() {
            return new ServletRegistrationBean<>(new CountingServlet(), "/hello");
        }
    }

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    @DisplayName("컨트롤러는 http-nio-포트-exec-번호 이름의 톰캣 스레드에서 돈다")
    void controllerRunsOnTomcatThread() {
        try (MiniWeb web = MiniWeb.start(OrderController.class)) {
            String body = web.get("/orders/7").body();

            assertThat(body).matches("order 7 on http-nio-[\\w-]+-exec-\\d+");
            CiOutput.record(LESSON, "thread", "GET /orders/7\n→ " + body.replaceAll("nio-auto-\\d+-", "nio-8080-"));
        }
    }

    @Test
    @DisplayName("동시에 처리 중인 두 요청은 서로 다른 스레드에서 돈다")
    void concurrentRequestsUseDifferentThreads() throws Exception {
        try (MiniWeb web = MiniWeb.start(OrderController.class)) {
            CompletableFuture<HttpResponse<String>> a = http.sendAsync(get(web, "/together/g1"), HttpResponse.BodyHandlers.ofString());
            CompletableFuture<HttpResponse<String>> b = http.sendAsync(get(web, "/together/g1"), HttpResponse.BodyHandlers.ofString());
            String ta = a.get(10, TimeUnit.SECONDS).body();
            String tb = b.get(10, TimeUnit.SECONDS).body();

            assertThat(ta).isNotEqualTo(tb);
            CiOutput.record(LESSON, "concurrent", ("요청 A → " + ta + "\n요청 B → " + tb).replaceAll("nio-auto-\\d+-", "nio-8080-"));
        }
    }

    @Test
    @DisplayName("한 요청의 필터와 컨트롤러는 같은 스레드에서 돈다")
    void filterAndControllerShareThread() {
        try (MiniWeb web = MiniWeb.start(OrderController.class, ThreadFilterConfig.class)) {
            String body = web.get("/same-thread").body();
            String[] lines = body.split("\n");

            assertThat(lines[0].substring("필터: ".length())).isEqualTo(lines[1].substring("컨트롤러: ".length()));
            CiOutput.record(LESSON, "same-thread", body.replaceAll("nio-auto-\\d+-", "nio-8080-"));
        }
    }

    @Test
    @DisplayName("스레드 2개에 1초 걸리는 요청 3개를 함께 보내면 셋째는 한 스레드가 풀린 뒤에 처리되고 에러 없이 응답한다")
    void thirdRequestWaitsForThread() throws Exception {
        try (MiniWeb web = MiniWeb.startWith(new String[] {"server.tomcat.threads.max=2", "server.tomcat.threads.min-spare=2"},
                OrderController.class, TomcatSettings.class)) {
            web.get("/orders/0"); // 스레드와 연결을 미리 띄워 둔다

            long start = System.nanoTime();
            List<CompletableFuture<long[]>> calls = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                calls.add(http.sendAsync(get(web, "/slow/" + i), HttpResponse.BodyHandlers.ofString())
                        .thenApply(r -> {
                            assertThat(r.statusCode()).isEqualTo(200);
                            long entered = Long.parseLong(r.body().split(" ")[0]);
                            return new long[] {(entered - start) / 1_000_000, (System.nanoTime() - start) / 1_000_000};
                        }));
            }
            List<long[]> ms = new ArrayList<>();
            for (CompletableFuture<long[]> c : calls) {
                ms.add(c.get(10, TimeUnit.SECONDS));
            }
            ms.sort((x, y) -> Long.compare(x[0], y[0]));

            // 셋째 요청은 앞의 요청 하나가 1초 쉬고 스레드를 돌려준 뒤에야 컨트롤러에 들어간다
            assertThat(ms.get(2)[0] - ms.get(0)[0]).isGreaterThanOrEqualTo(900L);
            assertThat(ms.get(1)[0] - ms.get(0)[0]).isLessThan(500L);
            StringBuilder sb = new StringBuilder("server.tomcat.threads.max=2\n요청 3개를 동시에 보냄\n");
            String[] names = {"첫째", "둘째", "셋째"};
            for (int i = 0; i < 3; i++) {
                sb.append(String.format("%s: 컨트롤러 시작 %.1f초, 응답 %.1f초 (200)",
                        names[i], ms.get(i)[0] / 1000.0, ms.get(i)[1] / 1000.0)).append('\n');
            }
            CiOutput.record(LESSON, "pool-full", sb.toString().strip());
        }
    }

    @Test
    @DisplayName("톰캣에 등록된 스프링 MVC의 서블릿은 dispatcherServlet 하나이고 /에 걸려 있다")
    void dispatcherServletIsTheOnlyServlet() {
        try (MiniWeb web = MiniWeb.start(OrderController.class)) {
            var ctx = (ServletWebServerApplicationContext) web.context();
            Map<String, ? extends ServletRegistration> regs = ctx.getServletContext().getServletRegistrations();
            Map<String, String> shown = new TreeMap<>();
            regs.forEach((name, reg) -> shown.put(name, String.valueOf(reg.getMappings())));

            assertThat(regs.get("dispatcherServlet").getMappings()).containsExactly("/");
            StringBuilder sb = new StringBuilder();
            shown.forEach((name, mappings) -> sb.append(name).append(" → ").append(mappings).append('\n'));
            CiOutput.record(LESSON, "servlets", sb.toString().strip());
        }
    }

    @Test
    @DisplayName("서블릿은 하나만 만들어진다: 요청 5개에 init 1번, service 5번, 객체 하나")
    void servletIsSingleInstance() {
        CountingServlet.INITS.set(0);
        CountingServlet.SERVICES.set(0);
        CountingServlet.INSTANCES.clear();
        try (MiniWeb web = MiniWeb.start(CountingServletConfig.class)) {
            for (int i = 0; i < 5; i++) {
                assertThat(web.get("/hello").body()).isEqualTo("hello");
            }

            assertThat(CountingServlet.INITS.get()).isEqualTo(1);
            assertThat(CountingServlet.SERVICES.get()).isEqualTo(5);
            assertThat(CountingServlet.INSTANCES).hasSize(1);
            CiOutput.record(LESSON, "lifecycle", "요청 5개를 보냄\ninit 호출: " + CountingServlet.INITS.get()
                    + "번\nservice 호출: " + CountingServlet.SERVICES.get()
                    + "번\n서블릿 객체: " + CountingServlet.INSTANCES.size() + "개");
        }
    }

    @Test
    @DisplayName("읽기 타임아웃 1초를 준 RestClient는 3초 걸리는 서버를 부르면 약 1초에 예외를 던진다")
    void readTimeoutReleasesThread() {
        try (MiniWeb web = MiniWeb.start(OrderController.class)) {
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
            factory.setReadTimeout(Duration.ofSeconds(1));
            RestClient client = RestClient.builder()
                    .baseUrl("http://localhost:" + web.port())
                    .requestFactory(factory)
                    .build();

            long start = System.nanoTime();
            assertThatThrownBy(() -> client.get().uri("/very-slow").retrieve().body(String.class))
                    .isInstanceOf(ResourceAccessException.class);
            long ms = (System.nanoTime() - start) / 1_000_000;

            assertThat(ms).isBetween(900L, 2500L);
            CiOutput.record(LESSON, "timeout", String.format(
                    "서버 응답: 3초 걸림\n읽기 타임아웃: 1초\n"
                            + "예외까지 걸린 시간: %.1f초\n예외: ResourceAccessException", ms / 1000.0));
        }
    }

    private static HttpRequest get(MiniWeb web, String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + web.port() + path)).GET().build();
    }
}
