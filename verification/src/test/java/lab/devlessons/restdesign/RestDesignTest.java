package lab.devlessons.restdesign;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 레슨 "REST API 설계"(N5)의 주장을 검증한다. 상품 API 하나를 스프링 부트 3.5로 띄워 같은 요청을 두 번씩 보낸다.
 * 예전 레슨 rest-api의 테스트(rest/RestApiTest)는 페이지네이션(D8)의 재료라 그대로 둔다.
 */
class RestDesignTest {

    static final String LESSON = "rest-api";
    static final Map<Long, Product> STORE = new ConcurrentHashMap<>();
    static final AtomicLong SEQ = new AtomicLong();

    static MiniWeb web;
    static MiniWeb withProblemDetails;
    final HttpClient http = HttpClient.newHttpClient();

    record NewProduct(String name, int price) {
    }

    record Product(long id, String name, int price) {
    }

    static class ProductNotFound extends RuntimeException {
        final long id;

        ProductNotFound(long id) {
            super("상품을 찾을 수 없어요: " + id);
            this.id = id;
        }
    }

    @RestController
    static class ProductController {
        @PostMapping("/products")
        ResponseEntity<Product> create(@RequestBody NewProduct body) {
            Product p = new Product(SEQ.incrementAndGet(), body.name(), body.price());
            STORE.put(p.id(), p);
            return ResponseEntity.created(URI.create("/products/" + p.id())).body(p);
        }

        @GetMapping("/products/{id}")
        Product get(@PathVariable long id) {
            Product p = STORE.get(id);
            if (p == null) {
                throw new ProductNotFound(id);
            }
            return p;
        }

        @PutMapping("/products/{id}")
        ResponseEntity<Product> put(@PathVariable long id, @RequestBody NewProduct body) {
            Product p = new Product(id, body.name(), body.price());
            Product before = STORE.put(id, p);
            return before == null ? ResponseEntity.created(URI.create("/products/" + id)).body(p)
                    : ResponseEntity.ok(p);
        }

        @DeleteMapping("/products/{id}")
        ResponseEntity<Void> delete(@PathVariable long id) {
            if (STORE.remove(id) == null) {
                throw new ProductNotFound(id);
            }
            return ResponseEntity.noContent().build();
        }

        @ExceptionHandler
        ProblemDetail notFound(ProductNotFound e) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
            pd.setProperty("productId", e.id);
            return pd;
        }

        /** 흔한 실수: 실패를 200에 담는다 */
        @GetMapping("/v0/products/{id}")
        Map<String, Object> oldGet(@PathVariable long id) {
            Product p = STORE.get(id);
            if (p == null) {
                return Map.of("success", false, "message", "상품이 없습니다");
            }
            return Map.of("success", true, "data", p);
        }
    }

    @BeforeAll
    static void start() {
        web = MiniWeb.start(ProductController.class);
        withProblemDetails = MiniWeb.startWith(new String[] {"spring.mvc.problemdetails.enabled=true"},
                ProductController.class);
    }

    @AfterAll
    static void stop() {
        web.close();
        withProblemDetails.close();
    }

    @BeforeEach
    void reset() {
        STORE.clear();
        SEQ.set(0);
    }

    HttpResponse<String> send(MiniWeb app, String method, String path, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + path));
        if (json == null) {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json));
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> send(String method, String path, String json) throws Exception {
        return send(web, method, path, json);
    }

    static String show(String request, HttpResponse<String> res) {
        StringBuilder sb = new StringBuilder(request).append("\n→ ").append(res.statusCode());
        for (String h : List.of("Location", "Allow", "Content-Type")) {
            res.headers().firstValue(h).ifPresent(v -> sb.append("\n  ").append(h).append(": ").append(v));
        }
        if (!res.body().isEmpty()) {
            sb.append("\n  ").append(res.body());
        }
        return sb.toString();
    }

    @Test
    @DisplayName("같은 POST를 두 번 보내면 상품이 2개, 같은 PUT을 두 번 보내면 1개이고 두 PUT의 본문이 같다")
    void postIsNotIdempotentPutIs() throws Exception {
        String body = "{\"name\":\"커피\",\"price\":4500}";
        HttpResponse<String> p1 = send("POST", "/products", body);
        HttpResponse<String> p2 = send("POST", "/products", body);
        int afterPost = STORE.size();
        reset();
        HttpResponse<String> u1 = send("PUT", "/products/7", body);
        HttpResponse<String> u2 = send("PUT", "/products/7", body);
        int afterPut = STORE.size();

        assertThat(afterPost).isEqualTo(2);
        assertThat(afterPut).isEqualTo(1);
        assertThat(u1.body()).isEqualTo(u2.body());
        CiOutput.record(LESSON, "post-put", String.join("\n",
                show("POST /products (첫 번째)", p1), show("POST /products (두 번째, 같은 본문)", p2),
                "서버의 상품 수: " + afterPost, "",
                show("PUT /products/7 (첫 번째)", u1), show("PUT /products/7 (두 번째, 같은 본문)", u2),
                "서버의 상품 수: " + afterPut));
    }

    @Test
    @DisplayName("DELETE를 두 번 보내면 204와 404이고, 두 번 뒤에도 상품이 없다")
    void deleteTwice() throws Exception {
        send("PUT", "/products/7", "{\"name\":\"커피\",\"price\":4500}");
        HttpResponse<String> d1 = send("DELETE", "/products/7", null);
        HttpResponse<String> d2 = send("DELETE", "/products/7", null);

        assertThat(d1.statusCode()).isEqualTo(204);
        assertThat(d2.statusCode()).isEqualTo(404);
        assertThat(STORE).isEmpty();
        CiOutput.record(LESSON, "delete", String.join("\n",
                "DELETE /products/7 (첫 번째) → " + d1.statusCode(),
                "DELETE /products/7 (두 번째) → " + d2.statusCode(),
                "두 번 뒤 7번 상품: " + (STORE.containsKey(7L) ? "있음" : "없음")));
    }

    @Test
    @DisplayName("자원에 없는 메서드를 보내면 405와 Allow 헤더가 온다")
    void methodNotAllowed() throws Exception {
        HttpResponse<String> res = send("POST", "/products/1", "{}");

        assertThat(res.statusCode()).isEqualTo(405);
        assertThat(res.headers().firstValue("Allow").orElse("")).contains("GET", "PUT", "DELETE");
        CiOutput.record(LESSON, "405", "POST /products/1 → " + res.statusCode()
                + "\nAllow: " + res.headers().firstValue("Allow").orElse(""));
    }

    @Test
    @DisplayName("ExceptionHandler가 ProblemDetail을 돌려주면 404와 application/problem+json, 더한 칸이 온다")
    void problemDetail() throws Exception {
        HttpResponse<String> res = send("GET", "/products/99", null);

        assertThat(res.statusCode()).isEqualTo(404);
        assertThat(res.headers().firstValue("Content-Type").orElse("")).isEqualTo("application/problem+json");
        assertThat(res.body()).contains("\"productId\":99").contains("\"status\":404");
        CiOutput.record(LESSON, "problem-404", show("GET /products/99", res));
    }

    @Test
    @DisplayName("실패를 200에 담은 응답과 404 Problem Details를 비교한다")
    void okButFailedVsProblem() throws Exception {
        HttpResponse<String> old = send("GET", "/v0/products/99", null);
        HttpResponse<String> fixed = send("GET", "/products/99", null);

        assertThat(old.statusCode()).isEqualTo(200);
        assertThat(fixed.statusCode()).isEqualTo(404);
        CiOutput.record(LESSON, "mistake", show("GET /v0/products/99 (실패를 200에 담음)", old)
                + "\n\n" + show("GET /products/99 (고친 코드)", fixed));
    }

    @Test
    @DisplayName("spring.mvc.problemdetails.enabled를 켜면 스프링이 직접 내는 405도 Problem Details로 나온다")
    void frameworkErrorsAsProblemDetails() throws Exception {
        HttpResponse<String> plain = send(web, "POST", "/products/1", "{}");
        HttpResponse<String> enabled = send(withProblemDetails, "POST", "/products/1", "{}");

        assertThat(enabled.headers().firstValue("Content-Type").orElse("")).isEqualTo("application/problem+json");
        assertThat(plain.headers().firstValue("Content-Type").orElse("")).doesNotContain("problem");
        CiOutput.record(LESSON, "problem-enabled", show("[기본] POST /products/1", plain)
                + "\n\n" + show("[problemdetails.enabled=true] POST /products/1", enabled));
    }
}
