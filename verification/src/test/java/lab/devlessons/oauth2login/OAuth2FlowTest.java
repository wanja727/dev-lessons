package lab.devlessons.oauth2login;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "OAuth 2.0과 소셜 로그인"의 주장을 검증한다.
 * 실제 카카오나 구글 서버에는 요청할 수 없어서, RFC 6749 4.1(인가 코드 흐름)과 5.2(에러 이름)의 규칙대로 만든
 * 작은 인가 서버와 쇼핑몰 서버를 JDK HttpServer로 띄운다. 브라우저 역할은 리다이렉트를 따라가지 않는 HttpClient가 한다.
 */
class OAuth2FlowTest {

    static final String LESSON = "oauth2-login";
    static final String CLIENT_ID = "shop";
    static final String CLIENT_SECRET = "s3cr3t";

    HttpServer auth;
    HttpServer shop;
    String authBase;
    String shopBase;
    String redirectUri;
    /** 인가 서버가 받은 토큰 요청 수 */
    final AtomicInteger tokenRequests = new AtomicInteger();
    /** 코드 → 그 코드를 만든 인가 요청의 redirect_uri. 쓰면 지운다(한 번만 쓰기) */
    final Map<String, String> codes = new ConcurrentHashMap<>();
    final Map<String, String> tokens = new ConcurrentHashMap<>();
    final HttpClient browser = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    final HttpClient backChannel = HttpClient.newHttpClient();
    final SecureRandom random = new SecureRandom();

    @BeforeEach
    void start() throws Exception {
        auth = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        shop = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        authBase = "http://localhost:" + auth.getAddress().getPort();
        shopBase = "http://localhost:" + shop.getAddress().getPort();
        redirectUri = shopBase + "/login/oauth2/code/kakao";
        String registeredRedirect = redirectUri;

        // ── 인가 서버 (카카오 역할). 사용자는 이미 로그인하고 동의했다고 본다 ──
        auth.createContext("/oauth/authorize", ex -> {
            Map<String, String> q = query(ex.getRequestURI().getRawQuery());
            if (!CLIENT_ID.equals(q.get("client_id")) || !registeredRedirect.equals(q.get("redirect_uri"))) {
                reply(ex, 400, "redirect_uri_mismatch", Map.of());
                return;
            }
            String code = hex(6);
            codes.put(code, q.get("redirect_uri"));
            reply(ex, 302, "", Map.of("Location", q.get("redirect_uri") + "?code=" + code
                    + "&state=" + enc(q.get("state"))));
        });
        auth.createContext("/oauth/token", ex -> {
            tokenRequests.incrementAndGet();
            Map<String, String> f = query(new String(ex.getRequestBody().readAllBytes(), UTF_8));
            if (!CLIENT_ID.equals(f.get("client_id")) || !CLIENT_SECRET.equals(f.get("client_secret"))) {
                reply(ex, 401, "{\"error\":\"invalid_client\"}", Map.of());
                return;
            }
            String expectedRedirect = codes.remove(f.get("code"));
            if (expectedRedirect == null || !expectedRedirect.equals(f.get("redirect_uri"))) {
                reply(ex, 400, "{\"error\":\"invalid_grant\"}", Map.of());
                return;
            }
            String token = "at-" + hex(6);
            tokens.put(token, "3812294");
            reply(ex, 200, "{\"access_token\":\"" + token + "\",\"token_type\":\"Bearer\","
                    + "\"expires_in\":3600,\"scope\":\"profile\"}", Map.of());
        });
        auth.createContext("/v2/user/me", ex -> {
            String h = ex.getRequestHeaders().getFirst("Authorization");
            String id = h == null ? null : tokens.get(h.replace("Bearer ", ""));
            if (id == null) {
                reply(ex, 401, "", Map.of());
                return;
            }
            reply(ex, 200, "{\"id\":" + id + ",\"nickname\":\"kim\"}", Map.of());
        });

        // ── 쇼핑몰 서버 (우리 서비스, OAuth의 클라이언트) ──
        shop.createContext("/oauth2/authorization/kakao", ex -> {
            String state = hex(4);
            reply(ex, 302, "", Map.of(
                    "Set-Cookie", "OAUTH_STATE=" + state,
                    "Location", authBase + "/oauth/authorize?response_type=code&client_id=" + CLIENT_ID
                            + "&redirect_uri=" + enc(redirectUri) + "&scope=profile&state=" + state));
        });
        shop.createContext("/login/oauth2/code/kakao", ex -> {
            Map<String, String> q = query(ex.getRequestURI().getRawQuery());
            String cookie = ex.getRequestHeaders().getFirst("Cookie");
            String saved = cookie == null ? null : cookie.replace("OAUTH_STATE=", "");
            if (saved == null || !saved.equals(q.get("state"))) {
                reply(ex, 400, "state가 달라서 멈춤 (토큰을 요청하지 않음)", Map.of());
                return;
            }
            try {
                HttpResponse<String> token = backChannel.send(tokenRequest(q.get("code"), CLIENT_SECRET, redirectUri),
                        HttpResponse.BodyHandlers.ofString());
                String accessToken = token.body().replaceAll(".*\"access_token\":\"([^\"]+)\".*", "$1");
                HttpResponse<String> me = backChannel.send(HttpRequest.newBuilder(URI.create(authBase + "/v2/user/me"))
                        .header("Authorization", "Bearer " + accessToken).build(), HttpResponse.BodyHandlers.ofString());
                reply(ex, 200, "로그인 완료. 카카오 사용자 정보: " + me.body(), Map.of());
            } catch (Exception e) {
                reply(ex, 500, e.toString(), Map.of());
            }
        });
        auth.start();
        shop.start();
    }

    @AfterEach
    void stop() {
        auth.stop(0);
        shop.stop(0);
    }

    HttpRequest tokenRequest(String code, String secret, String redirect) {
        String form = "grant_type=authorization_code&code=" + enc(code) + "&redirect_uri=" + enc(redirect)
                + "&client_id=" + CLIENT_ID + "&client_secret=" + enc(secret);
        return HttpRequest.newBuilder(URI.create(authBase + "/oauth/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
    }

    static void reply(HttpExchange ex, int status, String body, Map<String, String> headers) {
        try (ex) {
            headers.forEach((k, v) -> ex.getResponseHeaders().add(k, v));
            byte[] b = body.getBytes(UTF_8);
            ex.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
            if (b.length > 0) {
                ex.getResponseBody().write(b);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static Map<String, String> query(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return m;
        }
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            m.put(URLDecoder.decode(pair.substring(0, i), UTF_8), URLDecoder.decode(pair.substring(i + 1), UTF_8));
        }
        return m;
    }

    static String enc(String s) {
        return URLEncoder.encode(s, UTF_8);
    }

    String hex(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    String local(String url) {
        // 읽기 쉽게 redirect_uri의 인코딩을 풀어서 보여 준다
        return URLDecoder.decode(url, UTF_8).replace(shopBase, "(쇼핑몰)").replace(authBase, "(인가 서버)");
    }

    /** 1~3단계를 밟고 돌아온 주소(콜백)와 state 쿠키를 돌려준다 */
    Map<String, String> walkToCallback(List<String> log) throws Exception {
        HttpResponse<String> r1 = browser.send(HttpRequest.newBuilder(URI.create(shopBase + "/oauth2/authorization/kakao"))
                .build(), HttpResponse.BodyHandlers.ofString());
        String cookie = r1.headers().firstValue("Set-Cookie").orElseThrow();
        String toAuth = r1.headers().firstValue("Location").orElseThrow();
        log.add("1) 브라우저 → 쇼핑몰  GET /oauth2/authorization/kakao");
        log.add("   ← " + r1.statusCode() + "  Location: " + local(toAuth));
        HttpResponse<String> r2 = browser.send(HttpRequest.newBuilder(URI.create(toAuth)).build(),
                HttpResponse.BodyHandlers.ofString());
        String callback = r2.headers().firstValue("Location").orElseThrow();
        log.add("2) 브라우저 → 인가 서버 (kim이 로그인하고 동의함)");
        log.add("3)   ← " + r2.statusCode() + "  Location: " + local(callback));
        Map<String, String> out = new LinkedHashMap<>();
        out.put("callback", callback);
        out.put("cookie", cookie);
        return out;
    }

    @Test
    @DisplayName("인가 코드 흐름을 끝까지 밟으면 쇼핑몰이 코드를 토큰으로 바꿔 사용자 정보를 받는다")
    void fullFlow() throws Exception {
        List<String> log = new ArrayList<>();
        Map<String, String> c = walkToCallback(log);
        HttpResponse<String> r3 = browser.send(HttpRequest.newBuilder(URI.create(c.get("callback")))
                .header("Cookie", c.get("cookie")).build(), HttpResponse.BodyHandlers.ofString());
        log.add("4) 브라우저 → 쇼핑몰  GET " + local(c.get("callback")).replace("(쇼핑몰)", ""));
        log.add("   쇼핑몰: state가 쿠키의 값과 같은지 확인");
        log.add("5) 쇼핑몰 → 인가 서버 POST /oauth/token (code, client_secret)  [서버끼리]");
        log.add("6) 쇼핑몰 → 인가 서버 GET /v2/user/me (Authorization: Bearer ...)  [서버끼리]");
        log.add("   ← " + r3.statusCode() + "  " + r3.body());

        assertThat(c.get("callback")).contains("code=").contains("state=");
        assertThat(r3.statusCode()).isEqualTo(200);
        assertThat(r3.body()).contains("3812294");
        assertThat(tokenRequests.get()).isEqualTo(1);
        CiOutput.record(LESSON, "flow", String.join("\n", log));
    }

    @Test
    @DisplayName("같은 코드로 두 번째 토큰 요청은 invalid_grant, 틀린 client_secret은 invalid_client, 다른 redirect_uri는 invalid_grant")
    void tokenEndpointRules() throws Exception {
        Map<String, String> c = walkToCallback(new ArrayList<>());
        String code = query(URI.create(c.get("callback")).getRawQuery()).get("code");

        HttpResponse<String> wrongSecret = backChannel.send(tokenRequest(code, "guess", redirectUri),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> first = backChannel.send(tokenRequest(code, CLIENT_SECRET, redirectUri),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> again = backChannel.send(tokenRequest(code, CLIENT_SECRET, redirectUri),
                HttpResponse.BodyHandlers.ofString());
        Map<String, String> c2 = walkToCallback(new ArrayList<>());
        String code2 = query(URI.create(c2.get("callback")).getRawQuery()).get("code");
        HttpResponse<String> otherRedirect = backChannel.send(
                tokenRequest(code2, CLIENT_SECRET, "https://evil.example/callback"),
                HttpResponse.BodyHandlers.ofString());

        assertThat(wrongSecret.body()).contains("invalid_client");
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(again.body()).contains("invalid_grant");
        assertThat(otherRedirect.body()).contains("invalid_grant");
        CiOutput.record(LESSON, "token-rules", String.join("\n",
                "틀린 client_secret으로 토큰 요청 → " + wrongSecret.statusCode() + " " + wrongSecret.body(),
                "맞는 값으로 토큰 요청        → " + first.statusCode() + " " + first.body(),
                "같은 코드로 다시 토큰 요청   → " + again.statusCode() + " " + again.body(),
                "다른 redirect_uri로 토큰 요청 → " + otherRedirect.statusCode() + " " + otherRedirect.body()));
    }

    @Test
    @DisplayName("돌아온 state가 쿠키의 값과 다르면 쇼핑몰은 토큰을 요청하지 않는다")
    void stateMismatchStops() throws Exception {
        Map<String, String> c = walkToCallback(new ArrayList<>());
        String forged = c.get("callback").replaceAll("state=[^&]+", "state=attacker");
        int before = tokenRequests.get();
        HttpResponse<String> res = browser.send(HttpRequest.newBuilder(URI.create(forged))
                .header("Cookie", c.get("cookie")).build(), HttpResponse.BodyHandlers.ofString());

        assertThat(res.statusCode()).isEqualTo(400);
        assertThat(tokenRequests.get()).isEqualTo(before);
        CiOutput.record(LESSON, "state", "state=attacker로 콜백 → " + res.statusCode() + " " + res.body()
                + "\n인가 서버가 받은 토큰 요청 수: " + before + " → " + tokenRequests.get());
    }
}
