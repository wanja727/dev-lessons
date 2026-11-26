package lab.devlessons.webflux;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.http.codec.CodecsAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.embedded.EmbeddedWebServerFactoryCustomizerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.ReactiveWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.ReactorResourceFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.resources.LoopResources;

/**
 * 레슨 "웹플럭스"(S16)의 주장을 네티 위의 작은 리액티브 앱으로 확인한다.
 * 이벤트 루프 스레드는 2개로 줄인다(ReactorResourceFactory). 느린 외부 서버는 같은 앱의 /stock(1초, 논블로킹 지연)이다.
 */
class WebFluxLessonTest {

    private static final String LESSON = "webflux";
    static final Set<String> THREADS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    static volatile int port;
    static final AtomicInteger SUBSCRIBED = new AtomicInteger();

    public record Product(long id, int stock, String thread) {
    }

    @RestController
    static class ProductController {
        private final WebClient client = WebClient.create();

        /** 느린 외부 재고 서버 흉내: 스레드를 붙잡지 않고 1초 뒤에 답한다 */
        @GetMapping("/stock")
        Mono<Integer> stock() {
            return Mono.delay(Duration.ofSeconds(1)).thenReturn(7);
        }

        /** 레슨의 코드 predict */
        @GetMapping("/products")
        Mono<Product> product(@RequestParam long id) {
            String thread = Thread.currentThread().getName();
            THREADS.add(thread);
            return client.get().uri("http://localhost:" + port + "/stock").retrieve().bodyToMono(Integer.class)
                    .map(stock -> new Product(id, stock, thread));
        }

        /** 이벤트 루프에서 블로킹한다(잘못된 코드) */
        @GetMapping("/blocking")
        Mono<String> blocking() throws InterruptedException {
            Thread.sleep(1000);
            return Mono.just("done on " + Thread.currentThread().getName());
        }

        /** 블로킹을 boundedElastic으로 옮긴다 */
        @GetMapping("/offloaded")
        Mono<String> offloaded() {
            return Mono.fromCallable(() -> {
                Thread.sleep(1000);
                return "done on " + Thread.currentThread().getName();
            }).subscribeOn(Schedulers.boundedElastic());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({ReactiveWebServerFactoryAutoConfiguration.class, EmbeddedWebServerFactoryCustomizerAutoConfiguration.class,
            HttpHandlerAutoConfiguration.class, WebFluxAutoConfiguration.class, CodecsAutoConfiguration.class,
            JacksonAutoConfiguration.class, PropertyPlaceholderAutoConfiguration.class})
    @Import(ProductController.class)
    static class ReactiveApp {
        @Bean
        ReactorResourceFactory reactorResourceFactory() {
            ReactorResourceFactory f = new ReactorResourceFactory();
            f.setUseGlobalResources(false);
            f.setLoopResources(LoopResources.create("reactor-http", 2, true));
            return f;
        }

        /**
         * 이 저장소에는 톰캣도 있어서, 그대로 두면 스프링 부트가 리액티브 앱을 톰캣 위에 띄운다(요청 스레드 http-nio-exec).
         * 네티 서버를 직접 고른다. 실제 웹플럭스 프로젝트는 톰캣이 없어서 네티가 기본이다
         */
        @Bean
        NettyReactiveWebServerFactory nettyReactiveWebServerFactory(ReactorResourceFactory resources) {
            NettyReactiveWebServerFactory f = new NettyReactiveWebServerFactory();
            f.setResourceFactory(resources);
            return f;
        }
    }

    /** 같은 일을 MVC로: 톰캣 스레드 2개, 블로킹 RestClient */
    @RestController
    static class MvcProductController {
        static volatile int stockPort;
        private final RestClient client = RestClient.create();

        @GetMapping("/products")
        Product product(@RequestParam long id) {
            Integer stock = client.get().uri("http://localhost:" + stockPort + "/stock").retrieve().body(Integer.class);
            return new Product(id, stock, Thread.currentThread().getName());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(EmbeddedWebServerFactoryCustomizerAutoConfiguration.class)
    static class TomcatSettings {
    }

    private static ConfigurableApplicationContext startReactive() {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(ReactiveApp.class)
                .web(WebApplicationType.REACTIVE)
                .properties("server.port=0", "spring.main.banner-mode=off", "spring.main.log-startup-info=false")
                .run();
        port = ((ReactiveWebServerApplicationContext) ctx).getWebServer().getPort();
        return ctx;
    }

    private static double fire(int port, String path, int n) throws Exception {
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        long start = System.nanoTime();
        List<CompletableFuture<HttpResponse<String>>> all = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            all.add(http.sendAsync(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path.replace("{i}", String.valueOf(i))))
                    .timeout(Duration.ofSeconds(60)).build(), HttpResponse.BodyHandlers.ofString()));
        }
        for (CompletableFuture<HttpResponse<String>> f : all) {
            assertThat(f.get(90, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        }
        return (System.nanoTime() - start) / 1e9;
    }

    @Test
    @DisplayName("이벤트 루프 2개로 1초 걸리는 외부 호출 요청 100개가 약 1초에 끝나고, 처리한 스레드는 reactor-http 이벤트 루프 둘이다")
    void manyRequestsFewThreads() throws Exception {
        THREADS.clear();
        try (var ctx = startReactive()) {
            fire(port, "/products?id={i}", 5); // 연결과 클래스를 미리 데운다
            THREADS.clear();
            double seconds = fire(port, "/products?id={i}", 100);

            assertThat(seconds).isLessThan(3.5);
            assertThat(THREADS).allMatch(t -> t.startsWith("reactor-http-"));
            // 리눅스(CI)는 네이티브 전송을 써서 reactor-http-epoll-N, 맥과 윈도는 reactor-http-nio-N
            assertThat(THREADS.size()).isLessThanOrEqualTo(2);
            CiOutput.record(LESSON, "many", String.format("이벤트 루프 스레드 2개, 외부 재고 서버 1초\n요청 100개를 동시에 보냄\n"
                    + "모두 끝난 시간: %.1f초\n요청을 처리한 스레드: %s", seconds, new TreeSet<>(THREADS)));
        }
    }

    @Test
    @DisplayName("같은 일을 MVC(톰캣 스레드 2개, 블로킹 RestClient)로 하면 요청 10개에 약 5초 걸린다")
    void mvcComparison() throws Exception {
        try (var reactive = startReactive();
                MiniWeb mvc = MiniWeb.startWith(new String[] {"server.tomcat.threads.max=2", "server.tomcat.threads.min-spare=2"},
                        MvcProductController.class, TomcatSettings.class)) {
            MvcProductController.stockPort = port;
            mvc.get("/products?id=0");
            double mvcSeconds = fire(mvc.port(), "/products?id={i}", 10);
            double fluxSeconds = fire(port, "/products?id={i}", 10);

            assertThat(mvcSeconds).isGreaterThan(4.0);
            assertThat(fluxSeconds).isLessThan(2.5);
            CiOutput.record(LESSON, "mvc", String.format("외부 재고 서버 1초, 요청 10개를 동시에 보냄\n"
                    + "MVC(톰캣 스레드 2개, RestClient): %.1f초\n웹플럭스(이벤트 루프 2개, WebClient): %.1f초", mvcSeconds, fluxSeconds));
        }
    }

    @Test
    @DisplayName("이벤트 루프에서 Thread.sleep을 하면 요청 10개에 약 5초, boundedElastic으로 옮기면 약 1초")
    void blockingOnEventLoop() throws Exception {
        try (var ctx = startReactive()) {
            fire(port, "/offloaded", 2);
            double blocking = fire(port, "/blocking", 10);
            double offloaded = fire(port, "/offloaded", 10);

            assertThat(blocking).isGreaterThan(4.0);
            assertThat(offloaded).isLessThan(2.5);
            CiOutput.record(LESSON, "blocking", String.format("이벤트 루프 스레드 2개, 요청 10개를 동시에 보냄\n"
                    + "컨트롤러에서 Thread.sleep(1000): %.1f초\nsubscribeOn(Schedulers.boundedElastic())으로 옮김: %.1f초", blocking, offloaded));
        }
    }

    @Test
    @DisplayName("Mono는 구독하기 전에는 실행되지 않는다")
    void monoIsLazy() {
        SUBSCRIBED.set(0);
        Mono<Integer> m = Mono.fromCallable(SUBSCRIBED::incrementAndGet);
        int before = SUBSCRIBED.get();
        Integer value = m.block();

        assertThat(before).isZero();
        assertThat(value).isEqualTo(1);
        CiOutput.record(LESSON, "lazy", "Mono를 만든 뒤 실행 횟수: " + before + "\nblock()으로 구독한 뒤 실행 횟수: " + SUBSCRIBED.get());
    }

    @Test
    @DisplayName("MVC와 웹플럭스 스타터가 함께 있으면 스프링 부트는 서블릿(MVC) 앱으로 띄운다")
    void mvcWinsWhenBothPresent() {
        WebApplicationType type = new SpringApplication(ReactiveApp.class).getWebApplicationType();

        assertThat(type).isEqualTo(WebApplicationType.SERVLET);
        CiOutput.record(LESSON, "both", "클래스패스에 spring-webmvc와 spring-webflux가 함께 있음\n스프링 부트가 고른 웹 앱 종류: " + type);
    }
}
