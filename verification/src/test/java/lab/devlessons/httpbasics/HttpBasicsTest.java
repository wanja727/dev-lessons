package lab.devlessons.httpbasics;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * 레슨 "HTTP 요청과 응답"의 주장을 검증한다.
 * 스프링 부트(톰캣 10.1) 서버에 소켓으로 요청 글을 직접 보내고, 받은 응답 글을 그대로 본다.
 */
class HttpBasicsTest {

    static final String LESSON = "http-basics";
    static MiniWeb web;

    record NewProduct(String name, int price) {
    }

    record Product(long id, String name, int price) {
    }

    @RestController
    static class ProductController {
        @PostMapping(value = "/products", consumes = "application/json")
        ResponseEntity<Product> create(@RequestBody NewProduct body) {
            return ResponseEntity.status(HttpStatus.CREATED).body(new Product(1, body.name(), body.price()));
        }

        @GetMapping("/products/{id}")
        Product get(@PathVariable long id) {
            return new Product(id, "커피", 4500);
        }

        @GetMapping("/menu")
        String menu() {
            return "커피 4500원";
        }

        @GetMapping("/stream")
        StreamingResponseBody stream() {
            return out -> {
                for (int i = 1; i <= 3; i++) {
                    out.write(("주문" + i + "\n").getBytes(UTF_8));
                    out.flush();
                }
            };
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("처리 중 오류");
        }
    }

    @BeforeAll
    static void start() {
        web = MiniWeb.start(ProductController.class);
    }

    @AfterAll
    static void stop() {
        web.close();
    }

    /** 요청 글을 소켓에 그대로 쓰고, 서버가 연결을 닫을 때까지 읽은 응답 글을 돌려준다 */
    static String send(String request) throws Exception {
        try (Socket socket = new Socket("localhost", web.port())) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(UTF_8));
            out.flush();
            InputStream in = socket.getInputStream();
            return tidy(new String(in.readAllBytes(), UTF_8));
        }
    }

    static String tidy(String response) {
        return response.replace("\r\n", "\n").replaceAll("(?m)^Date: .*\n", "");
    }

    static String statusLine(String response) {
        return response.lines().findFirst().orElse("");
    }

    static String header(String response, String name) {
        return response.lines().takeWhile(l -> !l.isEmpty())
                .filter(l -> l.toLowerCase().startsWith(name.toLowerCase() + ":"))
                .findFirst().orElse("(없음)");
    }

    static String body(String response) {
        int i = response.indexOf("\n\n");
        return i < 0 ? "" : response.substring(i + 2);
    }

    @Test
    @DisplayName("소켓으로 POST 요청 글을 보내면 상태 줄, 헤더, 빈 줄, 본문으로 된 응답이 온다")
    void requestAndResponseShape() throws Exception {
        String json = "{\"name\":\"커피\",\"price\":4500}";
        String request = "POST /products HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + json.getBytes(UTF_8).length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n"
                + json;
        String response = send(request);

        assertThat(statusLine(response)).isEqualTo("HTTP/1.1 201 ");
        assertThat(body(response)).contains("\"id\":1");
        CiOutput.record(LESSON, "shape", "[보낸 요청]\n" + request.replace("\r\n", "\n")
                + "\n\n[받은 응답]\n" + response);
    }

    @Test
    @DisplayName("Host 헤더가 없는 HTTP/1.1 요청에 톰캣은 400으로 답한다")
    void missingHostIs400() throws Exception {
        String response = send("GET /products/1 HTTP/1.1\r\n\r\n");
        String withHost = send("GET /products/1 HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        assertThat(statusLine(response)).startsWith("HTTP/1.1 400");
        assertThat(statusLine(withHost)).startsWith("HTTP/1.1 200");
        CiOutput.record(LESSON, "no-host", "Host 없음 → " + statusLine(response)
                + "\nHost: localhost → " + statusLine(withHost));
    }

    @Test
    @DisplayName("HEAD는 GET과 같은 Content-Length를 주고 본문이 없다")
    void headHasNoBody() throws Exception {
        String get = send("GET /menu HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
        String head = send("HEAD /menu HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        assertThat(header(head, "Content-Length")).isEqualTo(header(get, "Content-Length"));
        assertThat(body(get)).isEqualTo("커피 4500원");
        assertThat(body(head)).isEmpty();
        CiOutput.record(LESSON, "head", "[GET /menu]\n" + get + "\n\n[HEAD /menu]\n" + head + "(본문 없음)");
    }

    @Test
    @DisplayName("없는 경로는 404, 맞지 않는 메서드는 405와 Allow, 처리 중 예외는 500")
    void statusCodes() throws Exception {
        String notFound = send("GET /nope HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
        String wrongMethod = send("DELETE /products/1 HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
        String boom = send("GET /boom HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        assertThat(statusLine(notFound)).startsWith("HTTP/1.1 404");
        assertThat(statusLine(wrongMethod)).startsWith("HTTP/1.1 405");
        assertThat(header(wrongMethod, "Allow")).contains("GET");
        assertThat(statusLine(boom)).startsWith("HTTP/1.1 500");
        CiOutput.record(LESSON, "status", String.join("\n",
                "GET /nope         → " + statusLine(notFound),
                "DELETE /products/1 → " + statusLine(wrongMethod) + "  " + header(wrongMethod, "Allow"),
                "GET /boom         → " + statusLine(boom)));
    }

    @Test
    @DisplayName("길이를 아는 응답은 Content-Length, 스트림으로 쓴 응답은 Transfer-Encoding: chunked")
    void contentLengthOrChunked() throws Exception {
        String fixed = send("GET /menu HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
        String streamed = send("GET /stream HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        assertThat(header(fixed, "Content-Length")).isEqualTo("Content-Length: 14");
        assertThat(header(streamed, "Transfer-Encoding")).isEqualTo("Transfer-Encoding: chunked");
        CiOutput.record(LESSON, "length", "[GET /menu]\n" + fixed + "\n\n[GET /stream]\n" + streamed);
    }

    @Test
    @DisplayName("JSON만 만드는 주소에 Accept: application/xml을 보내면 406")
    void acceptMismatchIs406() throws Exception {
        String json = send("GET /products/1 HTTP/1.1\r\nHost: localhost\r\nAccept: application/json\r\n"
                + "Connection: close\r\n\r\n");
        String xml = send("GET /products/1 HTTP/1.1\r\nHost: localhost\r\nAccept: application/xml\r\n"
                + "Connection: close\r\n\r\n");

        assertThat(statusLine(json)).startsWith("HTTP/1.1 200");
        assertThat(statusLine(xml)).startsWith("HTTP/1.1 406");
        CiOutput.record(LESSON, "accept", "Accept: application/json → " + statusLine(json)
                + "\nAccept: application/xml  → " + statusLine(xml));
    }

    @Test
    @DisplayName("소켓 하나에 요청 둘을 이어 쓰면 응답 둘이 차례로 오고, Connection: close 뒤에 서버가 연결을 닫는다")
    void twoRequestsOnOneConnection() throws Exception {
        String requests = "GET /products/1 HTTP/1.1\r\nHost: localhost\r\n\r\n"
                + "GET /products/2 HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n";
        String response;
        int afterClose;
        try (Socket socket = new Socket("localhost", web.port())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(requests.getBytes(UTF_8));
            InputStream in = socket.getInputStream();
            response = tidy(new String(in.readAllBytes(), UTF_8));
            afterClose = in.read();
        }

        List<String> statusLines = response.lines().filter(l -> l.startsWith("HTTP/1.1")).toList();
        assertThat(statusLines).hasSize(2);
        assertThat(response).contains("\"id\":1").contains("\"id\":2");
        assertThat(afterClose).isEqualTo(-1);
        CiOutput.record(LESSON, "keep-alive", response + "\n\n(두 번째 응답 뒤 read → " + afterClose + ": 서버가 연결을 닫음)");
    }

    @Test
    @DisplayName("JSON 본문에 Content-Type이 없으면 @RequestBody 주소가 415를 준다")
    void missingContentTypeIs415() throws Exception {
        String json = "{\"name\":\"커피\",\"price\":4500}";
        int len = json.getBytes(UTF_8).length;
        String without = send("POST /products HTTP/1.1\r\nHost: localhost\r\nContent-Length: " + len
                + "\r\nConnection: close\r\n\r\n" + json);
        String with = send("POST /products HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\n"
                + "Content-Length: " + len + "\r\nConnection: close\r\n\r\n" + json);

        assertThat(statusLine(without)).startsWith("HTTP/1.1 415");
        assertThat(statusLine(with)).startsWith("HTTP/1.1 201");
        CiOutput.record(LESSON, "no-type", "Content-Type 없음 → " + statusLine(without) + "\n" + body(without)
                + "\n\nContent-Type: application/json → " + statusLine(with) + "\n" + body(with));
    }

    static Map<String, String> unused() {
        return Map.of();
    }
}
