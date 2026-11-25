package lab.devlessons.websocketsse;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpointConfig;
import java.io.InputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.MiniWeb;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.websocket.server.WsSci;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 레슨 "웹소켓과 SSE"의 주장을 검증한다.
 * 폴링과 SSE는 스프링 MVC(MiniWeb)로, 웹소켓은 톰캣의 자카르타 웹소켓(tomcat-embed-websocket)을 직접 띄워 확인한다.
 * 클라이언트는 자바 HttpClient와 그 WebSocket이다. 브라우저의 EventSource와 다시 연결하기는 HTML 명세가 근거다.
 */
class WebSocketSseTest {

    static final String LESSON = "websocket-sse";
    static final AtomicInteger POLLS = new AtomicInteger();
    static volatile long shippedAt;

    @RestController
    static class OrderApi {
        /** 폴링: 부를 때마다 지금 상태를 돌려준다 */
        @GetMapping("/orders/1/status")
        String status() {
            POLLS.incrementAndGet();
            return System.currentTimeMillis() >= shippedAt ? "배송 시작" : "준비 중";
        }

        /** SSE: 응답 하나를 열어 두고 이벤트를 흘려 보낸다 */
        @GetMapping(value = "/orders/1/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter events() {
            SseEmitter emitter = new SseEmitter(10_000L);
            CompletableFuture.runAsync(() -> {
                try {
                    String[] steps = {"결제 완료", "배송 시작", "배송 완료"};
                    for (int i = 0; i < steps.length; i++) {
                        Thread.sleep(200);
                        emitter.send(SseEmitter.event().id(String.valueOf(i + 1)).name("status").data(steps[i]));
                    }
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            });
            return emitter;
        }

        /** 흔한 실수: 시간 제한을 짧게(기본값에 맡겨) 두고 이벤트를 늦게 보낸다 */
        @GetMapping(value = "/orders/1/slow-events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter slowEvents() {
            SseEmitter emitter = new SseEmitter(500L);
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(3000);
                    emitter.send(SseEmitter.event().data("배송 시작"));
                } catch (Exception ignored) {
                    // 이미 끝난 연결
                }
            });
            return emitter;
        }
    }

    @Test
    @DisplayName("1초 동안 100ms마다 폴링하면 요청은 열 번 남짓인데 바뀐 상태를 알려 준 응답은 일부다")
    void pollingWastesRequests() throws Exception {
        POLLS.set(0);
        try (MiniWeb web = MiniWeb.start(OrderApi.class)) {
            shippedAt = Long.MAX_VALUE;
            web.get("/orders/1/status"); // 첫 요청의 준비 시간을 빼려고 한 번 먼저 부른다
            POLLS.set(0);
            shippedAt = System.currentTimeMillis() + 700;
            List<String> answers = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                answers.add(web.get("/orders/1/status").body());
                Thread.sleep(100);
            }
            long changed = answers.stream().filter("배송 시작"::equals).count();

            assertThat(POLLS.get()).isEqualTo(10);
            assertThat(answers.get(0)).isEqualTo("준비 중");
            assertThat(changed).isGreaterThan(0).isLessThan(10);
            CiOutput.record(LESSON, "polling", "100ms마다 10번 물은 응답: " + answers
                    + "\n서버가 받은 요청: " + POLLS.get() + "번, 그 가운데 상태가 바뀐 뒤의 응답: " + changed + "번"
                    + "\n(배송 시작은 0.7초쯤에 일어났다. 알게 된 것은 그다음 폴링 때다)");
        }
    }

    @Test
    @DisplayName("SseEmitter는 응답 하나를 열어 두고 text/event-stream으로 id, event, data 줄을 차례로 보낸다")
    void serverSentEvents() throws Exception {
        try (MiniWeb web = MiniWeb.start(OrderApi.class)) {
            HttpClient http = HttpClient.newHttpClient();
            long t0 = System.nanoTime();
            List<String> lines = new ArrayList<>();
            HttpResponse<java.util.stream.Stream<String>> res = http.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + web.port() + "/orders/1/events"))
                            .header("Accept", "text/event-stream").build(),
                    HttpResponse.BodyHandlers.ofLines());
            res.body().forEach(line -> lines.add(String.format("%4dms | %s", (System.nanoTime() - t0) / 1_000_000, line)));

            assertThat(res.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");
            assertThat(String.join("\n", lines)).contains("id:1").contains("event:status").contains("data:배송 완료");
            CiOutput.record(LESSON, "sse", "응답 " + res.statusCode() + ", Content-Type: "
                    + res.headers().firstValue("Content-Type").orElse("") + "\n(받은 시각 | 받은 줄)\n" + String.join("\n", lines));
        }
    }

    @Test
    @DisplayName("SseEmitter의 시간 제한이 지나면 이벤트를 보내기 전에 응답이 끝난다")
    void sseTimeoutEndsStream() throws Exception {
        try (MiniWeb web = MiniWeb.start(OrderApi.class)) {
            HttpClient http = HttpClient.newHttpClient();
            long t0 = System.nanoTime();
            HttpResponse<String> res = http.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + web.port() + "/orders/1/slow-events"))
                            .header("Accept", "text/event-stream").build(),
                    HttpResponse.BodyHandlers.ofString());
            long ms = (System.nanoTime() - t0) / 1_000_000;

            assertThat(res.body()).doesNotContain("배송 시작");
            assertThat(ms).isLessThan(2500);
            CiOutput.record(LESSON, "sse-timeout", "new SseEmitter(500L), 이벤트는 3초 뒤에 보냄\n"
                    + ms + "ms 뒤 응답이 끝남, 상태 " + res.statusCode() + ", 받은 본문: "
                    + (res.body().isBlank() ? "(없음)" : res.body().strip()));
        }
    }

    // ── 웹소켓: 톰캣의 자카르타 웹소켓을 직접 띄운다 ──

    /** 연결되자마자 서버가 먼저 한 번 보내고, 받은 글에는 답한다 */
    public static class OrderSocket extends Endpoint {
        @Override
        public void onOpen(Session session, EndpointConfig config) {
            session.getAsyncRemote().sendText("서버가 먼저: 주문 1 배송 시작");
            session.addMessageHandler(String.class,
                    (MessageHandler.Whole<String>) msg -> session.getAsyncRemote().sendText("서버의 답: " + msg + " 받았어요"));
        }
    }

    static Tomcat startWebSocketServer() throws Exception {
        Path dir = Files.createTempDirectory("ws");
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(dir.toString());
        tomcat.setPort(0);
        Context ctx = tomcat.addContext("", dir.toString());
        ctx.addServletContainerInitializer(new WsSci(), null);
        // WsSci가 만든 ServerContainer에 엔드포인트를 더한다
        ctx.addServletContainerInitializer((classes, servletContext) -> {
            ServerContainer sc = (ServerContainer) servletContext
                    .getAttribute("jakarta.websocket.server.ServerContainer");
            try {
                sc.addEndpoint(ServerEndpointConfig.Builder.create(OrderSocket.class, "/ws/orders").build());
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }, null);
        // 서블릿이 하나도 없으면 톰캣이 필터(WsFilter)를 거치기 전에 404로 답해서, 빈 서블릿을 하나 둔다
        Tomcat.addServlet(ctx, "default", new jakarta.servlet.http.HttpServlet() {
        });
        ctx.addServletMappingDecoded("/", "default");
        tomcat.getConnector();
        tomcat.start();
        return tomcat;
    }

    @Test
    @DisplayName("웹소켓은 HTTP Upgrade 요청으로 시작하고, 서버는 101과 키로 계산한 Sec-WebSocket-Accept로 답한다")
    void upgradeHandshake() throws Exception {
        Tomcat tomcat = startWebSocketServer();
        try (Socket s = new Socket("localhost", tomcat.getConnector().getLocalPort())) {
            s.setSoTimeout(3000);
            String request = "GET /ws/orders HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                    + "Sec-WebSocket-Version: 13\r\n\r\n";
            s.getOutputStream().write(request.getBytes(UTF_8));
            InputStream in = s.getInputStream();
            StringBuilder head = new StringBuilder();
            int b;
            while ((b = in.read()) != -1) {
                head.append((char) b);
                if (head.toString().endsWith("\r\n\r\n")) {
                    break;
                }
            }
            String response = head.toString().replace("\r\n", "\n").replaceAll("(?m)^Date: .*\n", "");

            assertThat(response).startsWith("HTTP/1.1 101");
            assertThat(response).containsIgnoringCase("Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=");
            CiOutput.record(LESSON, "handshake", "[보낸 요청]\n" + request.replace("\r\n", "\n")
                    + "[받은 응답]\n" + response.strip());
        } finally {
            tomcat.stop();
            tomcat.destroy();
        }
    }

    @Test
    @DisplayName("웹소켓 연결이 열리면 서버가 먼저 보낼 수 있고, 같은 연결로 클라이언트도 보낸다")
    void bothDirections() throws Exception {
        Tomcat tomcat = startWebSocketServer();
        try {
            List<String> received = new CopyOnWriteArrayList<>();
            CountDownLatch two = new CountDownLatch(2);
            WebSocket ws = HttpClient.newHttpClient().newWebSocketBuilder()
                    .buildAsync(URI.create("ws://localhost:" + tomcat.getConnector().getLocalPort() + "/ws/orders"),
                            new WebSocket.Listener() {
                                @Override
                                public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
                                    received.add(data.toString());
                                    two.countDown();
                                    w.request(1);
                                    return null;
                                }
                            }).join();
            Thread.sleep(200);
            ws.sendText("배송지 바꿔 주세요", true).join();
            boolean done = two.await(3, TimeUnit.SECONDS);
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "끝").join();

            assertThat(done).isTrue();
            assertThat(received.get(0)).startsWith("서버가 먼저");
            assertThat(received.get(1)).contains("배송지 바꿔 주세요");
            CiOutput.record(LESSON, "websocket", "연결이 열림 (요청 없이)\n← " + received.get(0)
                    + "\n→ 배송지 바꿔 주세요\n← " + received.get(1));
        } finally {
            tomcat.stop();
            tomcat.destroy();
        }
    }
}
