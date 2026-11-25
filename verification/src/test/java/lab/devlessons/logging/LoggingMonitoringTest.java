package lab.devlessons.logging;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 레슨 "로그와 모니터링"(T7). 로그 수준, MDC의 요청 ID, 타이머의 백분위, GC 지표를 실제로 돌려 본다.
 */
class LoggingMonitoringTest {

    static final String LESSON = "logging-monitoring";

    /** 이름 하나의 로거를 INFO로 두고 찍힌 이벤트를 모은다 */
    static ListAppender<ILoggingEvent> capture(String name, Level level) {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = ctx.getLogger(name);
        logger.detachAndStopAllAppenders();
        logger.setLevel(level);
        logger.setAdditive(false);
        // MDC는 이벤트를 읽을 때 가져오므로, 찍히는 순간의 값을 붙잡아 둔다
        ListAppender<ILoggingEvent> app = new ListAppender<>() {
            @Override
            protected void append(ILoggingEvent e) {
                e.prepareForDeferredProcessing();
                super.append(e);
            }
        };
        app.setContext(ctx);
        app.start();
        logger.addAppender(app);
        return app;
    }

    /** toString이 불린 횟수를 센다 */
    static class Cart {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public String toString() {
            calls.incrementAndGet();
            return "Cart[펜 2개]";
        }
    }

    @Test
    @DisplayName("수준이 INFO면 DEBUG는 버려지고, + 로 이어 붙인 인자는 그래도 toString이 불리며 자리표시자는 불리지 않는다")
    void levelsAndPlaceholders() {
        ListAppender<ILoggingEvent> app = capture("t7.order", Level.INFO);
        org.slf4j.Logger log = LoggerFactory.getLogger("t7.order");

        Cart concat = new Cart();
        Cart placeholder = new Cart();
        for (int i = 0; i < 10_000; i++) {
            log.debug("장바구니 " + concat);
            log.debug("장바구니 {}", placeholder);
        }
        log.info("주문 {} 생성", 1001);
        log.warn("재고 부족 {}", "PEN-1");

        assertThat(app.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.INFO, Level.WARN);
        assertThat(concat.calls.get()).isEqualTo(10_000);
        assertThat(placeholder.calls.get()).isZero();
        List<String> lines = app.list.stream()
                .map(e -> e.getLevel() + " " + e.getFormattedMessage()).toList();
        CiOutput.record(LESSON, "levels", """
                (logging.level = INFO)
                찍힌 줄
                %s

                log.debug("장바구니 " + cart) 1만 번
                  → cart.toString() %d번
                log.debug("장바구니 {}", cart) 1만 번
                  → cart.toString() %d번""".formatted(String.join("\n", lines),
                concat.calls.get(), placeholder.calls.get()));
    }

    @Test
    @DisplayName("log.error(메시지, e)는 스택 트레이스를 남기고 getMessage만 이어 붙이면 남지 않는다")
    void exceptionNeedsTheObject() {
        ListAppender<ILoggingEvent> app = capture("t7.pay", Level.INFO);
        org.slf4j.Logger log = LoggerFactory.getLogger("t7.pay");
        Exception e = new IllegalStateException("결제사 응답 없음",
                new java.net.SocketTimeoutException("Read timed out"));

        log.error("결제 실패: " + e.getMessage());
        log.error("결제 실패 주문 {}", 1001, e);

        ILoggingEvent onlyMessage = app.list.get(0);
        ILoggingEvent withThrowable = app.list.get(1);
        assertThat(onlyMessage.getThrowableProxy()).isNull();
        assertThat(withThrowable.getThrowableProxy()).isNotNull();
        assertThat(withThrowable.getThrowableProxy().getCause().getClassName())
                .isEqualTo("java.net.SocketTimeoutException");
        CiOutput.record(LESSON, "exception", """
                log.error("결제 실패: " + e.getMessage())
                  메시지 → %s
                  예외 → %s

                log.error("결제 실패 주문 {}", 1001, e)
                  메시지 → %s
                  예외 → %s: %s
                  원인 → %s: %s""".formatted(onlyMessage.getFormattedMessage(),
                onlyMessage.getThrowableProxy(),
                withThrowable.getFormattedMessage(),
                withThrowable.getThrowableProxy().getClassName(),
                withThrowable.getThrowableProxy().getMessage(),
                withThrowable.getThrowableProxy().getCause().getClassName(),
                withThrowable.getThrowableProxy().getCause().getMessage()));
    }

    // ── MDC ──

    static final AtomicInteger IDS = new AtomicInteger();

    /** 주문 요청에만 요청 ID를 붙인다. remove가 true면 finally에서 지운다 */
    static class RequestIdFilter extends OncePerRequestFilter {
        final boolean remove;

        RequestIdFilter(boolean remove) {
            this.remove = remove;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            if (req.getRequestURI().startsWith("/orders")) {
                MDC.put("requestId", "req-" + IDS.incrementAndGet());
            }
            try {
                chain.doFilter(req, res);
            } finally {
                if (remove) {
                    MDC.remove("requestId");
                }
            }
        }
    }

    @RestController
    public static class OrderController {
        private static final org.slf4j.Logger log = LoggerFactory.getLogger("t7.web");

        @GetMapping("/orders/1")
        public String order() {
            log.info("주문 조회 시작");
            log.info("주문 조회 끝");
            return "ok";
        }

        @GetMapping("/health")
        public String health() {
            log.info("헬스 체크");
            return "up";
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
            ServletWebServerFactoryAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            // server.tomcat.threads.* 설정을 톰캣에 넣는 자동 설정
            org.springframework.boot.autoconfigure.web.embedded.EmbeddedWebServerFactoryCustomizerAutoConfiguration.class,
    })
    @Import(OrderController.class)
    static class WebApp {
    }

    @Configuration(proxyBeanMethods = false)
    static class RemovingFilter {
        @org.springframework.context.annotation.Bean
        RequestIdFilter requestIdFilter() {
            return new RequestIdFilter(true);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class LeakyFilter {
        @org.springframework.context.annotation.Bean
        RequestIdFilter requestIdFilter() {
            return new RequestIdFilter(false);
        }
    }

    static List<String> runRequests(boolean remove) throws Exception {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(WebApp.class,
                remove ? RemovingFilter.class : LeakyFilter.class)
                .properties("server.port=0", "spring.main.banner-mode=off",
                        "server.tomcat.threads.max=1", "server.tomcat.threads.min-spare=1")
                .run();
        // 스프링 부트가 시작하며 로깅 설정을 다시 하므로, 모으는 어펜더는 시작한 뒤에 붙인다
        ListAppender<ILoggingEvent> app = capture("t7.web", Level.INFO);
        try {
            int port = ((ServletWebServerApplicationContext) ctx).getWebServer().getPort();
            HttpClient http = HttpClient.newHttpClient();
            for (String path : List.of("/orders/1", "/health")) {
                http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                        HttpResponse.BodyHandlers.ofString(UTF_8));
            }
        } finally {
            ctx.close();
        }
        return app.list.stream().map(e -> "%-5s [%s] %s (%s)".formatted(e.getLevel(),
                e.getMDCPropertyMap().getOrDefault("requestId", ""), e.getFormattedMessage(),
                e.getThreadName())).toList();
    }

    @Test
    @DisplayName("필터가 MDC에 넣은 requestId가 그 요청의 로그 줄에 붙고, 지우지 않으면 같은 스레드의 다음 요청에 남는다")
    void mdcRequestId() throws Exception {
        List<String> good = runRequests(true);
        List<String> leaky = runRequests(false);

        assertThat(good.get(0)).contains("[req-");
        assertThat(good.get(0).substring(6, 13)).isEqualTo(good.get(1).substring(6, 13));
        assertThat(good.get(2)).contains("[]");
        assertThat(leaky.get(2)).contains("[req-");
        CiOutput.record(LESSON, "mdc", """
                (톰캣 스레드 하나. GET /orders/1 다음 GET /health)

                finally에서 MDC.remove 함
                %s

                MDC.remove 안 함
                %s""".formatted(String.join("\n", good), String.join("\n", leaky)));
    }

    @Test
    @DisplayName("스레드 풀로 넘긴 작업에서는 MDC가 비어 있고, TaskDecorator로 복사하면 남는다")
    void mdcAcrossThreads() throws Exception {
        String without = runOnPool(false);
        String with = runOnPool(true);

        assertThat(without).isEqualTo("null");
        assertThat(with).isEqualTo("req-42");
        CiOutput.record(LESSON, "mdc-async", """
                (요청 스레드에서 MDC.put("requestId", "req-42") 뒤 풀에 일을 넘김)
                풀의 스레드에서 MDC.get("requestId")
                  TaskDecorator 없음 → %s
                  MDC를 복사하는 TaskDecorator → %s""".formatted(without, with));
    }

    static String runOnPool(boolean copy) throws Exception {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        if (copy) {
            pool.setTaskDecorator(task -> {
                var context = MDC.getCopyOfContextMap();
                return () -> {
                    if (context != null) {
                        MDC.setContextMap(context);
                    }
                    try {
                        task.run();
                    } finally {
                        MDC.clear();
                    }
                };
            });
        }
        pool.initialize();
        AtomicReference<String> seen = new AtomicReference<>();
        try {
            MDC.put("requestId", "req-42");
            pool.submit(() -> seen.set(String.valueOf(MDC.get("requestId")))).get(5, TimeUnit.SECONDS);
        } finally {
            MDC.remove("requestId");
            pool.shutdown();
        }
        return seen.get();
    }

    // ── 지표 ──

    @Test
    @DisplayName("95개는 10ms, 5개는 2초인 타이머의 평균은 100ms 남짓이고 p99는 2초 가까이다")
    void percentilesShowSlowRequests() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Timer timer = Timer.builder("order.pay").publishPercentiles(0.5, 0.95, 0.99).register(registry);
        for (int i = 0; i < 95; i++) {
            timer.record(10, TimeUnit.MILLISECONDS);
        }
        for (int i = 0; i < 5; i++) {
            timer.record(2, TimeUnit.SECONDS);
        }
        HistogramSnapshot snap = timer.takeSnapshot();
        double mean = snap.mean(TimeUnit.MILLISECONDS);
        List<String> lines = new ArrayList<>();
        lines.add("요청 수 → %d".formatted(snap.count()));
        lines.add("평균 → %.0f ms".formatted(mean));
        double p99 = 0;
        for (ValueAtPercentile v : snap.percentileValues()) {
            double ms = v.value(TimeUnit.MILLISECONDS);
            lines.add("p%.0f → %.0f ms".formatted(v.percentile() * 100, ms));
            if (v.percentile() == 0.99) {
                p99 = ms;
            }
        }

        assertThat(mean).isBetween(100.0, 120.0);
        assertThat(p99).isGreaterThan(1500);
        CiOutput.record(LESSON, "percentile", """
                (95건은 10ms, 5건은 2초)
                %s""".formatted(String.join("\n", lines)));
    }

    @Test
    @DisplayName("JvmGcMetrics를 붙이고 GC를 일으키면 jvm.gc.pause 타이머가 쌓인다")
    void gcMetrics() throws InterruptedException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try (JvmGcMetrics gc = new JvmGcMetrics()) {
            gc.bindTo(registry);
            for (int round = 0; round < 3; round++) {
                List<byte[]> garbage = new ArrayList<>();
                for (int i = 0; i < 2000; i++) {
                    garbage.add(new byte[10_000]);
                }
                garbage.clear();
                System.gc();
            }
            long count = 0;
            for (int i = 0; i < 50 && count == 0; i++) {
                Thread.sleep(100);
                count = registry.find("jvm.gc.pause").timers().stream().mapToLong(Timer::count).sum();
            }
            List<String> lines = registry.find("jvm.gc.pause").timers().stream()
                    .map(t -> "jvm.gc.pause %s, cause=%s → %d번, 최대 %.1f ms".formatted(
                            t.getId().getTag("action"), t.getId().getTag("cause"), t.count(),
                            t.max(TimeUnit.MILLISECONDS)))
                    .toList();

            assertThat(count).isGreaterThan(0);
            CiOutput.record(LESSON, "gc", String.join("\n", lines));
        }
    }
}
