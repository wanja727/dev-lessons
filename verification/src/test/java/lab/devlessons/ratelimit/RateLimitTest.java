package lab.devlessons.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/** 레슨 "요청 수 제한"의 주장을 Redis 컨테이너로 검증한다. 시각은 밀리초 값으로 넘겨 창의 경계를 정확히 맞춘다. */
class RateLimitTest {

    static final String LESSON = "rate-limit";

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    static RedisClient client;
    static StatefulRedisConnection<String, String> conn;
    static RedisCommands<String, String> redis;

    /** 고정 창: 창마다 키 하나를 INCR하고, 처음이면 만료를 준다. 한도 안이면 1 */
    static final String FIXED = """
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[2]) end
            if n <= tonumber(ARGV[1]) then return 1 end
            return 0
            """;

    /** 토큰 버킷: 지난 시각부터 흐른 시간만큼 토큰을 채우고, 하나 있으면 쓴다. ARGV: 용량, 초당 채움, 지금(ms) */
    static final String BUCKET = """
            local cap = tonumber(ARGV[1])
            local rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local t = redis.call('HMGET', KEYS[1], 'tokens', 'at')
            local tokens = tonumber(t[1]) or cap
            local at = tonumber(t[2]) or now
            tokens = math.min(cap, tokens + (now - at) * rate / 1000)
            local ok = 0
            if tokens >= 1 then tokens = tokens - 1 ok = 1 end
            redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'at', tostring(now))
            redis.call('PEXPIRE', KEYS[1], 60000)
            return ok
            """;

    @BeforeAll
    static void start() {
        REDIS.start();
        client = RedisClient.create(RedisURI.create(REDIS.getHost(), REDIS.getMappedPort(6379)));
        conn = client.connect();
        redis = conn.sync();
    }

    @AfterAll
    static void stop() {
        conn.close();
        client.shutdown();
        REDIS.stop();
    }

    @BeforeEach
    void flush() {
        redis.flushall();
    }

    /** 고정 창 1초에 limit개. now는 밀리초. 창 번호를 키에 넣는다 */
    static boolean fixedWindow(String user, int limit, long now) {
        long window = now / 1000;
        Long ok = redis.eval(FIXED, ScriptOutputType.INTEGER,
                new String[] {"rl:fixed:" + user + ":" + window}, String.valueOf(limit), "1000");
        return ok == 1;
    }

    static boolean bucket(String user, int capacity, int perSecond, long now) {
        Long ok = redis.eval(BUCKET, ScriptOutputType.INTEGER,
                new String[] {"rl:bucket:" + user}, String.valueOf(capacity), String.valueOf(perSecond), String.valueOf(now));
        return ok == 1;
    }

    @Test
    @DisplayName("고정 창(1초에 5번): 같은 창 안의 6번째부터 거절하고, 다른 사용자는 따로 센다")
    void fixedWindowLimits() {
        long t = 10_000;
        List<String> a = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            a.add(fixedWindow("A", 5, t + i * 10) ? "허용" : "거절");
        }
        boolean b = fixedWindow("B", 5, t + 80);

        assertThat(a).containsExactly("허용", "허용", "허용", "허용", "허용", "거절", "거절");
        assertThat(b).isTrue();
        CiOutput.record(LESSON, "fixed", "[고정 창, 1초에 5번, 사용자 A가 0.07초 동안 7번]\n"
                + "A: " + String.join(", ", a) + "\n"
                + "같은 때 사용자 B의 첫 요청: " + (b ? "허용" : "거절"));
    }

    @Test
    @DisplayName("고정 창의 경계: 창이 바뀌는 순간 앞뒤로 5번씩 보내면 0.2초 안에 10번이 허용된다. 토큰 버킷은 같은 요청에서 5번과 채워진 만큼만 허용한다")
    void boundaryBurst() {
        int fixedOk = 0;
        int bucketOk = 0;
        // 창 경계 10초 직전 0.9~0.99초와 직후 0.0~0.09초에 5번씩
        long[] times = {10_900, 10_920, 10_940, 10_960, 10_980, 11_000, 11_020, 11_040, 11_060, 11_080};
        for (long t : times) {
            if (fixedWindow("A", 5, t)) {
                fixedOk++;
            }
            if (bucket("A", 5, 5, t)) {
                bucketOk++;
            }
        }

        assertThat(fixedOk).isEqualTo(10);
        assertThat(bucketOk).isBetween(5, 6);
        CiOutput.record(LESSON, "boundary", "[한도 1초에 5번, 10.90초~11.08초에 10번]\n"
                + "고정 창: 허용 " + fixedOk + "번 (10초 창에 5번, 11초 창에 5번)\n"
                + "토큰 버킷(용량 5, 초당 5개 채움): 허용 " + bucketOk + "번");
    }

    @Test
    @DisplayName("토큰 버킷: 쉬는 동안 용량까지 채워져 몰아 보내기를 허용하고, 그 뒤에는 채우는 속도만큼만 허용한다")
    void bucketRefills() {
        List<String> log = new ArrayList<>();
        int burst = 0;
        for (int i = 0; i < 8; i++) {
            if (bucket("A", 5, 5, 20_000)) {
                burst++;
            }
        }
        log.add("20.0초에 한꺼번에 8번 → 허용 " + burst + "번 (가득 찬 5개)");
        int later = 0;
        for (int i = 0; i < 3; i++) {
            if (bucket("A", 5, 5, 20_400)) {
                later++;
            }
        }
        log.add("0.4초 뒤 3번 → 허용 " + later + "번 (0.4초 동안 2개 채워짐)");

        assertThat(burst).isEqualTo(5);
        assertThat(later).isEqualTo(2);
        CiOutput.record(LESSON, "bucket", "[토큰 버킷: 용량 5, 초당 5개 채움]\n" + String.join("\n", log));
    }

    // ── 스프링 MVC: 인터셉터에서 429와 Retry-After ──

    static class RateLimitInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
            String user = req.getHeader("X-User-Id");
            if (fixedWindow(user, 3, System.currentTimeMillis())) {
                return true;
            }
            res.setStatus(429);
            res.setHeader("Retry-After", "1");
            return false;
        }
    }

    @Configuration
    static class WebConfig implements WebMvcConfigurer {
        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new RateLimitInterceptor()).addPathPatterns("/api/**");
        }
    }

    @RestController
    static class ProductController {
        @GetMapping("/api/products")
        String list() {
            return "ok";
        }
    }

    @Test
    @DisplayName("인터셉터가 한도를 넘은 요청에 429 Too Many Requests와 Retry-After를 돌려준다")
    void interceptorReturns429() throws Exception {
        try (MiniWeb web = MiniWeb.start(WebConfig.class, ProductController.class)) {
            int port = ((ServletWebServerApplicationContext) web.context()).getWebServer().getPort();
            HttpClient http = HttpClient.newHttpClient();
            List<String> log = new ArrayList<>();
            // 창 경계에 걸리지 않게 창의 앞쪽에서 시작한다
            while (System.currentTimeMillis() % 1000 > 500) {
                Thread.sleep(20);
            }
            int last = 0;
            for (int i = 0; i < 4; i++) {
                HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/products"))
                        .header("X-User-Id", "u1").build(), HttpResponse.BodyHandlers.ofString());
                last = res.statusCode();
                log.add("GET /api/products → " + res.statusCode()
                        + res.headers().firstValue("Retry-After").map(v -> " (Retry-After: " + v + ")").orElse(""));
            }
            assertThat(last).isEqualTo(429);
            CiOutput.record(LESSON, "interceptor", "[인터셉터, 사용자마다 1초에 3번]\n" + String.join("\n", log));
        }
    }
}
