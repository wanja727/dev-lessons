package lab.devlessons.httpstls;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 레슨 "HTTPS와 TLS"의 주장을 검증한다.
 * keytool로 인증서를 만들어 TLS 서버를 띄우고, 클라이언트와 서버 사이에 바이트를 기록하는 중계기를 둔다.
 * TLS의 메시지(ClientHello 등)는 자바 코드에 보이지 않아 RFC 8446을 근거로 하고, 여기서는 결과만 확인한다.
 */
class HttpsTlsTest {

    static final String LESSON = "https-tls";
    static final char[] PASS = "changeit".toCharArray();
    static final String LOGIN_JSON = "{\"user\":\"kim\",\"password\":\"s3cret!\"}";

    static Path dir;
    static SSLContext serverCtx;      // CN=localhost 인증서
    static SSLContext otherServerCtx; // CN=shop.example.com 인증서
    static SSLContext trustShop;      // localhost 인증서만 믿는 클라이언트
    static SSLContext trustOther;     // shop.example.com 인증서만 믿는 클라이언트
    static final List<AutoCloseable> closers = new ArrayList<>();

    @BeforeAll
    static void makeCertificates() throws Exception {
        dir = Files.createTempDirectory("tls");
        keytool("-genkeypair", "-alias", "shop", "-keyalg", "EC", "-validity", "1",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost",
                "-keystore", dir.resolve("server.p12").toString(), "-storetype", "PKCS12",
                "-storepass", "changeit", "-keypass", "changeit");
        keytool("-genkeypair", "-alias", "shop", "-keyalg", "EC", "-validity", "1",
                "-dname", "CN=shop.example.com", "-ext", "SAN=dns:shop.example.com",
                "-keystore", dir.resolve("other.p12").toString(), "-storetype", "PKCS12",
                "-storepass", "changeit", "-keypass", "changeit");
        serverCtx = serverContext(dir.resolve("server.p12"));
        otherServerCtx = serverContext(dir.resolve("other.p12"));
        trustShop = trustOnly(certOf(dir.resolve("server.p12")));
        trustOther = trustOnly(certOf(dir.resolve("other.p12")));
    }

    @AfterAll
    static void closeAll() throws Exception {
        for (AutoCloseable c : closers) {
            c.close();
        }
    }

    static void keytool(String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), UTF_8);
        if (p.waitFor() != 0) {
            throw new IllegalStateException("keytool 실패: " + out);
        }
    }

    static SSLContext serverContext(Path p12) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(p12.toFile())) {
            ks.load(in, PASS);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, PASS);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }

    static Certificate certOf(Path p12) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(p12.toFile())) {
            ks.load(in, PASS);
        }
        byte[] der = ks.getCertificate("shop").getEncoded();
        return CertificateFactory.getInstance("X.509").generateCertificate(new java.io.ByteArrayInputStream(der));
    }

    /** 이 인증서 하나만 믿는 클라이언트 SSLContext (레슨의 trustOnly) */
    static SSLContext trustOnly(Certificate cert) throws Exception {
        KeyStore ts = KeyStore.getInstance("PKCS12");
        ts.load(null, null);
        ts.setCertificateEntry("shop", cert);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tmf.getTrustManagers(), null);
        return ctx;
    }

    /** 요청 하나를 읽고 "OK"로 답하는 서버. ssl이 null이면 평문 HTTP */
    static int startServer(SSLContext ssl) throws Exception {
        ServerSocket server = ssl == null ? new ServerSocket(0)
                : ssl.getServerSocketFactory().createServerSocket(0);
        closers.add(server);
        Thread t = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket s = server.accept()) {
                    InputStream in = s.getInputStream();
                    ByteArrayOutputStream got = new ByteArrayOutputStream();
                    int b;
                    while ((b = in.read()) != -1) {
                        got.write(b);
                        if (got.toString(ISO_8859_1).endsWith("\r\n\r\n")) {
                            break;
                        }
                    }
                    String head = got.toString(ISO_8859_1);
                    int len = 0;
                    for (String line : head.split("\r\n")) {
                        if (line.toLowerCase().startsWith("content-length:")) {
                            len = Integer.parseInt(line.substring(15).trim());
                        }
                    }
                    in.readNBytes(len);
                    s.getOutputStream().write(
                            "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK".getBytes(UTF_8));
                } catch (Exception ignored) {
                    // 핸드셰이크 실패 등은 클라이언트 쪽에서 본다
                }
            }
        });
        t.setDaemon(true);
        t.start();
        return server.getLocalPort();
    }

    /** 클라이언트가 보낸 바이트를 기록하며 서버로 넘기는 TCP 중계기 */
    static int startRelay(int target, ByteArrayOutputStream recorded) throws Exception {
        ServerSocket relay = new ServerSocket(0);
        closers.add(relay);
        Thread t = new Thread(() -> {
            try (Socket client = relay.accept(); Socket upstream = new Socket("localhost", target)) {
                CompletableFuture<Void> back = CompletableFuture.runAsync(
                        () -> pipe(upstream, client, null));
                pipe(client, upstream, recorded);
                back.join();
            } catch (Exception ignored) {
                // 연결이 끝남
            }
        });
        t.setDaemon(true);
        t.start();
        return relay.getLocalPort();
    }

    static void pipe(Socket from, Socket to, ByteArrayOutputStream recorded) {
        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                if (recorded != null) {
                    synchronized (recorded) {
                        recorded.write(buf, 0, n);
                    }
                }
                out.write(buf, 0, n);
                out.flush();
            }
            to.shutdownOutput();
        } catch (Exception ignored) {
            // 한쪽이 닫힘
        }
    }

    static String loginRequest() {
        return "POST /login HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\n"
                + "Content-Length: " + LOGIN_JSON.getBytes(UTF_8).length + "\r\nConnection: close\r\n\r\n" + LOGIN_JSON;
    }

    static String exchange(Socket socket) throws Exception {
        socket.getOutputStream().write(loginRequest().getBytes(UTF_8));
        socket.getOutputStream().flush();
        return new String(socket.getInputStream().readAllBytes(), UTF_8);
    }

    static SSLSocket tlsSocket(SSLContext trust, int port) throws Exception {
        SSLSocket s = (SSLSocket) trust.getSocketFactory().createSocket("localhost", port);
        SSLParameters params = s.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS");
        s.setSSLParameters(params);
        return s;
    }

    static String printable(byte[] bytes, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(bytes.length, max); i++) {
            int c = bytes[i] & 0xff;
            sb.append(c >= 0x20 && c < 0x7f ? String.valueOf((char) c) : ".");
        }
        return sb.toString();
    }

    @Test
    @DisplayName("HTTP로 보낸 바이트에는 비밀번호가 그대로 있고, HTTPS로 보낸 바이트에는 없다")
    void eavesdropper() throws Exception {
        ByteArrayOutputStream plainBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream tlsBytes = new ByteArrayOutputStream();
        int plainRelay = startRelay(startServer(null), plainBytes);
        int tlsRelay = startRelay(startServer(serverCtx), tlsBytes);

        String plainReply;
        try (Socket s = new Socket("localhost", plainRelay)) {
            plainReply = exchange(s);
        }
        String tlsReply;
        try (SSLSocket s = tlsSocket(trustShop, tlsRelay)) {
            tlsReply = exchange(s);
        }
        Thread.sleep(200);
        String plainSeen;
        String tlsSeen;
        synchronized (plainBytes) {
            plainSeen = plainBytes.toString(UTF_8);
        }
        byte[] tlsRaw;
        synchronized (tlsBytes) {
            tlsRaw = tlsBytes.toByteArray();
            tlsSeen = new String(tlsRaw, ISO_8859_1);
        }

        assertThat(plainReply).endsWith("OK");
        assertThat(tlsReply).endsWith("OK");
        assertThat(plainSeen).contains("s3cret!");
        assertThat(tlsSeen).doesNotContain("s3cret!").doesNotContain("password").doesNotContain("POST");
        CiOutput.record(LESSON, "sniff", String.join("\n",
                "[HTTP] 중계기가 본 바이트 " + plainSeen.length() + "개:",
                plainSeen.replace("\r\n", "\n").strip(),
                "→ 's3cret!'이 보이나: " + plainSeen.contains("s3cret!"),
                "",
                "[HTTPS] 중계기가 본 바이트 " + tlsRaw.length + "개 (앞 120개, 글자가 아닌 바이트는 .):",
                printable(tlsRaw, 120),
                "→ 's3cret!'이 보이나: " + tlsSeen.contains("s3cret!")));
    }

    @Test
    @DisplayName("자바 21 클라이언트와 서버는 TLS 1.3과 AES GCM 묶음으로 연결한다")
    void negotiatedProtocol() throws Exception {
        int port = startServer(serverCtx);
        String protocol;
        String suite;
        try (SSLSocket s = tlsSocket(trustShop, port)) {
            s.startHandshake();
            protocol = s.getSession().getProtocol();
            suite = s.getSession().getCipherSuite();
            exchange(s);
        }

        assertThat(protocol).isEqualTo("TLSv1.3");
        assertThat(suite).contains("AES").contains("GCM");
        CiOutput.record(LESSON, "suite", "프로토콜: " + protocol + "\n암호화 묶음: " + suite);
    }

    static String tryHttps(HttpClient client, int port) {
        try {
            HttpResponse<String> res = client.send(
                    HttpRequest.newBuilder(URI.create("https://localhost:" + port + "/")).build(),
                    HttpResponse.BodyHandlers.ofString());
            return res.statusCode() + " " + res.body();
        } catch (Exception e) {
            Throwable t = e;
            String first = e.getClass().getSimpleName() + ": " + e.getMessage();
            while (t.getCause() != null) {
                t = t.getCause();
            }
            return first.equals(t.getClass().getSimpleName() + ": " + t.getMessage()) ? first
                    : first + "\n  원인: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    @Test
    @DisplayName("자기 서명 인증서는 기본 신뢰 저장소에서 PKIX로 거부되고, 그 인증서를 믿게 하면 연결되고, 도메인이 다르면 거부된다")
    void certificateChecks() throws Exception {
        int shop = startServer(serverCtx);
        int other = startServer(otherServerCtx);
        HttpClient plainClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        HttpClient trustingShop = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .sslContext(trustShop).build();
        HttpClient trustingOther = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .sslContext(trustOther).build();

        String untrusted = tryHttps(plainClient, shop);
        String trusted = tryHttps(trustingShop, shop);
        String wrongName = tryHttps(trustingOther, other);

        assertThat(untrusted).contains("SSLHandshakeException").contains("PKIX path building failed");
        assertThat(trusted).isEqualTo("200 OK");
        assertThat(wrongName).contains("SSLHandshakeException").contains("localhost");
        CiOutput.record(LESSON, "certs", String.join("\n",
                "1) 기본 신뢰 저장소(cacerts)로 CN=localhost 서버에:",
                "  " + untrusted,
                "",
                "2) 그 인증서를 믿게 한 클라이언트로 같은 서버에:",
                "  " + trusted,
                "",
                "3) shop.example.com 인증서를 믿게 하고, 그 인증서를 쓰는 서버에 localhost로:",
                "  " + wrongName));
    }

    @RestController
    static class SchemeApi {
        @GetMapping("/scheme")
        String scheme(HttpServletRequest request) {
            return "getScheme()=" + request.getScheme() + ", isSecure()=" + request.isSecure();
        }
    }

    @Test
    @DisplayName("로드 밸런서가 보낸 X-Forwarded-Proto: https를 스프링은 기본으로 읽지 않고, forward-headers-strategy=framework면 https가 된다")
    void forwardedProto() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        String plain;
        String framework;
        try (MiniWeb web = MiniWeb.start(SchemeApi.class)) {
            plain = http.send(proto(web.port()), HttpResponse.BodyHandlers.ofString()).body();
        }
        try (MiniWeb web = MiniWeb.startWith(new String[] {"server.forward-headers-strategy=framework"},
                SchemeApi.class)) {
            framework = http.send(proto(web.port()), HttpResponse.BodyHandlers.ofString()).body();
        }

        assertThat(plain).startsWith("getScheme()=http,");
        assertThat(framework).startsWith("getScheme()=https,");
        CiOutput.record(LESSON, "forwarded", "[기본 설정] " + plain + "\n[forward-headers-strategy=framework] " + framework);
    }

    static HttpRequest proto(int port) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/scheme"))
                .header("X-Forwarded-Proto", "https").build();
    }
}
