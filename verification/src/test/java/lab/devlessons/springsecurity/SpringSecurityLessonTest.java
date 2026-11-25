package lab.devlessons.springsecurity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 레슨 "인증과 인가"(S12)의 주장을 작은 웹 앱(MiniWeb)에 스프링 시큐리티를 직접 켜서 확인한다.
 * 저장소의 application.yml은 시큐리티 자동 설정을 끄므로, 여기서는 @EnableWebSecurity로 체인을 만든다.
 */
class SpringSecurityLessonTest {

    private static final String LESSON = "spring-security";
    static final AtomicInteger ORDERS_CALLS = new AtomicInteger();

    @RestController
    static class ShopController {
        @GetMapping("/products")
        String products() {
            return "상품 목록";
        }

        @GetMapping("/orders")
        String orders(@AuthenticationPrincipal UserDetails user) {
            ORDERS_CALLS.incrementAndGet();
            String fromHolder = SecurityContextHolder.getContext().getAuthentication().getName();
            return user.getUsername() + "의 주문 (SecurityContextHolder: " + fromHolder + ")";
        }

        @PostMapping("/orders")
        String create() {
            return "주문 생성";
        }

        @GetMapping("/admin/stats")
        String stats() {
            return "관리자 통계";
        }

        @GetMapping("/orders/async")
        String async() throws InterruptedException {
            AtomicReference<String> seen = new AtomicReference<>();
            Thread t = new Thread(() -> seen.set(String.valueOf(SecurityContextHolder.getContext().getAuthentication())));
            t.start();
            t.join();
            return "요청 스레드: " + SecurityContextHolder.getContext().getAuthentication().getName() + ", 새 스레드: " + seen.get();
        }
    }

    /**
     * 시큐리티 필터는 톰캣을 띄울 때 만들어져 요청 매핑을 일찍 읽는다. 그때 @RequestMapping의 ${...}를 풀 수 있게
     * 자리 표시자 설정을 켠다(보통 앱에서는 @SpringBootApplication이 켠다)
     */
    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(PropertyPlaceholderAutoConfiguration.class)
    static class Users {
        @Bean
        PasswordEncoder passwordEncoder() {
            return PasswordEncoderFactories.createDelegatingPasswordEncoder();
        }

        @Bean
        InMemoryUserDetailsManager users(PasswordEncoder encoder) {
            return new InMemoryUserDetailsManager(
                    User.withUsername("kim").password(encoder.encode("kim-pw")).roles("USER").build(),
                    User.withUsername("admin").password(encoder.encode("admin-pw")).roles("ADMIN").build());
        }
    }

    /** 레슨의 코드 predict: HTTP 기본 인증 */
    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class BasicSecurity {
        @Bean
        SecurityFilterChain api(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(a -> a
                            .requestMatchers("/products/**").permitAll()
                            .requestMatchers("/admin/**").hasRole("ADMIN")
                            .anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults())
                    .csrf(c -> c.disable());
            return http.build();
        }
    }

    /** 레슨의 코드 session: 폼 로그인과 세션 */
    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class SessionSecurity {
        @Bean
        SecurityFilterChain web(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(a -> a.anyRequest().authenticated())
                    .formLogin(f -> f.successHandler((req, res, auth) -> res.setStatus(200))
                            .failureHandler((req, res, e) -> res.setStatus(401)))
                    .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> res.sendError(401)))
                    .csrf(c -> c.ignoringRequestMatchers("/login"));
            return http.build();
        }
    }

    /** 레슨의 코드 tokenFilter: 요청마다 헤더의 토큰을 검증한다. 예제라 토큰은 "token-사용자이름" 모양이다 */
    static class TokenFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            String h = req.getHeader("Authorization");
            if (h != null && h.startsWith("Bearer token-")) {
                String name = h.substring("Bearer token-".length());
                User user = new User(name, "", AuthorityUtils.createAuthorityList("ROLE_USER"));
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
            }
            chain.doFilter(req, res);
        }
    }

    /** 레슨의 코드 stateless */
    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TokenSecurity {
        @Bean
        SecurityFilterChain api(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(a -> a.anyRequest().authenticated())
                    .csrf(c -> c.disable())
                    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> res.sendError(401)))
                    .addFilterBefore(new TokenFilter(), UsernamePasswordAuthenticationFilter.class);
            return http.build();
        }
    }

    private static String basic(String user, String pw) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + pw).getBytes(StandardCharsets.UTF_8));
    }

    private static int status(MiniWeb web, String path, String auth) {
        var b = web.request(path).GET();
        if (auth != null) {
            b.header("Authorization", auth);
        }
        return web.exchange(b).statusCode();
    }

    @Test
    @DisplayName("로그인 없이 /products는 200, /orders는 401, USER로 /orders는 200, USER로 /admin은 403, 비밀번호가 틀리면 401")
    void authenticationVersusAuthorization() {
        try (MiniWeb web = MiniWeb.start(ShopController.class, Users.class, BasicSecurity.class)) {
            int products = status(web, "/products", null);
            int orders = status(web, "/orders", null);
            int kimOrders = status(web, "/orders", basic("kim", "kim-pw"));
            int kimAdmin = status(web, "/admin/stats", basic("kim", "kim-pw"));
            int wrongPw = status(web, "/admin/stats", basic("admin", "wrong"));
            int admin = status(web, "/admin/stats", basic("admin", "admin-pw"));

            assertThat(List.of(products, orders, kimOrders, kimAdmin, wrongPw, admin)).containsExactly(200, 401, 200, 403, 401, 200);
            CiOutput.record(LESSON, "status", "로그인 없이 GET /products → " + products
                    + "\n로그인 없이 GET /orders → " + orders
                    + "\nkim(USER)로 GET /orders → " + kimOrders
                    + "\nkim(USER)로 GET /admin/stats → " + kimAdmin
                    + "\nadmin, 틀린 비밀번호로 GET /admin/stats → " + wrongPw
                    + "\nadmin(ADMIN)로 GET /admin/stats → " + admin);
        }
    }

    @Test
    @DisplayName("체인의 필터에 SecurityContextHolderFilter, BasicAuthenticationFilter, ExceptionTranslationFilter, AuthorizationFilter가 이 순서로 있다. 401 요청에 컨트롤러는 불리지 않는다")
    void filterChain() {
        try (MiniWeb web = MiniWeb.start(ShopController.class, Users.class, BasicSecurity.class)) {
            FilterChainProxy proxy = web.context().getBean(FilterChainProxy.class);
            List<String> names = proxy.getFilterChains().get(0).getFilters().stream().map(f -> f.getClass().getSimpleName()).toList();
            ORDERS_CALLS.set(0);
            int s = status(web, "/orders", null);

            assertThat(names.indexOf("SecurityContextHolderFilter")).isLessThan(names.indexOf("BasicAuthenticationFilter"));
            assertThat(names.indexOf("BasicAuthenticationFilter")).isLessThan(names.indexOf("ExceptionTranslationFilter"));
            assertThat(names.indexOf("ExceptionTranslationFilter")).isLessThan(names.indexOf("AuthorizationFilter"));
            assertThat(s).isEqualTo(401);
            assertThat(ORDERS_CALLS.get()).isZero();
            CiOutput.record(LESSON, "filters", names.stream().collect(Collectors.joining("\n"))
                    + "\n\n로그인 없이 GET /orders → " + s + ", 컨트롤러 실행 " + ORDERS_CALLS.get() + "번");
        }
    }

    @Test
    @DisplayName("컨트롤러의 @AuthenticationPrincipal과 SecurityContextHolder가 같은 사용자를 보고, 새 스레드에서는 비어 있다")
    void securityContextHolder() {
        try (MiniWeb web = MiniWeb.start(ShopController.class, Users.class, BasicSecurity.class)) {
            String body = web.exchange(web.request("/orders").header("Authorization", basic("kim", "kim-pw")).GET()).body();
            String async = web.exchange(web.request("/orders/async").header("Authorization", basic("kim", "kim-pw")).GET()).body();

            assertThat(body).isEqualTo("kim의 주문 (SecurityContextHolder: kim)");
            assertThat(async).endsWith("새 스레드: null");
            CiOutput.record(LESSON, "holder", "GET /orders (kim) → " + body + "\nGET /orders/async (kim) → " + async);
        }
    }

    @Test
    @DisplayName("세션 방식: 로그인 뒤 쿠키만으로 200, 쿠키 없으면 401, 로그인 전후 세션 id가 바뀐다. CSRF 토큰 없는 POST는 403")
    void sessionLogin() throws Exception {
        try (MiniWeb web = MiniWeb.start(ShopController.class, Users.class, SessionSecurity.class)) {
            CookieManager cookies = new CookieManager();
            HttpClient browser = HttpClient.newBuilder().cookieHandler(cookies).build();
            String base = "http://localhost:" + web.port();

            browser.send(HttpRequest.newBuilder(URI.create(base + "/orders")).GET().build(), HttpResponse.BodyHandlers.ofString());
            String before = cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("JSESSIONID"))
                    .map(c -> c.getValue()).findFirst().orElse("(없음)");
            HttpResponse<String> login = browser.send(HttpRequest.newBuilder(URI.create(base + "/login"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("username=kim&password=kim-pw")).build(), HttpResponse.BodyHandlers.ofString());
            String after = cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("JSESSIONID"))
                    .map(c -> c.getValue()).findFirst().orElse("(없음)");
            HttpResponse<String> withCookie = browser.send(HttpRequest.newBuilder(URI.create(base + "/orders")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> post = browser.send(HttpRequest.newBuilder(URI.create(base + "/orders"))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            int noCookie = status(web, "/orders", null);

            assertThat(login.statusCode()).isEqualTo(200);
            assertThat(withCookie.statusCode()).isEqualTo(200);
            assertThat(noCookie).isEqualTo(401);
            assertThat(after).isNotEqualTo(before);
            assertThat(post.statusCode()).isEqualTo(403);
            CiOutput.record(LESSON, "session", "POST /login (kim) → " + login.statusCode()
                    + "\n로그인 전후 JSESSIONID가 바뀜? " + !after.equals(before)
                    + "\n쿠키만 보내고 GET /orders → " + withCookie.statusCode() + " " + withCookie.body()
                    + "\n쿠키 없이 GET /orders → " + noCookie
                    + "\n쿠키는 있고 CSRF 토큰 없이 POST /orders → " + post.statusCode());
        }
    }

    @Test
    @DisplayName("토큰 방식: 토큰이 맞으면 200, 없거나 틀리면 401, 응답에 JSESSIONID 쿠키가 없다")
    void tokenStateless() {
        try (MiniWeb web = MiniWeb.start(ShopController.class, Users.class, TokenSecurity.class)) {
            HttpResponse<String> ok = web.exchange(web.request("/orders").header("Authorization", "Bearer token-kim").GET());
            int none = status(web, "/orders", null);
            int wrong = status(web, "/orders", "Bearer nope");
            HttpResponse<String> post = web.exchange(web.request("/orders").header("Authorization", "Bearer token-kim")
                    .POST(HttpRequest.BodyPublishers.noBody()));

            assertThat(ok.statusCode()).isEqualTo(200);
            assertThat(none).isEqualTo(401);
            assertThat(wrong).isEqualTo(401);
            assertThat(ok.headers().firstValue("Set-Cookie")).isEmpty();
            assertThat(post.statusCode()).isEqualTo(200);
            CiOutput.record(LESSON, "token", "Authorization: Bearer token-kim로 GET /orders → " + ok.statusCode() + " " + ok.body()
                    + "\nSet-Cookie 헤더: " + ok.headers().firstValue("Set-Cookie").orElse("없음")
                    + "\n토큰 없이 → " + none + "\n틀린 토큰 → " + wrong
                    + "\n토큰으로 POST /orders (CSRF 끔) → " + post.statusCode());
        }
    }

    @Test
    @DisplayName("같은 비밀번호를 두 번 인코딩하면 다르고 둘 다 matches가 true이며 {bcrypt}로 시작한다")
    void passwordEncoder() {
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        String a = encoder.encode("kim-pw");
        String b = encoder.encode("kim-pw");

        assertThat(a).isNotEqualTo(b).startsWith("{bcrypt}");
        assertThat(encoder.matches("kim-pw", a)).isTrue();
        assertThat(encoder.matches("kim-pw", b)).isTrue();
        assertThat(encoder.matches("wrong", a)).isFalse();
        CiOutput.record(LESSON, "bcrypt", "encode(\"kim-pw\") 첫째: " + a + "\nencode(\"kim-pw\") 둘째: " + b
                + "\nmatches(\"kim-pw\", 첫째) → true\nmatches(\"kim-pw\", 둘째) → true\nmatches(\"wrong\", 첫째) → false");
    }
}
