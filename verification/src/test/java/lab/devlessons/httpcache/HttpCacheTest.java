package lab.devlessons.httpcache;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * 레슨 "HTTP 캐시"의 주장을 검증한다. 브라우저와 CDN이 응답을 저장하는 모습은 테스트로 돌릴 수 없어서,
 * 스프링 서버가 보내는 캐시 헤더와 조건부 요청의 응답을 자바 HttpClient(캐시가 없다)로 본다.
 */
class HttpCacheTest {

    static final String LESSON = "http-cache";
    static volatile int version = 3;
    static final Instant UPDATED = Instant.parse("2026-11-25T09:00:00Z");
    static final AtomicInteger CALLS = new AtomicInteger();
    final HttpClient http = HttpClient.newHttpClient();

    @RestController
    static class ProductApi {
        @GetMapping("/products")
        ResponseEntity<List<String>> list() {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)))
                    .body(List.of("커피", "라떼"));
        }

        @GetMapping("/products/7")
        ResponseEntity<Map<String, Object>> one(WebRequest request) {
            String etag = "\"v" + version + "\"";
            if (request.checkNotModified(etag)) {
                return null; // 스프링이 304를 만든다
            }
            return ResponseEntity.ok().eTag(etag).body(Map.of("id", 7, "name", "커피", "version", version));
        }

        @GetMapping("/notices/1")
        ResponseEntity<String> notice(WebRequest request) {
            if (request.checkNotModified(UPDATED.toEpochMilli())) {
                return null;
            }
            return ResponseEntity.ok().lastModified(UPDATED).body("12월 배송 안내");
        }

        /** 흔한 실수: 사용자마다 다른 응답을 공유 캐시가 저장하게 둔다 */
        @GetMapping("/me")
        ResponseEntity<String> me() {
            return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
                    .body("kim의 정보");
        }

        @GetMapping("/me/fixed")
        ResponseEntity<String> meFixed() {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body("kim의 정보");
        }
    }

    @RestController
    static class MenuApi {
        @GetMapping("/menu")
        String menu() {
            CALLS.incrementAndGet();
            return "커피 4500원, 라떼 5000원";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class EtagFilterConfig {
        @Bean
        FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
            return new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        }
    }

    HttpResponse<String> get(MiniWeb web, String path, String... headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + web.port() + path));
        for (int i = 0; i < headers.length; i += 2) {
            b.header(headers[i], headers[i + 1]);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    static String show(String request, HttpResponse<String> res) {
        StringBuilder sb = new StringBuilder(request).append("\n→ ").append(res.statusCode());
        for (String h : List.of("Cache-Control", "ETag", "Last-Modified")) {
            res.headers().firstValue(h).ifPresent(v -> sb.append("\n  ").append(h).append(": ").append(v));
        }
        sb.append("\n  본문: ").append(res.body().isEmpty() ? "(없음)" : res.body());
        return sb.toString();
    }

    @Test
    @DisplayName("CacheControl.maxAge(60초)를 주면 Cache-Control: max-age=60이 붙는다")
    void maxAge() throws Exception {
        try (MiniWeb web = MiniWeb.start(ProductApi.class)) {
            HttpResponse<String> res = get(web, "/products");

            assertThat(res.headers().firstValue("Cache-Control")).contains("max-age=60");
            CiOutput.record(LESSON, "max-age", show("GET /products", res));
        }
    }

    @Test
    @DisplayName("ETag가 같은 If-None-Match에는 304와 빈 본문, 상품이 바뀐 뒤에는 200과 새 ETag")
    void etagRevalidation() throws Exception {
        version = 3;
        try (MiniWeb web = MiniWeb.start(ProductApi.class)) {
            HttpResponse<String> first = get(web, "/products/7");
            String etag = first.headers().firstValue("ETag").orElseThrow();
            HttpResponse<String> same = get(web, "/products/7", "If-None-Match", etag);
            version = 4;
            HttpResponse<String> changed = get(web, "/products/7", "If-None-Match", etag);

            assertThat(first.statusCode()).isEqualTo(200);
            assertThat(same.statusCode()).isEqualTo(304);
            assertThat(same.body()).isEmpty();
            assertThat(changed.statusCode()).isEqualTo(200);
            assertThat(changed.headers().firstValue("ETag")).contains("\"v4\"");
            CiOutput.record(LESSON, "etag", String.join("\n\n",
                    show("GET /products/7", first),
                    show("GET /products/7\nIf-None-Match: " + etag + "  (그대로)", same),
                    show("GET /products/7\nIf-None-Match: " + etag + "  (상품이 바뀐 뒤)", changed)));
        } finally {
            version = 3;
        }
    }

    @Test
    @DisplayName("If-Modified-Since가 Last-Modified와 같으면 304, 그보다 앞이면 200")
    void lastModified() throws Exception {
        DateTimeFormatter http1 = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC);
        try (MiniWeb web = MiniWeb.start(ProductApi.class)) {
            HttpResponse<String> first = get(web, "/notices/1");
            String lm = first.headers().firstValue("Last-Modified").orElseThrow();
            HttpResponse<String> same = get(web, "/notices/1", "If-Modified-Since", lm);
            HttpResponse<String> older = get(web, "/notices/1", "If-Modified-Since",
                    http1.format(UPDATED.minusSeconds(3600)));

            assertThat(same.statusCode()).isEqualTo(304);
            assertThat(older.statusCode()).isEqualTo(200);
            CiOutput.record(LESSON, "last-modified", String.join("\n\n",
                    show("GET /notices/1", first),
                    show("GET /notices/1\nIf-Modified-Since: " + lm, same),
                    show("GET /notices/1\nIf-Modified-Since: (한 시간 전)", older)));
        }
    }

    @Test
    @DisplayName("ShallowEtagHeaderFilter는 304를 주면서도 컨트롤러를 매번 실행한다")
    void shallowEtagStillRunsController() throws Exception {
        CALLS.set(0);
        try (MiniWeb web = MiniWeb.start(MenuApi.class, EtagFilterConfig.class)) {
            HttpResponse<String> first = get(web, "/menu");
            String etag = first.headers().firstValue("ETag").orElseThrow();
            HttpResponse<String> second = get(web, "/menu", "If-None-Match", etag);

            assertThat(second.statusCode()).isEqualTo(304);
            assertThat(CALLS.get()).isEqualTo(2);
            CiOutput.record(LESSON, "shallow", show("GET /menu", first) + "\n\n"
                    + show("GET /menu\nIf-None-Match: " + etag, second)
                    + "\n\n컨트롤러가 불린 횟수: " + CALLS.get());
        }
    }

    @Test
    @DisplayName("사용자의 정보에 public, max-age를 주면 공유 캐시가 저장할 수 있고, no-store는 어디에도 저장하지 않게 한다")
    void privateResponses() throws Exception {
        try (MiniWeb web = MiniWeb.start(ProductApi.class)) {
            HttpResponse<String> mistake = get(web, "/me");
            HttpResponse<String> fixed = get(web, "/me/fixed");

            assertThat(mistake.headers().firstValue("Cache-Control").orElse("")).contains("public");
            assertThat(fixed.headers().firstValue("Cache-Control")).contains("no-store");
            CiOutput.record(LESSON, "private", show("GET /me (흔한 실수)", mistake) + "\n\n"
                    + show("GET /me/fixed (고친 코드)", fixed));
        }
    }
}
