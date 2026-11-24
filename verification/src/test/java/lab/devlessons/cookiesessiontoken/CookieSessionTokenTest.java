package lab.devlessons.cookiesessiontoken;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lab.devlessons.support.CiOutput;
import org.apache.catalina.Context;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 레슨 "쿠키, 세션, 토큰"의 주장을 검증한다.
 * 스프링 없이 내장 톰캣에 서블릿 하나를 올려 서버를 띄우고, 자바 HttpClient가 브라우저 역할을 한다.
 * 세션은 톰캣의 HttpSession 그대로다. JWT는 라이브러리 없이 HMAC-SHA256으로 서명만 한다.
 * 서버를 여러 대 띄워 "다른 서버로 간 요청"을 확인한다.
 */
class CookieSessionTokenTest {

    private static final String LESSON = "cookie-session-token";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final byte[] KEY = "dev-lessons-signing-key-0123456789".getBytes(UTF_8);
    private static final byte[] OTHER_KEY = "another-signing-key-9876543210".getBytes(UTF_8);

    @TempDir Path tmp;

    private final HttpClient http = HttpClient.newHttpClient();
    private final List<Server> servers = new ArrayList<>();
    /** 서버들이 함께 보는 계정 DB. 이름 → 상태 */
    private final Map<String, String> accounts = new ConcurrentHashMap<>(Map.of("kim", "ACTIVE"));

    @AfterEach
    void stopServers() {
        servers.forEach(Server::stop);
    }

    // ── 서버 ──

    /** 서버 한 대. 세션은 이 서버의 메모리에만 있다 */
    class Server {
        private final Tomcat tomcat = new Tomcat();
        private final byte[] key;
        /** 계정을 정지할 때 지울 세션을 찾으려고 적어 둔다 */
        private final Map<String, HttpSession> sessionsByUser = new ConcurrentHashMap<>();
        final int port;

        Server(String name, byte[] key) throws Exception {
            this.key = key;
            Path dir = Files.createDirectories(tmp.resolve(name + "-" + servers.size()));
            tomcat.setBaseDir(dir.toString());
            tomcat.getEngine().setName(name + "-" + servers.size());
            tomcat.setPort(0);
            Context ctx = tomcat.addContext("", dir.toString());
            // 세션을 메모리에만 둔다. 스프링 부트의 기본값(server.servlet.session.persistent=false)과 같다
            StandardManager manager = new StandardManager();
            manager.setPathname(null);
            ctx.setManager(manager);
            Tomcat.addServlet(ctx, "app", new HttpServlet() {
                @Override
                protected void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
                    handle(req, res);
                }
            });
            ctx.addServletMappingDecoded("/*", "app");
            tomcat.getConnector();
            tomcat.start();
            port = tomcat.getConnector().getLocalPort();
            servers.add(this);
        }

        void stop() {
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (Exception ignored) {
                // 이미 멈춘 서버
            }
        }

        /** 계정을 정지한다. 세션 방식의 서버는 그 사용자의 세션도 지운다 */
        void suspend(String user) {
            accounts.put(user, "SUSPENDED");
            HttpSession session = sessionsByUser.remove(user);
            if (session != null) {
                session.invalidate();
            }
        }

        private void handle(HttpServletRequest req, HttpServletResponse res) throws IOException {
            switch (req.getRequestURI()) {
                // 쿠키
                case "/theme" -> {
                    res.addHeader("Set-Cookie", "theme=dark");
                    write(res, 200, "ok");
                }
                case "/echo" -> {
                    String theme = cookie(req, "theme");
                    write(res, 200, "서버가 읽은 theme: " + (theme == null ? "없음" : theme));
                }
                case "/role" -> {
                    res.addHeader("Set-Cookie", "role=USER");
                    write(res, 200, "ok");
                }
                // 쿠키의 role 값만 보고 판단하는 서버
                case "/admin" -> {
                    if ("ADMIN".equals(cookie(req, "role"))) {
                        write(res, 200, "관리자 화면");
                    } else {
                        write(res, 403, "권한 없음");
                    }
                }
                // 세션
                case "/session/login" -> {
                    String user = req.getParameter("user");
                    HttpSession session = req.getSession(true);
                    session.setAttribute("user", user);
                    sessionsByUser.put(user, session);
                    write(res, 200, "로그인: " + user);
                }
                case "/session/me" -> {
                    HttpSession session = req.getSession(false);
                    Object user = session == null ? null : session.getAttribute("user");
                    if (user == null) {
                        write(res, 401, "로그인하지 않은 요청");
                    } else {
                        write(res, 200, "kim의 정보".replace("kim", user.toString()));
                    }
                }
                case "/session/logout" -> {
                    HttpSession session = req.getSession(false);
                    if (session != null) {
                        session.invalidate();
                    }
                    write(res, 200, "로그아웃");
                }
                // 토큰
                case "/token/login" -> {
                    long exp = System.currentTimeMillis() / 1000 + 30 * 60;
                    write(res, 200, issue(key, req.getParameter("user"), "USER", exp));
                }
                case "/token/me" -> {
                    String auth = req.getHeader("Authorization");
                    String token = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : "";
                    String problem = problemOf(token);
                    if (problem != null) {
                        write(res, 401, problem);
                    } else {
                        JsonNode payload = JSON.readTree(decode(token.split("\\.")[1]));
                        write(res, 200, payload.get("sub").asText() + "의 정보 (role: "
                                + payload.get("role").asText() + ")");
                    }
                }
                default -> write(res, 404, "없는 주소");
            }
        }

        /** 서명과 만료 시간만 확인한다. 저장소도 계정 DB도 보지 않는다. 문제가 없으면 null */
        private String problemOf(String token) throws IOException {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return "토큰이 없음";
            }
            String expected = sign(key, parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(expected.getBytes(UTF_8), parts[2].getBytes(UTF_8))) {
                return "서명이 맞지 않음";
            }
            long exp = JSON.readTree(decode(parts[1])).get("exp").asLong();
            if (exp < System.currentTimeMillis() / 1000) {
                return "만료 시간이 지남";
            }
            return null;
        }
    }

    private static String cookie(HttpServletRequest req, String name) {
        if (req.getCookies() == null) {
            return null;
        }
        for (Cookie c : req.getCookies()) {
            if (c.getName().equals(name)) {
                return c.getValue();
            }
        }
        return null;
    }

    private static void write(HttpServletResponse res, int status, String body) throws IOException {
        res.setStatus(status);
        res.setCharacterEncoding("UTF-8");
        res.setContentType("text/plain");
        res.getWriter().write(body);
    }

    // ── JWT (서명만 한 형태) ──

    private static String issue(byte[] key, String sub, String role, long exp) {
        String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = encode("{\"sub\":\"" + sub + "\",\"role\":\"" + role + "\",\"exp\":" + exp + "}");
        return header + "." + payload + "." + sign(key, header + "." + payload);
    }

    private static String sign(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String encode(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(UTF_8));
    }

    /** 키를 쓰지 않는다. Base64URL을 풀 뿐이다 */
    private static String decode(String part) {
        return new String(Base64.getUrlDecoder().decode(part), UTF_8);
    }

    // ── 브라우저 역할 ──

    record Reply(int status, String body, List<String> setCookie) {
        String line() {
            return status + " " + body;
        }
    }

    private Reply send(Server server, String method, String path, String... headers) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + server.port + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        for (int i = 0; i < headers.length; i += 2) {
            req.header(headers[i], headers[i + 1]);
        }
        HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString(UTF_8));
        return new Reply(res.statusCode(), res.body(), res.headers().allValues("set-cookie"));
    }

    /** Set-Cookie 값에서 "이름=값"만 꺼낸다. 브라우저가 Cookie 헤더에 넣는 부분이다 */
    private static String pair(String setCookie) {
        return setCookie.split(";")[0];
    }

    /** 긴 토큰을 좁은 화면에 맞게 36자씩 끊는다 */
    private static String wrap(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i += 36) {
            out.append(i == 0 ? "" : "\n  ").append(text, i, Math.min(i + 36, text.length()));
        }
        return out.toString();
    }

    // ── 질문 1. 쿠키 ──

    @Test
    @DisplayName("Set-Cookie로 준 값은 다음 요청의 Cookie 헤더로 돌아온다. 쿠키가 없으면 서버는 읽지 못한다")
    void cookieComesBackOnNextRequest() throws Exception {
        Server server = new Server("a", KEY);

        Reply first = send(server, "GET", "/theme");
        assertThat(first.setCookie()).containsExactly("theme=dark");

        Reply withCookie = send(server, "GET", "/echo", "Cookie", pair(first.setCookie().get(0)));
        Reply withoutCookie = send(server, "GET", "/echo");
        assertThat(withCookie.body()).isEqualTo("서버가 읽은 theme: dark");
        assertThat(withoutCookie.body()).isEqualTo("서버가 읽은 theme: 없음");

        CiOutput.record(LESSON, "cookie", String.join("\n",
                "[GET /theme 의 응답 헤더]",
                "Set-Cookie: " + first.setCookie().get(0),
                "",
                "[Cookie: theme=dark 를 넣은 GET /echo]",
                withCookie.line(),
                "",
                "[Cookie 헤더 없이 보낸 GET /echo]",
                withoutCookie.line()));
    }

    @Test
    @DisplayName("쿠키의 값을 바꿔 보내면 서버는 바꾼 값을 그대로 읽는다")
    void serverCannotTellEditedCookie() throws Exception {
        Server server = new Server("a", KEY);

        Reply given = send(server, "GET", "/role");
        assertThat(given.setCookie()).containsExactly("role=USER");

        Reply asGiven = send(server, "GET", "/admin", "Cookie", "role=USER");
        Reply edited = send(server, "GET", "/admin", "Cookie", "role=ADMIN");
        assertThat(asGiven.status()).isEqualTo(403);
        assertThat(edited.status()).isEqualTo(200);

        CiOutput.record(LESSON, "cookie-role", String.join("\n",
                "[서버가 준 쿠키]",
                "Set-Cookie: " + given.setCookie().get(0),
                "",
                "[받은 대로 Cookie: role=USER]",
                asGiven.line(),
                "",
                "[값을 바꿔서 Cookie: role=ADMIN]",
                edited.line()));
    }

    // ── 질문 2. 세션 ──

    @Test
    @DisplayName("세션 쿠키에는 세션 ID만 있다. 서버가 세션을 지우면 같은 쿠키가 401을 받는다")
    void sessionCookieHoldsOnlyId() throws Exception {
        Server server = new Server("a", KEY);

        Reply login = send(server, "POST", "/session/login?user=kim");
        assertThat(login.setCookie()).hasSize(1);
        String setCookie = login.setCookie().get(0);
        String cookie = pair(setCookie);
        assertThat(cookie).startsWith("JSESSIONID=");
        assertThat(setCookie).doesNotContain("kim");

        Reply me = send(server, "GET", "/session/me", "Cookie", cookie);
        Reply noCookie = send(server, "GET", "/session/me");
        assertThat(me.line()).isEqualTo("200 kim의 정보");
        assertThat(noCookie.status()).isEqualTo(401);

        send(server, "POST", "/session/logout", "Cookie", cookie);
        Reply afterLogout = send(server, "GET", "/session/me", "Cookie", cookie);
        assertThat(afterLogout.line()).isEqualTo("401 로그인하지 않은 요청");

        CiOutput.record(LESSON, "session", String.join("\n",
                "[POST /login 의 응답 헤더]",
                "Set-Cookie: " + setCookie,
                "",
                "[그 쿠키를 넣은 GET /me]",
                me.line(),
                "",
                "[로그아웃한 뒤, 같은 쿠키를 넣은 GET /me]",
                afterLogout.line()));
    }

    @Test
    @DisplayName("세션을 각자 메모리에 둔 다른 서버와, 다시 켠 서버는 그 세션 ID를 찾지 못한다")
    void otherServerAndRestartedServerDoNotHaveSession() throws Exception {
        Server a = new Server("a", KEY);
        Server b = new Server("b", KEY);

        String cookie = pair(send(a, "POST", "/session/login?user=kim").setCookie().get(0));
        Reply toA = send(a, "GET", "/session/me", "Cookie", cookie);
        Reply toB = send(b, "GET", "/session/me", "Cookie", cookie);
        assertThat(toA.line()).isEqualTo("200 kim의 정보");
        assertThat(toB.line()).isEqualTo("401 로그인하지 않은 요청");

        a.stop();
        Server restarted = new Server("a", KEY);
        Reply afterRestart = send(restarted, "GET", "/session/me", "Cookie", cookie);
        assertThat(afterRestart.line()).isEqualTo("401 로그인하지 않은 요청");

        CiOutput.record(LESSON, "two-servers", String.join("\n",
                "[서버 A에서 로그인하고 받은 쿠키로]",
                "서버 A에 GET /me  → " + toA.line(),
                "서버 B에 GET /me  → " + toB.line(),
                "",
                "[서버 A를 껐다 켠 뒤, 같은 쿠키로]",
                "서버 A에 GET /me  → " + afterRestart.line()));
    }

    // ── 질문 3. 토큰 ──

    @Test
    @DisplayName("JWT는 점으로 나뉜 세 부분이고, 페이로드는 키 없이 읽을 수 있다")
    void jwtHasThreePartsAndReadablePayload() throws Exception {
        Server server = new Server("a", KEY);

        String token = send(server, "POST", "/token/login?user=kim").body();
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        // 키를 쓰지 않고 Base64URL만 푼다
        String header = decode(parts[0]);
        String payload = decode(parts[1]);
        assertThat(header).isEqualTo("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        assertThat(JSON.readTree(payload).get("sub").asText()).isEqualTo("kim");
        assertThat(JSON.readTree(payload).get("role").asText()).isEqualTo("USER");
        assertThat(JSON.readTree(payload).has("exp")).isTrue();

        Reply me = send(server, "GET", "/token/me", "Authorization", "Bearer " + token);
        assertThat(me.line()).isEqualTo("200 kim의 정보 (role: USER)");

        CiOutput.record(LESSON, "jwt", String.join("\n",
                "[서버가 준 토큰을 점에서 나눈 세 부분]",
                "1 " + wrap(parts[0]),
                "2 " + wrap(parts[1]),
                "3 " + wrap(parts[2]),
                "",
                "[키 없이 Base64URL만 풀기]",
                "1 → " + header,
                "2 → " + payload,
                "",
                "[이 토큰을 넣은 GET /me]",
                me.line()));
    }

    @Test
    @DisplayName("페이로드를 바꾸고 서명을 그대로 둔 토큰은 거부된다")
    void tamperedPayloadIsRejected() throws Exception {
        Server server = new Server("a", KEY);

        String token = send(server, "POST", "/token/login?user=kim").body();
        String[] parts = token.split("\\.");
        String edited = decode(parts[1]).replace("\"role\":\"USER\"", "\"role\":\"ADMIN\"");
        String tampered = parts[0] + "." + encode(edited) + "." + parts[2];

        Reply original = send(server, "GET", "/token/me", "Authorization", "Bearer " + token);
        Reply rejected = send(server, "GET", "/token/me", "Authorization", "Bearer " + tampered);
        assertThat(original.status()).isEqualTo(200);
        assertThat(rejected.line()).isEqualTo("401 서명이 맞지 않음");

        CiOutput.record(LESSON, "tamper", String.join("\n",
                "[바꾼 페이로드]",
                edited,
                "",
                "[받은 토큰 그대로 GET /me]",
                original.line(),
                "",
                "[페이로드만 바꾸고 서명은 그대로 둔 토큰으로 GET /me]",
                rejected.line()));
    }

    // ── 질문 4. 얻는 것과 잃는 것 ──

    @Test
    @DisplayName("같은 서명 키를 가진 다른 서버는 토큰을 통과시킨다. 키가 다른 서버는 거부한다")
    void otherServerWithSameKeyAcceptsToken() throws Exception {
        Server a = new Server("a", KEY);
        Server b = new Server("b", KEY);
        Server stranger = new Server("c", OTHER_KEY);

        String token = send(a, "POST", "/token/login?user=kim").body();
        Reply toB = send(b, "GET", "/token/me", "Authorization", "Bearer " + token);
        Reply toStranger = send(stranger, "GET", "/token/me", "Authorization", "Bearer " + token);
        assertThat(toB.line()).isEqualTo("200 kim의 정보 (role: USER)");
        assertThat(toStranger.line()).isEqualTo("401 서명이 맞지 않음");

        CiOutput.record(LESSON, "token-servers", String.join("\n",
                "[서버 A에서 로그인하고 받은 토큰으로]",
                "서버 B(같은 서명 키)에 GET /me  → " + toB.line(),
                "서버 C(다른 서명 키)에 GET /me  → " + toStranger.line()));
    }

    @Test
    @DisplayName("계정을 정지하면 세션은 바로 끊기고, 만료 전의 토큰은 통과한다. 만료된 토큰은 거부된다")
    void suspendedAccountTokenPassesUntilExpiry() throws Exception {
        Server server = new Server("a", KEY);

        String cookie = pair(send(server, "POST", "/session/login?user=kim").setCookie().get(0));
        String token = send(server, "POST", "/token/login?user=kim").body();

        server.suspend("kim");
        assertThat(accounts.get("kim")).isEqualTo("SUSPENDED");

        Reply bySession = send(server, "GET", "/session/me", "Cookie", cookie);
        Reply byToken = send(server, "GET", "/token/me", "Authorization", "Bearer " + token);
        assertThat(bySession.line()).isEqualTo("401 로그인하지 않은 요청");
        assertThat(byToken.line()).isEqualTo("200 kim의 정보 (role: USER)");

        // 만료 시간이 1분 전인 토큰. 서명은 맞다
        String expired = issue(KEY, "kim", "USER", System.currentTimeMillis() / 1000 - 60);
        Reply byExpired = send(server, "GET", "/token/me", "Authorization", "Bearer " + expired);
        assertThat(byExpired.line()).isEqualTo("401 만료 시간이 지남");

        CiOutput.record(LESSON, "suspend", String.join("\n",
                "[kim의 계정을 정지한 뒤]",
                "세션 쿠키로 GET /me            → " + bySession.line(),
                "만료 전의 토큰으로 GET /me     → " + byToken.line(),
                "",
                "[만료 시간이 지난 토큰으로]",
                "GET /me                        → " + byExpired.line()));
    }
}
