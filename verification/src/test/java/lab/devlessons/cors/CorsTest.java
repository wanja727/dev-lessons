package lab.devlessons.cors;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 레슨 "CORS"의 주장을 검증한다. 브라우저는 테스트에서 돌릴 수 없어서, 브라우저가 붙이는 헤더(Origin, 사전 요청의 헤더)를
 * 자바 HttpClient로 붙여 보내고 스프링 서버의 응답 헤더를 본다. 브라우저가 그 헤더로 하는 일은 Fetch 명세가 근거다.
 */
class CorsTest {

    static final String LESSON = "cors";
    static final AtomicInteger ORDERS = new AtomicInteger();
    final HttpClient http = HttpClient.newHttpClient();

    @RestController
    static class ShopApi {
        @GetMapping("/products")
        List<Map<String, Object>> products() {
            return List.of(Map.of("id", 7, "name", "커피"));
        }

        @PutMapping("/products/{id}")
        Map<String, Object> put(@PathVariable long id, @RequestBody Map<String, Object> body) {
            return Map.of("id", id, "name", body.get("name"));
        }

        @PostMapping("/orders")
        String order() {
            return "주문 " + ORDERS.incrementAndGet() + "번 생성";
        }
    }

    /** 레슨의 코드 config와 같다 */
    @Configuration(proxyBeanMethods = false)
    static class CorsConfig implements WebMvcConfigurer {
        @Override
        public void addCorsMappings(CorsRegistry registry) {
            registry.addMapping("/**")
                    .allowedOrigins("https://shop.com")
                    .allowedMethods("GET", "POST", "PUT", "DELETE")
                    .allowCredentials(true)
                    .maxAge(1800);
        }
    }

    /** 흔한 실수: 모든 출처와 쿠키를 함께 허락 */
    @Configuration(proxyBeanMethods = false)
    static class WildcardConfig implements WebMvcConfigurer {
        @Override
        public void addCorsMappings(CorsRegistry registry) {
            registry.addMapping("/**").allowedOrigins("*").allowCredentials(true);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PatternConfig implements WebMvcConfigurer {
        @Override
        public void addCorsMappings(CorsRegistry registry) {
            registry.addMapping("/**").allowedOriginPatterns("https://*.shop.com").allowCredentials(true);
        }
    }

    HttpResponse<String> send(MiniWeb web, String method, String path, Map<String, String> headers, String body)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + web.port() + path));
        headers.forEach(b::header);
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    static String corsHeaders(HttpResponse<String> res) {
        StringBuilder sb = new StringBuilder("HTTP " + res.statusCode());
        res.headers().map().forEach((k, v) -> {
            if (k.toLowerCase().startsWith("access-control-") || k.equalsIgnoreCase("vary")) {
                sb.append("\n").append(k).append(": ").append(String.join(", ", v));
            }
        });
        return sb.toString();
    }

    @Test
    @DisplayName("CORS 설정이 없으면 Origin이 붙은 GET에 200과 본문을 주고 Allow-Origin은 없다. 설정하면 있다")
    void allowOriginHeader() throws Exception {
        HttpResponse<String> plain;
        HttpResponse<String> configured;
        try (MiniWeb web = MiniWeb.start(ShopApi.class)) {
            plain = send(web, "GET", "/products", Map.of("Origin", "https://shop.com"), null);
        }
        try (MiniWeb web = MiniWeb.start(ShopApi.class, CorsConfig.class)) {
            configured = send(web, "GET", "/products", Map.of("Origin", "https://shop.com"), null);
        }

        assertThat(plain.statusCode()).isEqualTo(200);
        assertThat(plain.body()).contains("커피");
        assertThat(plain.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
        assertThat(configured.headers().firstValue("Access-Control-Allow-Origin")).contains("https://shop.com");
        CiOutput.record(LESSON, "allow-origin", "[CORS 설정 없음]\n" + corsHeaders(plain) + "\n본문: " + plain.body()
                + "\n\n[shop.com을 허락한 설정]\n" + corsHeaders(configured) + "\n본문: " + configured.body());
    }

    @Test
    @DisplayName("CORS 설정이 없는 서버는 다른 출처의 단순 POST(폼)를 처리한다. 설정한 경로에서는 스프링이 허락하지 않은 출처를 403으로 거절한다")
    void simplePostIsProcessed() throws Exception {
        Map<String, String> evilForm = Map.of("Origin", "https://evil.com",
                "Content-Type", "application/x-www-form-urlencoded");
        HttpResponse<String> plain;
        int afterPlain;
        HttpResponse<String> configured;
        int afterConfigured;
        ORDERS.set(0);
        try (MiniWeb web = MiniWeb.start(ShopApi.class)) {
            plain = send(web, "POST", "/orders", evilForm, "productId=7");
            afterPlain = ORDERS.get();
        }
        try (MiniWeb web = MiniWeb.start(ShopApi.class, CorsConfig.class)) {
            configured = send(web, "POST", "/orders", evilForm, "productId=7");
            afterConfigured = ORDERS.get();
        }

        assertThat(plain.statusCode()).isEqualTo(200);
        assertThat(afterPlain).isEqualTo(1);
        assertThat(configured.statusCode()).isEqualTo(403);
        assertThat(afterConfigured).isEqualTo(1);
        CiOutput.record(LESSON, "simple-post", "[CORS 설정 없음] Origin: https://evil.com, 폼 POST /orders\n"
                + corsHeaders(plain) + "\n본문: " + plain.body() + "\n주문 수: " + afterPlain
                + "\n\n[shop.com만 허락한 설정] 같은 요청\n" + corsHeaders(configured) + "\n본문: " + configured.body()
                + "\n주문 수: " + afterConfigured + " (늘지 않음)");
    }

    @Test
    @DisplayName("허락한 출처의 사전 요청에는 200과 Allow-Methods, Allow-Headers, Max-Age가, 허락하지 않은 출처에는 403이 온다")
    void preflight() throws Exception {
        Map<String, String> fromShop = Map.of("Origin", "https://shop.com",
                "Access-Control-Request-Method", "PUT", "Access-Control-Request-Headers", "content-type");
        Map<String, String> fromEvil = Map.of("Origin", "https://evil.com",
                "Access-Control-Request-Method", "PUT", "Access-Control-Request-Headers", "content-type");
        HttpResponse<String> ok;
        HttpResponse<String> rejected;
        HttpResponse<String> noConfig;
        try (MiniWeb web = MiniWeb.start(ShopApi.class, CorsConfig.class)) {
            ok = send(web, "OPTIONS", "/products/7", fromShop, null);
            rejected = send(web, "OPTIONS", "/products/7", fromEvil, null);
        }
        try (MiniWeb web = MiniWeb.start(ShopApi.class)) {
            noConfig = send(web, "OPTIONS", "/products/7", fromShop, null);
        }

        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(ok.headers().firstValue("Access-Control-Allow-Methods").orElse("")).contains("PUT");
        assertThat(ok.headers().firstValue("Access-Control-Max-Age")).contains("1800");
        assertThat(rejected.statusCode()).isEqualTo(403);
        assertThat(rejected.body()).contains("Invalid CORS request");
        assertThat(noConfig.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
        CiOutput.record(LESSON, "preflight", "[Origin: https://shop.com] OPTIONS /products/7\n" + corsHeaders(ok)
                + "\n\n[Origin: https://evil.com] 같은 사전 요청\n" + corsHeaders(rejected) + "\n본문: " + rejected.body()
                + "\n\n[CORS 설정이 없는 서버] 같은 사전 요청\n" + corsHeaders(noConfig));
    }

    @Test
    @DisplayName("allowedOrigins(\"*\")와 allowCredentials(true)를 함께 두면 요청을 처리하다 예외가 나고, allowedOriginPatterns는 출처를 그대로 돌려준다")
    void credentialsNeedExactOrigin() throws Exception {
        Map<String, String> fromAdmin = Map.of("Origin", "https://admin.shop.com");
        HttpResponse<String> wildcard;
        HttpResponse<String> pattern;
        try (MiniWeb web = MiniWeb.start(ShopApi.class, WildcardConfig.class)) {
            wildcard = send(web, "GET", "/products", fromAdmin, null);
        }
        try (MiniWeb web = MiniWeb.start(ShopApi.class, PatternConfig.class)) {
            pattern = send(web, "GET", "/products", fromAdmin, null);
        }

        assertThat(wildcard.statusCode()).isEqualTo(500);
        assertThat(wildcard.body()).contains("allowCredentials");
        assertThat(pattern.headers().firstValue("Access-Control-Allow-Origin")).contains("https://admin.shop.com");
        assertThat(pattern.headers().firstValue("Access-Control-Allow-Credentials")).contains("true");
        CiOutput.record(LESSON, "credentials", "[allowedOrigins(\"*\") + allowCredentials(true)]\n"
                + corsHeaders(wildcard) + "\n원인: " + wildcard.body()
                        .replaceAll("(?s).*Root Cause</b></p><pre>([^\\n]*).*", "$1").replace("&quot;", "\"")
                + "\n\n[allowedOriginPatterns(\"https://*.shop.com\") + allowCredentials(true)]\n" + corsHeaders(pattern));
    }
}
