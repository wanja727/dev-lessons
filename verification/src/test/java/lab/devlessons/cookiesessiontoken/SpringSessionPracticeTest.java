package lab.devlessons.cookiesessiontoken;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

/**
 * 레슨 "쿠키, 세션, 토큰"의 "실무에서는" 구역을 검증한다.
 * 스프링 MVC 컨트롤러가 HttpSession을 쓰는 코드를 스프링 부트의 내장 톰캣으로 띄우고, 자바 HttpClient가 브라우저 역할을 한다.
 * 웹에 필요한 자동 설정만 켠다. DB와 배치 설정은 켜지 않는다.
 */
class SpringSessionPracticeTest {

    private static final String LESSON = "cookie-session-token";

    private static ConfigurableApplicationContext app;
    private static int port;
    private final HttpClient http = HttpClient.newHttpClient();

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
            ServletWebServerFactoryAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class,
    })
    @Import(ShopController.class)
    static class ShopApp {
    }

    /** 레슨에 실은 코드와 같다 */
    @RestController
    static class ShopController {

        @PostMapping("/login")
        String login(@RequestParam String user, HttpServletRequest request) {
            HttpSession session = request.getSession();
            session.setAttribute("user", user);
            return user + " 로그인";
        }

        @GetMapping("/me")
        ResponseEntity<String> me(@SessionAttribute(name = "user", required = false) String user) {
            if (user == null) {
                return ResponseEntity.status(401).body("로그인 필요");
            }
            return ResponseEntity.ok(user + "의 정보");
        }

        @PostMapping("/logout")
        String logout(HttpSession session) {
            session.invalidate();
            return "로그아웃";
        }

        /** 흔한 실수: 로그인했는지 보려고 HttpSession을 파라미터로 받는다 */
        @GetMapping("/cart/count")
        String cartCount(HttpSession session) {
            Object user = session.getAttribute("user");
            return user == null ? "0" : "3";
        }

        /** 고친 코드: 세션이 있을 때만 읽는다 */
        @GetMapping("/cart/count-fixed")
        String cartCountFixed(@SessionAttribute(name = "user", required = false) String user) {
            return user == null ? "0" : "3";
        }
    }

    @BeforeAll
    static void start() {
        app = new SpringApplicationBuilder(ShopApp.class)
                .properties("server.port=0", "spring.main.banner-mode=off")
                .run();
        port = ((ServletWebServerApplicationContext) app).getWebServer().getPort();
    }

    @AfterAll
    static void stop() {
        app.close();
    }

    record Reply(int status, String body, List<String> setCookie) {
        String line() {
            return status + " " + body;
        }
    }

    private Reply send(String method, String path, String... headers) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        for (int i = 0; i < headers.length; i += 2) {
            req.header(headers[i], headers[i + 1]);
        }
        HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString(UTF_8));
        return new Reply(res.statusCode(), res.body(), res.headers().allValues("set-cookie"));
    }

    private static String pair(String setCookie) {
        return setCookie.split(";")[0];
    }

    @Test
    @DisplayName("스프링 컨트롤러가 세션에 값을 넣으면 JSESSIONID 쿠키가 나가고, invalidate 뒤에는 같은 쿠키가 401을 받는다")
    void controllerSessionFlow() throws Exception {
        Reply login = send("POST", "/login?user=kim");
        assertThat(login.status()).isEqualTo(200);
        assertThat(login.setCookie()).hasSize(1);
        String cookie = pair(login.setCookie().get(0));
        assertThat(cookie).startsWith("JSESSIONID=").doesNotContain("kim");

        Reply me = send("GET", "/me", "Cookie", cookie);
        assertThat(me.line()).isEqualTo("200 kim의 정보");

        Reply logout = send("POST", "/logout", "Cookie", cookie);
        assertThat(logout.status()).isEqualTo(200);

        Reply after = send("GET", "/me", "Cookie", cookie);
        assertThat(after.line()).isEqualTo("401 로그인 필요");
        // @SessionAttribute(required = false)는 세션을 새로 만들지 않는다
        assertThat(after.setCookie()).isEmpty();

        CiOutput.record(LESSON, "spring-session", String.join("\n",
                "[POST /login?user=kim 의 응답]",
                login.line(),
                "Set-Cookie: " + login.setCookie().get(0).replaceAll("JSESSIONID=[^;]+", "JSESSIONID=(32자리 값)"),
                "",
                "[받은 쿠키로 GET /me]",
                me.line(),
                "",
                "[POST /logout 뒤에 같은 쿠키로 GET /me]",
                after.line()));
    }

    @Test
    @DisplayName("스프링 부트의 세션 만료 시간 기본값은 30분이다")
    void sessionTimeoutDefaultsTo30Minutes() {
        ServerProperties server = app.getBean(ServerProperties.class);
        assertThat(server.getServlet().getSession().getTimeout()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("HttpSession을 파라미터로 받으면 로그인하지 않은 요청에도 세션이 만들어진다. @SessionAttribute는 만들지 않는다")
    void sessionParameterCreatesSessionForAnonymous() throws Exception {
        Reply mistake = send("GET", "/cart/count");
        assertThat(mistake.line()).isEqualTo("200 0");
        assertThat(mistake.setCookie()).hasSize(1);
        assertThat(mistake.setCookie().get(0)).startsWith("JSESSIONID=");

        Reply fixed = send("GET", "/cart/count-fixed");
        assertThat(fixed.line()).isEqualTo("200 0");
        assertThat(fixed.setCookie()).isEmpty();

        CiOutput.record(LESSON, "spring-anonymous", String.join("\n",
                "[HttpSession을 파라미터로 받는 코드]",
                mistake.line(),
                "Set-Cookie: " + mistake.setCookie().get(0).replaceAll("JSESSIONID=[^;]+", "JSESSIONID=(32자리 값)"),
                "",
                "[@SessionAttribute로 받는 코드]",
                fixed.line(),
                "Set-Cookie 헤더 없음"));
    }
}
