package lab.devlessons.tcpudp;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 레슨 "TCP와 UDP"의 주장을 검증한다.
 * SYN, ACK 같은 패킷은 자바에서 보이지 않는다. 자바 소켓에 드러나는 결과(연결이 맺어지는 때, 읽히는 모양, 끝)를 확인한다.
 */
class TcpUdpTest {

    static final String LESSON = "tcp-udp";

    @Test
    @DisplayName("서버가 accept를 부르지 않아도 클라이언트의 연결은 맺어지고 보낸 바이트도 받아 둔다")
    void connectWithoutAccept() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            Socket client = new Socket("localhost", server.getLocalPort());
            boolean connected = client.isConnected();
            client.getOutputStream().write("PAY:1000".getBytes(UTF_8));

            // 이제야 accept로 꺼낸다
            try (client; Socket accepted = server.accept()) {
                byte[] got = accepted.getInputStream().readNBytes(8);

                assertThat(connected).isTrue();
                assertThat(new String(got, UTF_8)).isEqualTo("PAY:1000");
                CiOutput.record(LESSON, "no-accept", String.join("\n",
                        "accept 전 client.isConnected() = " + connected,
                        "accept 전에 보낸 바이트도 받아 둠: " + new String(got, UTF_8),
                        "(연결: " + client.getLocalPort() + " → " + server.getLocalPort() + ")"));
            }
        }
    }

    @Test
    @DisplayName("아무도 듣지 않는 포트에 연결하면 ConnectException(Connection refused)이 바로 난다")
    void refusedImmediately() throws Exception {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        long t0 = System.nanoTime();
        Throwable error = null;
        try (Socket ignored = new Socket("127.0.0.1", closedPort)) {
            // 오지 않는다
        } catch (Exception e) {
            error = e;
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;

        assertThat(error).isInstanceOf(ConnectException.class);
        CiOutput.record(LESSON, "refused", error.getClass().getSimpleName() + ": " + error.getMessage()
                + "\n걸린 시간: " + ms + "ms");
    }

    @Test
    @DisplayName("두 번 쓴 바이트를 받는 쪽이 read 한 번으로 함께 읽을 수 있다")
    void noMessageBoundary() throws Exception {
        try (ServerSocket server = new ServerSocket(0);
             Socket client = new Socket("localhost", server.getLocalPort());
             Socket accepted = server.accept()) {
            OutputStream out = client.getOutputStream();
            out.write("PAY:1000".getBytes(UTF_8));
            out.flush();
            out.write("PAY:2000".getBytes(UTF_8));
            out.flush();
            Thread.sleep(300);

            byte[] buf = new byte[100];
            int n = accepted.getInputStream().read(buf);

            assertThat(n).isEqualTo(16);
            CiOutput.record(LESSON, "stream", "read 한 번이 돌려준 바이트: " + n + "\n읽은 내용: "
                    + new String(buf, 0, n, UTF_8));
        }
    }

    @Test
    @DisplayName("받는 쪽이 읽지 않으면 보내는 쪽의 write가 어느 순간 멈춘다")
    void writerBlocksWhenReceiverDoesNotRead() throws Exception {
        AtomicLong written = new AtomicLong();
        try (ServerSocket server = new ServerSocket(0);
             Socket client = new Socket("localhost", server.getLocalPort());
             Socket accepted = server.accept()) {
            Thread writer = new Thread(() -> {
                byte[] chunk = new byte[8 * 1024];
                try {
                    OutputStream out = client.getOutputStream();
                    while (true) {
                        out.write(chunk);
                        written.addAndGet(chunk.length);
                    }
                } catch (Exception closed) {
                    // 소켓을 닫으면 끝난다
                }
            });
            writer.setDaemon(true);
            writer.start();

            Thread.sleep(1500);
            long first = written.get();
            Thread.sleep(500);
            long second = written.get();

            assertThat(second).isEqualTo(first);
            assertThat(second).isGreaterThan(0).isLessThan(256L * 1024 * 1024);
            CiOutput.record(LESSON, "flow", String.join("\n",
                    "받는 쪽은 한 바이트도 읽지 않음",
                    "1.5초 뒤까지 보낸 양: " + first / 1024 + "KB",
                    "0.5초 더 기다린 뒤: " + second / 1024 + "KB (늘지 않음. write에서 멈춤)"));
        }
    }

    @Test
    @DisplayName("상대가 소켓을 닫으면 read가 -1을 돌려준다")
    void readReturnsMinusOneAfterPeerCloses() throws Exception {
        try (ServerSocket server = new ServerSocket(0);
             Socket client = new Socket("localhost", server.getLocalPort())) {
            Socket accepted = server.accept();
            accepted.getOutputStream().write("OK".getBytes(UTF_8));
            accepted.close();

            InputStream in = client.getInputStream();
            byte[] buf = new byte[10];
            int first = in.read(buf);
            int second = in.read(buf);

            assertThat(first).isEqualTo(2);
            assertThat(second).isEqualTo(-1);
            CiOutput.record(LESSON, "close", "첫 read: " + first + "바이트 (OK)\n상대가 닫은 뒤 read: " + second);
        }
    }

    @Test
    @DisplayName("UDP는 아무도 듣지 않는 포트로 보내도 send가 예외 없이 끝나고, 두 번 보내면 두 번에 나눠 받는다")
    void udpHasNoConnectionAndKeepsBoundaries() throws Exception {
        InetAddress local = InetAddress.getLoopbackAddress();
        int nobody;
        try (DatagramSocket s = new DatagramSocket(0, local)) {
            nobody = s.getLocalPort();
        }
        String sendResult;
        try (DatagramSocket udp = new DatagramSocket()) {
            byte[] data = "PAY:1000".getBytes(UTF_8);
            udp.send(new DatagramPacket(data, data.length, local, nobody));
            sendResult = "예외 없이 끝남";
        }

        List<String> received = new ArrayList<>();
        try (DatagramSocket receiver = new DatagramSocket(0, local); DatagramSocket sender = new DatagramSocket()) {
            receiver.setSoTimeout(2000);
            for (String msg : List.of("PAY:1000", "PAY:2000")) {
                byte[] b = msg.getBytes(UTF_8);
                sender.send(new DatagramPacket(b, b.length, local, receiver.getLocalPort()));
            }
            Thread.sleep(200);
            for (int i = 0; i < 2; i++) {
                DatagramPacket p = new DatagramPacket(new byte[100], 100);
                receiver.receive(p);
                received.add(p.getLength() + "바이트: " + new String(p.getData(), 0, p.getLength(), UTF_8));
            }
        }

        assertThat(received).containsExactly("8바이트: PAY:1000", "8바이트: PAY:2000");
        CiOutput.record(LESSON, "udp", String.join("\n",
                "아무도 듣지 않는 포트 " + nobody + "로 send → " + sendResult,
                "두 번 보낸 데이터그램을 받기:",
                "  receive 1: " + received.get(0),
                "  receive 2: " + received.get(1)));
    }

    @RestController
    static class PortApi {
        @GetMapping("/port")
        String port(HttpServletRequest request) {
            return String.valueOf(request.getRemotePort());
        }
    }

    @Test
    @DisplayName("요청마다 새 HttpClient를 만들면 서버가 본 클라이언트 포트가 매번 다르고, 하나를 함께 쓰면 같다")
    void sharedClientReusesConnection() throws Exception {
        try (MiniWeb web = MiniWeb.start(PortApi.class)) {
            HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + web.port() + "/port")).build();
            Set<String> eachNew = new LinkedHashSet<>();
            for (int i = 0; i < 5; i++) {
                HttpClient c = HttpClient.newHttpClient();
                eachNew.add(c.send(req, HttpResponse.BodyHandlers.ofString()).body());
            }
            HttpClient shared = HttpClient.newHttpClient();
            Set<String> sharedPorts = new LinkedHashSet<>();
            for (int i = 0; i < 5; i++) {
                sharedPorts.add(shared.send(req, HttpResponse.BodyHandlers.ofString()).body());
            }

            assertThat(eachNew).hasSize(5);
            assertThat(sharedPorts).hasSize(1);
            CiOutput.record(LESSON, "client-reuse", String.join("\n",
                    "요청마다 새 HttpClient: 서버가 본 포트 " + eachNew + " → 연결 " + eachNew.size() + "개",
                    "HttpClient 하나를 함께 씀: 서버가 본 포트 " + sharedPorts + " → 연결 " + sharedPorts.size() + "개"));
        }
    }
}
