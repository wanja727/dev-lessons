package lab.devlessons.urltoresponse;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 레슨 "브라우저에 주소를 입력하면"의 주장을 검증한다.
 * 브라우저 대신 자바 코드로 같은 순서를 밟는다: URL 쪼개기, 이름을 주소로 바꾸기, 소켓으로 연결하고 HTTP 요청 글 쓰기.
 */
class UrlToResponseTest {

    static final String LESSON = "url-to-response";

    static MiniWeb web;
    static int port;

    @RestController
    static class ShopController {
        @GetMapping("/products/{id}")
        Map<String, Object> product(@PathVariable long id) {
            return Map.of("id", id, "name", "커피");
        }

        /** 서버가 본 클라이언트 쪽 포트. 같은 연결이면 같은 값이다 */
        @GetMapping("/port")
        String port(HttpServletRequest request) {
            return String.valueOf(request.getRemotePort());
        }

        @GetMapping("/slow")
        String slow() throws InterruptedException {
            Thread.sleep(3000);
            return "늦은 응답";
        }
    }

    @BeforeAll
    static void start() {
        web = MiniWeb.start(ShopController.class);
        port = ((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext) web.context())
                .getWebServer().getPort();
    }

    @AfterAll
    static void stop() {
        web.close();
    }

    @Test
    @DisplayName("URI로 URL을 쪼개면 스킴, 호스트, 포트(-1), 경로, 쿼리가 나온다")
    void splitUrl() {
        URI uri = URI.create("https://shop.example.com" + "/products/7?tab=review#top");

        assertThat(uri.getScheme()).isEqualTo("https");
        assertThat(uri.getHost()).isEqualTo("shop.example.com");
        assertThat(uri.getPort()).isEqualTo(-1);
        assertThat(uri.getPath()).isEqualTo("/products/7");
        assertThat(uri.getQuery()).isEqualTo("tab=review");
        assertThat(uri.getFragment()).isEqualTo("top");
        CiOutput.record(LESSON, "split", String.join("\n",
                "scheme   = " + uri.getScheme(),
                "host     = " + uri.getHost(),
                "port     = " + uri.getPort(),
                "path     = " + uri.getPath(),
                "query    = " + uri.getQuery(),
                "fragment = " + uri.getFragment()));
    }

    @Test
    @DisplayName("localhost는 127.0.0.1로, 숫자 주소는 그대로, .invalid 이름은 UnknownHostException")
    void resolveNames() throws Exception {
        InetAddress local = InetAddress.getByName("localhost");
        InetAddress number = InetAddress.getByName("127.0.0.1");

        assertThat(local.isLoopbackAddress()).isTrue();
        assertThat(number.getHostAddress()).isEqualTo("127.0.0.1");
        assertThatThrownBy(() -> InetAddress.getByName("shop.invalid"))
                .isInstanceOf(UnknownHostException.class);
        String unknown;
        try {
            InetAddress.getByName("shop.invalid");
            unknown = "찾음";
        } catch (UnknownHostException e) {
            unknown = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        CiOutput.record(LESSON, "dns", String.join("\n",
                "localhost    → " + local.getHostAddress(),
                "127.0.0.1    → " + number.getHostAddress(),
                "shop.invalid → " + unknown));
    }

    @Test
    @DisplayName("소켓에 HTTP 요청 글을 그대로 쓰면 서버가 응답 글을 돌려준다")
    void rawRequestOverSocket() throws Exception {
        String request = "GET /products/7 HTTP/1.1\r\n"
                + "Host: localhost:" + port + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";
        String response;
        try (Socket socket = new Socket("localhost", port)) {
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(UTF_8));
            out.flush();
            InputStream in = socket.getInputStream();
            response = new String(in.readAllBytes(), UTF_8);
        }

        assertThat(response).startsWith("HTTP/1.1 200");
        assertThat(response).contains("Content-Type: application/json");
        assertThat(response).contains("\"name\":\"커피\"");
        CiOutput.record(LESSON, "raw", "[소켓에 쓴 요청]\n" + request.replace("\r\n", "\n")
                + "[소켓에서 읽은 응답]\n" + hideDate(response.replace("\r\n", "\n")));
    }

    @Test
    @DisplayName("같은 HttpClient로 두 번 보내면 서버가 본 클라이언트 포트가 같고, 첫 요청이 더 오래 걸린다")
    void secondRequestReusesConnection() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/port")).build();

        long t0 = System.nanoTime();
        String first = client.send(req, HttpResponse.BodyHandlers.ofString()).body();
        long t1 = System.nanoTime();
        String second = client.send(req, HttpResponse.BodyHandlers.ofString()).body();
        long t2 = System.nanoTime();

        assertThat(second).isEqualTo(first);
        CiOutput.record(LESSON, "reuse", String.join("\n",
                "첫 요청: 클라이언트 포트 " + first + ", " + ms(t1 - t0),
                "두 번째: 클라이언트 포트 " + second + ", " + ms(t2 - t1),
                "(같은 포트 = 같은 TCP 연결을 다시 씀)"));
    }

    @Test
    @DisplayName("읽기 시간 제한 1초로 3초 걸리는 주소를 부르면 1초쯤에 시간 초과 예외가 난다")
    void readTimeout() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
        factory.setReadTimeout(Duration.ofSeconds(1));
        RestClient client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(factory)
                .build();

        long t0 = System.nanoTime();
        Throwable error = null;
        try {
            client.get().uri("/slow").retrieve().body(String.class);
        } catch (Throwable e) {
            error = e;
        }
        long elapsed = System.nanoTime() - t0;

        assertThat(error).isInstanceOf(ResourceAccessException.class);
        assertThat(elapsed).isLessThan(Duration.ofMillis(2500).toNanos());
        CiOutput.record(LESSON, "timeout", ms(elapsed) + " 뒤에\n"
                + error.getClass().getSimpleName() + "\n원인: " + rootCause(error));
    }

    @Test
    @DisplayName("없는 이름은 UnknownHostException, 아무도 듣지 않는 포트는 ConnectException(Connection refused)")
    void connectionRefused() throws Exception {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        assertThatThrownBy(() -> new Socket("localhost", closedPort).close())
                .isInstanceOf(ConnectException.class);
        assertThatThrownBy(() -> new Socket("shop.invalid", 80).close())
                .isInstanceOf(UnknownHostException.class);
        String refused = describe(() -> new Socket("localhost", closedPort).close());
        String noHost = describe(() -> new Socket("shop.invalid", 80).close());
        CiOutput.record(LESSON, "errors", String.join("\n",
                "new Socket(\"shop.invalid\", 80)",
                "→ " + noHost,
                "new Socket(\"localhost\", " + closedPort + ")  // 아무도 안 들음",
                "→ " + refused));
    }

    interface Call {
        void run() throws Exception;
    }

    private static String describe(Call call) {
        try {
            call.run();
            return "성공";
        } catch (Exception e) {
            return rootCause(e);
        }
    }

    private static String rootCause(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
    }

    private static String ms(long nanos) {
        return String.format("%.1fms", nanos / 1_000_000.0);
    }

    private static String hideDate(String response) {
        return response.replaceAll("(?m)^Date: .*$", "Date: (보낸 시각)");
    }
}
