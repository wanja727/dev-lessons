package lab.devlessons.scaleout;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 레슨 "서버를 늘리는 방법"의 주장을 검증한다.
 * 같은 컨트롤러를 내장 톰캣 두 개로 띄우고, 라운드 로빈으로 번갈아 보내는 작은 로드 밸런서가 브라우저처럼 쿠키를 들고 다닌다.
 */
class ScaleOutTest {

    static final String LESSON = "scale-out";

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    static RedisClient redisClient;
    static StatefulRedisConnection<String, String> redisConnection;
    /** 컨트롤러가 함께 쓰는 Redis. 서버 두 대가 같은 Redis를 본다 */
    static RedisCommands<String, String> redis;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        redisClient = RedisClient.create(RedisURI.create(REDIS.getHost(), REDIS.getMappedPort(6379)));
        redisConnection = redisClient.connect();
        redis = redisConnection.sync();
    }

    @AfterAll
    static void stopRedis() {
        redisConnection.close();
        redisClient.shutdown();
        REDIS.stop();
    }

    @BeforeEach
    void flush() {
        redis.flushall();
    }

    // ── 서버 코드 ──

    /** 세션을 서버 메모리(톰캣의 HttpSession)에 둔다 */
    @RestController
    static class MemorySessionController {
        @PostMapping("/login")
        String login(HttpSession session) {
            session.setAttribute("user", "minji");
            return "ok";
        }

        @GetMapping("/me")
        String me(HttpSession session) {
            Object u = session.getAttribute("user");
            return u == null ? "guest" : (String) u;
        }
    }

    /** 세션을 Redis에 둔다. 세션 id는 쿠키로 주고받는다 */
    @RestController
    static class RedisSessionController {
        @PostMapping("/login")
        String login(HttpServletResponse res) {
            String sid = UUID.randomUUID().toString();
            redis.setex("session:" + sid, 1800, "minji");
            res.addHeader("Set-Cookie", "SID=" + sid + "; Path=/; HttpOnly");
            return "ok";
        }

        @GetMapping("/me")
        String me(@CookieValue(name = "SID", required = false) String sid) {
            String u = sid == null ? null : redis.get("session:" + sid);
            return u == null ? "guest" : u;
        }
    }

    /** 조회수를 서버 메모리에 센다 */
    @RestController
    static class CounterController {
        private final AtomicLong views = new AtomicLong();

        @PostMapping("/views")
        long view() {
            return views.incrementAndGet();
        }
    }

    /** 서버마다 도는 스케줄 작업. 실행할 때마다 공유 카운터(Redis)를 올린다 */
    @Configuration
    @EnableScheduling
    static class SchedulingConfig {
    }

    @Component
    static class SettlementJob {
        @Scheduled(fixedRate = 100)
        void settle() {
            redis.incr("settlement-runs");
        }
    }

    /** 설정 값이 true인 서버에서만 켜지는 스케줄 작업 */
    @Component
    @ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true")
    static class GuardedSettlementJob {
        @Scheduled(fixedRate = 100)
        void settle() {
            redis.incr("guarded-runs");
        }
    }

    // ── 라운드 로빈 로드 밸런서: 서버를 차례로 고르고, 쿠키를 브라우저처럼 들고 다닌다 ──

    static class RoundRobin {
        private final List<Integer> ports;
        private final HttpClient http = HttpClient.newHttpClient();
        private final List<String> log = new ArrayList<>();
        private String cookie;
        private int next = 0;

        RoundRobin(MiniWeb... servers) {
            ports = new ArrayList<>();
            for (MiniWeb s : servers) {
                ports.add(((ServletWebServerApplicationContext) s.context()).getWebServer().getPort());
            }
        }

        String send(String method, String path) {
            int index = next;
            next = (next + 1) % ports.size();
            try {
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + ports.get(index) + path))
                        .method(method, HttpRequest.BodyPublishers.noBody());
                if (cookie != null) {
                    b.header("Cookie", cookie);
                }
                HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString(UTF_8));
                String setCookie = res.headers().firstValue("Set-Cookie").map(c -> c.split(";")[0]).orElse(null);
                if (setCookie != null) {
                    cookie = setCookie;
                }
                log.add(method + " " + path + " → 서버 " + (index + 1) + ": " + res.body()
                        + (setCookie == null ? "" : "  (새 쿠키 " + setCookie.substring(0, Math.min(setCookie.length(), 19)) + "...)"));
                return res.body();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        String log() {
            return String.join("\n", log);
        }
    }

    @Test
    @DisplayName("서버 메모리 세션으로 두 서버를 번갈아 보내면 /me 두 번이 guest, guest다. 서버 2가 새 세션을 만들어 쿠키를 덮어쓴다")
    void memorySessionIsLostOnOtherServer() {
        try (MiniWeb s1 = MiniWeb.start(MemorySessionController.class);
             MiniWeb s2 = MiniWeb.start(MemorySessionController.class)) {
            RoundRobin lb = new RoundRobin(s1, s2);
            lb.send("POST", "/login");
            String first = lb.send("GET", "/me");
            String second = lb.send("GET", "/me");

            assertThat(first).isEqualTo("guest");
            assertThat(second).isEqualTo("guest");
            CiOutput.record(LESSON, "memory", "[세션을 서버 메모리에 둠, 라운드 로빈]\n" + lb.log());
        }
    }

    /** 세션을 만들지 않고 읽기만 하는 /me */
    @RestController
    static class ReadOnlySessionController {
        @PostMapping("/login")
        String login(HttpSession session) {
            session.setAttribute("user", "minji");
            return "ok";
        }

        @GetMapping("/me")
        String me(jakarta.servlet.http.HttpServletRequest req) {
            HttpSession s = req.getSession(false);
            Object u = s == null ? null : s.getAttribute("user");
            return u == null ? "guest" : (String) u;
        }
    }

    @Test
    @DisplayName("/me가 세션을 새로 만들지 않으면 서버 메모리 세션에서 guest, minji다")
    void readOnlySessionKeepsCookie() {
        try (MiniWeb s1 = MiniWeb.start(ReadOnlySessionController.class);
             MiniWeb s2 = MiniWeb.start(ReadOnlySessionController.class)) {
            RoundRobin lb = new RoundRobin(s1, s2);
            lb.send("POST", "/login");
            String first = lb.send("GET", "/me");
            String second = lb.send("GET", "/me");

            assertThat(first).isEqualTo("guest");
            assertThat(second).isEqualTo("minji");
            CiOutput.record(LESSON, "readonly", "[/me가 getSession(false)로 읽기만 함]\n" + lb.log());
        }
    }

    @Test
    @DisplayName("세션을 Redis에 두면 같은 순서에서 /me 두 번이 minji, minji다")
    void redisSessionIsShared() {
        try (MiniWeb s1 = MiniWeb.start(RedisSessionController.class);
             MiniWeb s2 = MiniWeb.start(RedisSessionController.class)) {
            RoundRobin lb = new RoundRobin(s1, s2);
            lb.send("POST", "/login");
            String first = lb.send("GET", "/me");
            String second = lb.send("GET", "/me");

            assertThat(first).isEqualTo("minji");
            assertThat(second).isEqualTo("minji");
            CiOutput.record(LESSON, "redis", "[세션을 Redis에 둠, 라운드 로빈]\n" + lb.log());
        }
    }

    @Test
    @DisplayName("서버 메모리 카운터는 서버마다 따로 센다. 네 번 올리면 1, 1, 2, 2")
    void memoryCounterDiverges() {
        try (MiniWeb s1 = MiniWeb.start(CounterController.class);
             MiniWeb s2 = MiniWeb.start(CounterController.class)) {
            RoundRobin lb = new RoundRobin(s1, s2);
            List<String> answers = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                answers.add(lb.send("POST", "/views"));
            }

            assertThat(answers).containsExactly("1", "1", "2", "2");
            CiOutput.record(LESSON, "counter", "[조회수를 서버 메모리에 셈, 라운드 로빈으로 4번]\n" + lb.log());
        }
    }

    private static AnnotationConfigApplicationContext scheduler(boolean enabled) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("app", Map.of("app.scheduling.enabled", String.valueOf(enabled))));
        ctx.register(SchedulingConfig.class, SettlementJob.class, GuardedSettlementJob.class);
        ctx.refresh();
        return ctx;
    }

    @Test
    @DisplayName("@Scheduled 작업은 서버마다 돈다. 설정으로 한 서버에서만 켜면 한 서버만 돈다")
    void scheduledRunsOnEveryServer() throws Exception {
        long all;
        long guarded;
        try (AnnotationConfigApplicationContext server1 = scheduler(true);
             AnnotationConfigApplicationContext server2 = scheduler(false)) {
            Thread.sleep(1_000);
            server1.stop();
            server2.stop();
            all = Long.parseLong(redis.get("settlement-runs"));
            guarded = Long.parseLong(redis.get("guarded-runs"));
        }

        // 100ms마다 1초 동안: 한 서버면 약 10번, 두 서버면 약 20번
        assertThat(all).isGreaterThanOrEqualTo(16);
        assertThat(guarded).isBetween(8L, 12L);
        CiOutput.record(LESSON, "scheduled", "[100ms마다 도는 작업, 서버 두 대, 1초 동안]\n"
                + "그냥 @Scheduled: 실행 " + all + "번 (서버마다 돎)\n"
                + "app.scheduling.enabled를 서버 1에서만 true: 실행 " + guarded + "번");
    }
}
