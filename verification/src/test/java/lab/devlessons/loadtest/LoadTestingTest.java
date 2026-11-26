package lab.devlessons.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lab.devlessons.support.CiOutput;
import org.apache.catalina.connector.Connector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.embedded.EmbeddedWebServerFactoryCustomizerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 레슨 "부하 테스트"(T9). 톰캣 스레드를 일부러 적게 둔 서버에 동시 사용자 N명이 쉬지 않고 요청을 보내
 * 처리량과 응답 시간을 잰다. 시간은 실행 환경마다 흔들리므로 단정은 비율과 크기 비교로만 한다.
 */
class LoadTestingTest {

    static final String LESSON = "load-testing";

    @RestController
    public static class ProductController {
        @GetMapping("/products/{id}")
        public String find(@PathVariable Long id) throws InterruptedException {
            Thread.sleep(100); // 외부 재고 API
            return "펜";
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
            ServletWebServerFactoryAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            EmbeddedWebServerFactoryCustomizerAutoConfiguration.class,
    })
    @Import(ProductController.class)
    static class App {
    }

    static ConfigurableApplicationContext start(int threads) {
        return new SpringApplicationBuilder(App.class)
                .properties("server.port=0", "spring.main.banner-mode=off",
                        "server.tomcat.threads.max=" + threads,
                        "server.tomcat.threads.min-spare=" + threads)
                .run();
    }

    static int port(ConfigurableApplicationContext ctx) {
        return ((ServletWebServerApplicationContext) ctx).getWebServer().getPort();
    }

    /** 톰캣 작업 스레드 가운데 지금 요청을 처리하는 수 */
    static int busyThreads(ConfigurableApplicationContext ctx) {
        TomcatWebServer web = (TomcatWebServer) ((ServletWebServerApplicationContext) ctx).getWebServer();
        Connector connector = web.getTomcat().getConnector();
        Object executor = connector.getProtocolHandler().getExecutor();
        if (executor instanceof java.util.concurrent.ThreadPoolExecutor tpe) {
            return tpe.getActiveCount();
        }
        return ((org.apache.tomcat.util.threads.ThreadPoolExecutor) executor).getActiveCount();
    }

    record Result(int users, int done, double seconds, double tps, double meanMs, double p50Ms, double p99Ms,
                  List<long[]> samples) {
        String row() {
            return "%3d명 | 처리량 %5.1f건/초 | 평균 %6.1f ms | p50 %6.1f ms | p99 %6.1f ms".formatted(
                    users, tps, meanMs, p50Ms, p99Ms);
        }
    }

    /** 동시 사용자 users명이 seconds초 동안 쉬지 않고 요청을 보낸다. samples는 {시작 시각(ms), 걸린 시간(µs)} */
    static Result load(int port, int users, double seconds, Runnable midway) throws Exception {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/products/1"))
                .timeout(Duration.ofSeconds(30)).build();
        List<long[]> samples = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger errors = new AtomicInteger();
        long start = System.nanoTime();
        long end = start + (long) (seconds * 1e9);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int u = 0; u < users; u++) {
                pool.submit(() -> {
                    while (System.nanoTime() < end) {
                        long t = System.nanoTime();
                        try {
                            http.send(req, HttpResponse.BodyHandlers.discarding());
                            samples.add(new long[] {(t - start) / 1_000_000, (System.nanoTime() - t) / 1_000});
                        } catch (Exception e) {
                            errors.incrementAndGet();
                        }
                    }
                });
            }
            if (midway != null) {
                TimeUnit.MILLISECONDS.sleep((long) (seconds * 500));
                midway.run();
            }
        }
        double elapsed = (System.nanoTime() - start) / 1e9;
        assertThat(errors.get()).isZero();
        List<Long> lat = samples.stream().map(s -> s[1]).sorted().toList();
        double mean = lat.stream().mapToLong(Long::longValue).average().orElse(0) / 1000.0;
        return new Result(users, lat.size(), elapsed, lat.size() / elapsed, mean,
                pct(lat, 0.50), pct(lat, 0.99), samples);
    }

    static double pct(List<Long> sortedMicros, double p) {
        if (sortedMicros.isEmpty()) {
            return 0;
        }
        int i = (int) Math.ceil(p * sortedMicros.size()) - 1;
        return sortedMicros.get(Math.max(0, i)) / 1000.0;
    }

    @Test
    @DisplayName("스레드 10개, 요청마다 100ms인 서버의 처리량은 초당 100건 가까이에서 멈추고, 그 뒤로 응답 시간만 늘어난다. 스레드를 50개로 늘리면 처리량이 는다")
    void throughputSaturates() throws Exception {
        List<Result> results = new ArrayList<>();
        AtomicInteger busyAt50 = new AtomicInteger(-1);
        Result warm;
        Result wide;
        try (ConfigurableApplicationContext ctx = start(10)) {
            int port = port(ctx);
            warm = load(port, 5, 3, null); // 워밍업: 처음 구간을 따로 본다
            for (int users : new int[] {1, 5, 10, 20, 50}) {
                Runnable probe = users == 50 ? () -> busyAt50.set(busyThreads(ctx)) : null;
                results.add(load(port, users, 3, probe));
            }
        }
        try (ConfigurableApplicationContext ctx = start(50)) {
            int port = port(ctx);
            load(port, 5, 2, null);
            wide = load(port, 50, 3, null);
        }

        Result r10 = results.get(2);
        Result r50 = results.get(4);
        assertThat(r10.tps()).isBetween(60.0, 105.0);
        assertThat(r50.tps()).isLessThan(r10.tps() * 1.3);
        assertThat(r50.p99Ms()).isGreaterThan(r10.p99Ms());
        assertThat(r50.meanMs()).isGreaterThan(r10.meanMs() * 2);
        assertThat(busyAt50.get()).isEqualTo(10);
        assertThat(wide.tps()).isGreaterThan(r50.tps() * 2);
        assertThat(wide.meanMs()).isLessThan(r50.meanMs());

        List<String> rows = new ArrayList<>();
        results.forEach(r -> rows.add(r.row()));
        CiOutput.record(LESSON, "saturation", """
                (톰캣 스레드 10개, 요청마다 100ms, 사용자마다 3초 동안 쉬지 않고 보냄)
                %s

                50명일 때 바쁜 톰캣 스레드 → %d개 / 10개""".formatted(String.join("\n", rows), busyAt50.get()));
        CiOutput.record(LESSON, "threads", """
                (동시 사용자 50명)
                톰캣 스레드 10개 → %s
                톰캣 스레드 50개 → %s""".formatted(r50.row().substring(6), wide.row().substring(6)));

        // 워밍업: 서버를 띄운 직후 첫 0.5초와 그 뒤
        List<Long> early = warm.samples().stream().filter(s -> s[0] < 500).map(s -> s[1]).sorted().toList();
        List<Long> later = warm.samples().stream().filter(s -> s[0] >= 1000).map(s -> s[1]).sorted().toList();
        double earlyMax = early.isEmpty() ? 0 : early.get(early.size() - 1) / 1000.0;
        double laterP50 = pct(later, 0.5);
        assertThat(earlyMax).isGreaterThan(laterP50);
        CiOutput.record(LESSON, "warmup", """
                (서버를 띄운 직후 사용자 5명, 3초)
                처음 0.5초 안에 시작한 요청의 최댓값 → %.1f ms
                1초 뒤부터 시작한 요청의 p50 → %.1f ms""".formatted(earlyMax, laterP50));
    }
}
