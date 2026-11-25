package lab.devlessons.monolith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 레슨 "모놀리스와 마이크로서비스"의 주장을 검증한다.
 * 주문 DB는 PostgreSQL, 재고 서버는 내장 톰캣으로 띄운 작은 스프링 앱이다.
 * 스프링 부트 테스트 컨텍스트 없이 DataSource와 트랜잭션 매니저를 직접 만든다.
 */
class MonolithMsaTest {

    static final String LESSON = "monolith-msa";

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;

    @BeforeAll
    static void start() {
        PG.start();
        DriverManagerDataSource ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("create table orders (id bigserial primary key, item_id bigint, qty int)");
        jdbc.execute("create table stock (item_id bigint primary key, qty int)");
    }

    @AfterAll
    static void stop() {
        PG.stop();
    }

    @BeforeEach
    void reset() {
        jdbc.execute("truncate orders");
        jdbc.execute("delete from stock");
        jdbc.update("insert into stock values (1, 10)");
        StockController.QTY.set(10);
        StockController.delayMillis = 0;
    }

    /** 다른 서버의 재고. 자기 메모리(재고 서버의 DB 대신)에 둔다 */
    @RestController
    static class StockController {
        static final AtomicInteger QTY = new AtomicInteger(10);
        static volatile long delayMillis = 0;

        @PostMapping("/stock/{id}/decrease")
        int decrease(@PathVariable long id, @RequestBody Map<String, Integer> body) throws InterruptedException {
            Thread.sleep(delayMillis);
            return QTY.addAndGet(-body.get("qty"));
        }
    }

    static int port(MiniWeb web) {
        return ((ServletWebServerApplicationContext) web.context()).getWebServer().getPort();
    }

    // ── 모놀리스: 같은 DB, 메서드 호출 ──

    void decreaseLocal(long itemId, int qty) {
        jdbc.update("update stock set qty = qty - ? where item_id = ?", qty, itemId);
    }

    @Test
    @DisplayName("같은 애플리케이션과 같은 DB에서는 주문 저장과 재고 차감 뒤 예외가 나면 둘 다 롤백된다")
    void localTransactionRollsBackBoth() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            jdbc.update("insert into orders(item_id, qty) values (1, 2)");
            decreaseLocal(1, 2);
            throw new IllegalStateException("결제 실패");
        })).isInstanceOf(IllegalStateException.class);

        int orders = jdbc.queryForObject("select count(*) from orders", Integer.class);
        int stock = jdbc.queryForObject("select qty from stock where item_id = 1", Integer.class);
        assertThat(orders).isZero();
        assertThat(stock).isEqualTo(10);
        CiOutput.record(LESSON, "local", "[모놀리스: 주문 저장 → 재고 차감(메서드) → 결제 실패]\n"
                + "주문: " + orders + "건\n재고: " + stock + " (처음 10, 둘 다 롤백)");
    }

    @Test
    @DisplayName("재고를 HTTP로 부른 뒤 주문 서버가 롤백해도 재고 서버의 재고는 줄어든 채 남는다")
    void remoteCallIsNotRolledBack() {
        try (MiniWeb stockServer = MiniWeb.start(StockController.class)) {
            RestClient rest = RestClient.create("http://localhost:" + port(stockServer));

            assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
                jdbc.update("insert into orders(item_id, qty) values (1, 2)");
                rest.post().uri("/stock/{id}/decrease", 1)
                        .body(Map.of("qty", 2)).retrieve().toBodilessEntity();
                throw new IllegalStateException("결제 실패");
            })).isInstanceOf(IllegalStateException.class);

            int orders = jdbc.queryForObject("select count(*) from orders", Integer.class);
            assertThat(orders).isZero();
            assertThat(StockController.QTY.get()).isEqualTo(8);
            CiOutput.record(LESSON, "remote", "[마이크로서비스: 주문 저장 → 재고 차감(HTTP) → 결제 실패]\n"
                    + "주문 서버의 주문: " + orders + "건 (롤백)\n"
                    + "재고 서버의 재고: " + StockController.QTY.get() + " (처음 10, 줄어든 채 남음)");
        }
    }

    @Test
    @DisplayName("HTTP 호출은 메서드 호출보다 훨씬 느리다 (각각 1,000번)")
    void remoteCallIsSlower() {
        try (MiniWeb stockServer = MiniWeb.start(StockController.class)) {
            RestClient rest = RestClient.create("http://localhost:" + port(stockServer));
            StockService local = new StockService();
            for (int i = 0; i < 200; i++) { // 데우기
                local.decrease(1, 0);
                rest.post().uri("/stock/{id}/decrease", 1).body(Map.of("qty", 0)).retrieve().body(Integer.class);
            }

            long t0 = System.nanoTime();
            for (int i = 0; i < 1_000; i++) {
                local.decrease(1, 0);
            }
            long localMicros = (System.nanoTime() - t0) / 1_000;
            long t1 = System.nanoTime();
            for (int i = 0; i < 1_000; i++) {
                rest.post().uri("/stock/{id}/decrease", 1).body(Map.of("qty", 0)).retrieve().body(Integer.class);
            }
            long remoteMicros = (System.nanoTime() - t1) / 1_000;

            assertThat(remoteMicros).isGreaterThan(localMicros * 10);
            CiOutput.record(LESSON, "speed", "[재고 차감 1,000번, 같은 컴퓨터 안]\n"
                    + "메서드 호출: " + Math.max(1, localMicros / 1_000) + "ms 안쪽 (" + localMicros + "μs)\n"
                    + "HTTP 호출: " + remoteMicros / 1_000 + "ms");
        }
    }

    static class StockService {
        final AtomicInteger qty = new AtomicInteger(10);

        int decrease(long itemId, int n) {
            return qty.addAndGet(-n);
        }
    }

    @Test
    @DisplayName("재고 서버가 꺼져 있으면 호출이 예외로 실패하고, 응답이 늦으면 부르는 쪽도 그만큼 늦다")
    void downOrSlowServerSpreads() {
        int port;
        String slow;
        try (MiniWeb stockServer = MiniWeb.start(StockController.class)) {
            port = port(stockServer);
            RestClient rest = RestClient.create("http://localhost:" + port);
            StockController.delayMillis = 1_500;
            long t0 = System.nanoTime();
            rest.post().uri("/stock/{id}/decrease", 1).body(Map.of("qty", 1)).retrieve().body(Integer.class);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            assertThat(ms).isGreaterThanOrEqualTo(1_500);
            slow = "재고 서버가 1.5초 늦게 응답 → 주문 쪽 호출 " + ms + "ms";
        }
        RestClient rest = RestClient.create("http://localhost:" + port);
        String down;
        try {
            rest.post().uri("/stock/{id}/decrease", 1).body(Map.of("qty", 1)).retrieve().body(Integer.class);
            down = "성공";
        } catch (RuntimeException e) {
            down = e.getClass().getSimpleName();
        }
        assertThat(down).isEqualTo("ResourceAccessException");
        CiOutput.record(LESSON, "spread", slow + "\n재고 서버가 꺼짐 → " + down);
    }

    @Test
    @DisplayName("읽기 타임아웃을 준 RestClient는 늦은 응답에서 타임아웃 예외를 던지고, 기본 설정은 끝까지 기다린다")
    void readTimeout() {
        try (MiniWeb stockServer = MiniWeb.start(StockController.class)) {
            String base = "http://localhost:" + port(stockServer);
            StockController.delayMillis = 2_000;

            RestClient plain = RestClient.create(base);
            long t0 = System.nanoTime();
            plain.post().uri("/stock/{id}/decrease", 1).body(Map.of("qty", 1)).retrieve().body(Integer.class);
            long plainMs = (System.nanoTime() - t0) / 1_000_000;

            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(1_000);
            factory.setReadTimeout(500);
            RestClient guarded = RestClient.builder().baseUrl(base).requestFactory(factory).build();
            long t1 = System.nanoTime();
            String failure;
            try {
                guarded.post().uri("/stock/{id}/decrease", 1).body(Map.of("qty", 1)).retrieve().body(Integer.class);
                failure = "받음";
            } catch (RestClientException e) {
                Throwable root = e;
                while (root.getCause() != null) {
                    root = root.getCause();
                }
                failure = e.getClass().getSimpleName() + " (원인: " + root.getClass().getSimpleName() + ")";
                assertThat(root).isInstanceOf(SocketTimeoutException.class);
            }
            long guardedMs = (System.nanoTime() - t1) / 1_000_000;

            assertThat(plainMs).isGreaterThanOrEqualTo(2_000);
            assertThat(guardedMs).isLessThan(1_500);
            CiOutput.record(LESSON, "timeout", "[재고 서버가 2초 늦게 응답]\n"
                    + "기본 RestClient: " + plainMs + "ms 기다려 받음\n"
                    + "읽기 타임아웃 0.5초: " + guardedMs + "ms 만에 " + failure);
        }
    }
}
