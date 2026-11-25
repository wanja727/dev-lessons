package lab.devlessons.networklayers;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 레슨 "OSI 7계층과 TCP/IP 4계층"의 주장을 검증한다.
 * 계층의 아래쪽(IP 헤더, 이더넷)은 자바에서 보이지 않는다. 자바 소켓에 드러나는 결과만 확인한다.
 */
class NetworkLayersTest {

    static final String LESSON = "network-layers";
    static final String REQUEST = "GET /products/7 HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n";

    @TempDir Path tmp;

    /** 통로가 무엇이든 HTTP 요청 글을 읽고 응답 글을 쓰는 처리기. 받은 요청의 첫 줄을 본문에 담는다 */
    static void serveOnce(ServerSocketChannel server) {
        try (SocketChannel ch = server.accept()) {
            ByteArrayOutputStream got = new ByteArrayOutputStream();
            ByteBuffer buf = ByteBuffer.allocate(1024);
            while (!got.toString(UTF_8).contains("\r\n\r\n") && ch.read(buf) > 0) {
                buf.flip();
                got.write(buf.array(), 0, buf.limit());
                buf.clear();
            }
            String firstLine = got.toString(UTF_8).lines().findFirst().orElse("");
            byte[] body = ("받은 요청: " + firstLine).getBytes(UTF_8);
            String head = "HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=UTF-8\r\n"
                    + "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
            ch.write(ByteBuffer.wrap(head.getBytes(UTF_8)));
            ch.write(ByteBuffer.wrap(body));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String sendRequest(SocketAddress address, StandardProtocolFamily family) throws Exception {
        try (SocketChannel ch = SocketChannel.open(family)) {
            ch.connect(address);
            ch.write(ByteBuffer.wrap(REQUEST.getBytes(UTF_8)));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteBuffer buf = ByteBuffer.allocate(1024);
            while (ch.read(buf) > 0) {
                buf.flip();
                out.write(buf.array(), 0, buf.limit());
                buf.clear();
            }
            return out.toString(UTF_8);
        }
    }

    @Test
    @DisplayName("같은 HTTP 요청 글을 TCP 소켓과 유닉스 도메인 소켓으로 보내면 같은 응답이 온다")
    void sameHttpOverDifferentTransports() throws Exception {
        String overTcp;
        try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.INET)) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            CompletableFuture<Void> serving = CompletableFuture.runAsync(() -> serveOnce(server));
            overTcp = sendRequest(server.getLocalAddress(), StandardProtocolFamily.INET);
            serving.join();
        }
        String overUnix;
        Path socketFile = tmp.resolve("shop.sock");
        try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            server.bind(UnixDomainSocketAddress.of(socketFile));
            CompletableFuture<Void> serving = CompletableFuture.runAsync(() -> serveOnce(server));
            overUnix = sendRequest(UnixDomainSocketAddress.of(socketFile), StandardProtocolFamily.UNIX);
            serving.join();
        } finally {
            Files.deleteIfExists(socketFile);
        }

        assertThat(overTcp).startsWith("HTTP/1.1 200 OK");
        assertThat(overUnix).isEqualTo(overTcp);
        CiOutput.record(LESSON, "two-transports", "[TCP 소켓 127.0.0.1로 보낸 요청의 응답]\n"
                + overTcp.replace("\r\n", "\n")
                + "\n\n[유닉스 도메인 소켓 shop.sock으로 보낸 요청의 응답]\n" + overUnix.replace("\r\n", "\n")
                + "\n\n두 응답이 같은가: " + overUnix.equals(overTcp));
    }

    @RestController
    static class OrderApi {
        @GetMapping("/whoami")
        String who() {
            return "주문 서버";
        }
    }

    @RestController
    static class PayApi {
        @GetMapping("/whoami")
        String who() {
            return "결제 서버";
        }
    }

    @Test
    @DisplayName("같은 IP에서 포트가 다른 서버 둘은 각자 다른 프로그램이다")
    void portsTellProgramsApart() throws Exception {
        try (MiniWeb order = MiniWeb.start(OrderApi.class); MiniWeb pay = MiniWeb.start(PayApi.class)) {
            String a = order.get("/whoami").body();
            String b = pay.get("/whoami").body();

            assertThat(a).isEqualTo("주문 서버");
            assertThat(b).isEqualTo("결제 서버");
            CiOutput.record(LESSON, "ports", String.join("\n",
                    "GET http://127.0.0.1:" + order.port() + "/whoami → " + a,
                    "GET http://127.0.0.1:" + pay.port() + "/whoami → " + b));
        }
    }

    @Test
    @DisplayName("같은 서버 포트로 연 연결 둘은 도착 IP와 포트가 같고 출발 포트가 다르다")
    void connectionIsFourTuple() throws Exception {
        try (ServerSocket server = new ServerSocket(0);
             Socket c1 = new Socket("127.0.0.1", server.getLocalPort());
             Socket c2 = new Socket("127.0.0.1", server.getLocalPort());
             Socket s1 = server.accept();
             Socket s2 = server.accept()) {

            assertThat(c1.getPort()).isEqualTo(c2.getPort());
            assertThat(c1.getLocalPort()).isNotEqualTo(c2.getLocalPort());
            assertThat(s1.getPort() == c1.getLocalPort() || s1.getPort() == c2.getLocalPort()).isTrue();
            CiOutput.record(LESSON, "four-tuple", String.join("\n",
                    "연결 1: " + tuple(c1),
                    "연결 2: " + tuple(c2),
                    "(서버가 본 상대 포트: " + s1.getPort() + ", " + s2.getPort() + ")"));
        }
    }

    private static String tuple(Socket s) {
        return s.getLocalAddress().getHostAddress() + ":" + s.getLocalPort()
                + " → " + s.getInetAddress().getHostAddress() + ":" + s.getPort();
    }

    @Test
    @DisplayName("1MB를 한 번에 써도 받는 쪽은 여러 번 나눠 읽고, 받은 바이트는 보낸 바이트와 같다")
    void bigWriteArrivesInPieces() throws Exception {
        byte[] data = new byte[1024 * 1024];
        new Random(7).nextBytes(data);
        int reads = 0;
        ByteArrayOutputStream got = new ByteArrayOutputStream();
        try (ServerSocket server = new ServerSocket(0);
             Socket client = new Socket("127.0.0.1", server.getLocalPort());
             Socket accepted = server.accept()) {
            CompletableFuture<Void> writing = CompletableFuture.runAsync(() -> {
                try {
                    OutputStream out = client.getOutputStream();
                    out.write(data);
                    out.flush();
                    client.shutdownOutput();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            InputStream in = accepted.getInputStream();
            byte[] buf = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                reads++;
                got.write(buf, 0, n);
            }
            writing.join();
        }

        assertThat(Arrays.equals(got.toByteArray(), data)).isTrue();
        assertThat(reads).isGreaterThan(1);
        CiOutput.record(LESSON, "pieces", String.join("\n",
                "write 1번으로 보낸 양: " + data.length + "바이트",
                "받는 쪽 read 횟수: " + reads + "번",
                "받은 양: " + got.size() + "바이트",
                "보낸 바이트와 같은가: true (헤더 없이 보낸 것만 왔다)"));
    }

    @RestController
    static class IpApi {
        @GetMapping("/ip")
        String ip(HttpServletRequest request) {
            return "getRemoteAddr()=" + request.getRemoteAddr()
                    + ", X-Forwarded-For=" + request.getHeader("X-Forwarded-For");
        }
    }

    @Test
    @DisplayName("L7 로드 밸런서가 X-Forwarded-For로 넘긴 IP를 스프링은 기본으로 쓰지 않고, forward-headers-strategy를 켜면 쓴다")
    void forwardedForNeedsSetting() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        String plain;
        String withStrategy;
        try (MiniWeb web = MiniWeb.start(IpApi.class)) {
            plain = http.send(forwarded(web.port()), HttpResponse.BodyHandlers.ofString()).body();
        }
        try (MiniWeb web = MiniWeb.startWith(new String[] {"server.forward-headers-strategy=framework"},
                IpApi.class)) {
            withStrategy = http.send(forwarded(web.port()), HttpResponse.BodyHandlers.ofString()).body();
        }

        assertThat(plain).startsWith("getRemoteAddr()=127.0.0.1");
        assertThat(withStrategy).startsWith("getRemoteAddr()=203.0.113.7");
        CiOutput.record(LESSON, "forwarded", String.join("\n",
                "[기본 설정] " + plain,
                "[forward-headers-strategy=framework] " + withStrategy));
    }

    private static HttpRequest forwarded(int port) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/ip"))
                .header("X-Forwarded-For", "203.0.113.7")
                .build();
    }
}
