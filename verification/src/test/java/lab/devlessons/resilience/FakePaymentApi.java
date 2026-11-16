package lab.devlessons.resilience;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 외부 결제 API(PG사) 대신 띄우는 진짜 HTTP 서버. 응답 지연과 실패를 바꿀 수 있다.
 * Idempotency-Key 헤더가 있으면 같은 키는 한 번만 처리한다.
 */
class FakePaymentApi implements AutoCloseable {

    private final HttpServer server;
    /** 실제로 결제를 처리한 횟수 (돈이 빠져나간 횟수) */
    final AtomicInteger processed = new AtomicInteger();
    /** 서버에 도착한 요청 수 */
    final AtomicInteger received = new AtomicInteger();
    private final Set<String> seenKeys = ConcurrentHashMap.newKeySet();
    volatile long delayMillis;
    volatile boolean failing;

    FakePaymentApi() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/pay", exchange -> {
            received.incrementAndGet();
            String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
            int status;
            if (failing) {
                status = 500;
            } else {
                if (key == null || seenKeys.add(key)) {
                    processed.incrementAndGet(); // 결제는 응답을 보내기 전에 이미 처리된다
                }
                status = 200;
            }
            sleep(delayMillis);
            byte[] body = (status == 200 ? "OK" : "ERROR").getBytes(StandardCharsets.UTF_8);
            try {
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
            } catch (IOException ignored) {
                // 클라이언트가 타임아웃으로 먼저 끊었다
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/pay";
    }

    void reset() {
        processed.set(0);
        received.set(0);
        seenKeys.clear();
        delayMillis = 0;
        failing = false;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
