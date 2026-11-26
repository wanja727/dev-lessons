package lab.devlessons.hexagonal;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lab.devlessons.hexagonal.domain.Order;
import lab.devlessons.hexagonal.domain.OrderStore;
import lab.devlessons.hexagonal.domain.PaymentPort;
import lab.devlessons.hexagonal.domain.PlaceOrder;
import lab.devlessons.hexagonal.domain.PlaceOrderService;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 레슨 "헥사고날 아키텍처"의 주장을 검증한다. 도메인은 domain 패키지, 어댑터는 이 테스트의 중첩 클래스다. */
class HexagonalTest {

    static final String LESSON = "hexagonal";

    // ── 나가는 어댑터 ──

    /** 메모리에 담는 저장 어댑터 */
    static class MemoryOrderStore implements OrderStore {
        final Map<String, Order> rows = new HashMap<>();

        public void save(Order order) {
            rows.put(order.id(), order);
        }

        public Order find(String id) {
            return rows.get(id);
        }
    }

    /** 테스트용 가짜 결제 어댑터 */
    static class FakePayment implements PaymentPort {
        final List<String> calls = new ArrayList<>();

        public boolean pay(String orderId, long amount) {
            calls.add(orderId + ":" + amount);
            return true;
        }
    }

    /** 진짜 HTTP로 PG를 부르는 결제 어댑터 */
    static class HttpPayment implements PaymentPort {
        final String base;
        final HttpClient http = HttpClient.newHttpClient();

        HttpPayment(String base) {
            this.base = base;
        }

        public boolean pay(String orderId, long amount) {
            try {
                HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(base + "/pay/" + orderId + "/" + amount))
                        .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
                return "approved".equals(res.body());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** 바깥의 PG 서버 */
    @RestController
    static class FakePgServer {
        @PostMapping("/pay/{orderId}/{amount}")
        String pay(@PathVariable String orderId, @PathVariable long amount) {
            return amount <= 100_000 ? "approved" : "declined";
        }
    }

    // ── 들어오는 어댑터: 웹 ──

    @Configuration
    static class WebApp {
        @Bean
        PlaceOrder placeOrder() {
            return new PlaceOrderService(new MemoryOrderStore(), new FakePayment());
        }

        @Bean
        OrderController orderController(PlaceOrder placeOrder) {
            return new OrderController(placeOrder);
        }
    }

    @RestController
    static class OrderController {
        private final PlaceOrder placeOrder;

        OrderController(PlaceOrder placeOrder) {
            this.placeOrder = placeOrder;
        }

        @PostMapping("/orders/{id}/{amount}")
        String place(@PathVariable String id, @PathVariable long amount) {
            return placeOrder.place(id, amount).status();
        }
    }

    @Test
    @DisplayName("같은 유스케이스에 결제 어댑터를 가짜에서 HTTP로 바꿔 끼워도 도메인 코드는 그대로이고 결과가 같다")
    void swapOutgoingAdapter() {
        MemoryOrderStore store1 = new MemoryOrderStore();
        FakePayment fake = new FakePayment();
        String withFake = new PlaceOrderService(store1, fake).place("o-1", 30_000).status();

        String withHttp;
        try (MiniWeb pg = MiniWeb.start(FakePgServer.class)) {
            int port = ((ServletWebServerApplicationContext) pg.context()).getWebServer().getPort();
            MemoryOrderStore store2 = new MemoryOrderStore();
            withHttp = new PlaceOrderService(store2, new HttpPayment("http://localhost:" + port)).place("o-1", 30_000).status();
        }

        assertThat(withFake).isEqualTo("PAID");
        assertThat(withHttp).isEqualTo("PAID");
        CiOutput.record(LESSON, "swap", "[PlaceOrderService.place(\"o-1\", 30000), 도메인 코드는 그대로]\n"
                + "결제 어댑터 FakePayment: " + withFake + " (가짜가 받은 호출 " + fake.calls + ")\n"
                + "결제 어댑터 HttpPayment(내장 톰캣 PG): " + withHttp);
    }

    @Test
    @DisplayName("같은 유스케이스를 웹 컨트롤러와 배치(코드에서 바로)가 각자 들어오는 어댑터로 부른다")
    void twoIncomingAdapters() throws Exception {
        String web;
        try (MiniWeb app = MiniWeb.start(WebApp.class)) {
            int port = ((ServletWebServerApplicationContext) app.context()).getWebServer().getPort();
            web = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/orders/o-2/5000"))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).body();
        }
        PlaceOrder useCase = new PlaceOrderService(new MemoryOrderStore(), new FakePayment());
        String batch = useCase.place("o-3", 7000).status();

        assertThat(web).isEqualTo("PAID");
        assertThat(batch).isEqualTo("PAID");
        CiOutput.record(LESSON, "incoming", "[같은 PlaceOrder 유스케이스]\n"
                + "웹 어댑터 POST /orders/o-2/5000 → " + web + "\n"
                + "배치 어댑터 useCase.place(\"o-3\", 7000) → " + batch);
    }

    private static String classBytes(Class<?> type) throws IOException {
        String resource = type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getClassLoader().getResourceAsStream(resource)) {
            return new String(in.readAllBytes(), ISO_8859_1);
        }
    }

    @Test
    @DisplayName("도메인 패키지의 클래스는 스프링, 서블릿, JPA, HTTP 클라이언트를 참조하지 않는다. 어댑터는 참조한다")
    void domainHasNoFrameworkReferences() throws IOException {
        List<Class<?>> domain = List.of(Order.class, OrderStore.class, PaymentPort.class, PlaceOrder.class, PlaceOrderService.class);
        String[] outside = {"org/springframework", "jakarta/", "java/net/http"};
        List<String> lines = new ArrayList<>();
        for (Class<?> c : domain) {
            String bytes = classBytes(c);
            for (String o : outside) {
                assertThat(bytes).doesNotContain(o);
            }
            lines.add(c.getSimpleName() + ": 바깥 기술 참조 없음");
        }
        assertThat(classBytes(HttpPayment.class)).contains("java/net/http");
        assertThat(classBytes(OrderController.class)).contains("org/springframework/web");
        lines.add("HttpPayment(어댑터): java.net.http 참조");
        lines.add("OrderController(어댑터): org.springframework.web 참조");
        CiOutput.record(LESSON, "deps", "[클래스 파일이 참조하는 바깥 기술]\n" + String.join("\n", lines));
    }

    @Test
    @DisplayName("도메인 규칙은 어댑터 없이 바로 시험한다: 금액이 0 이하면 주문을 만들 수 없다")
    void domainRuleWithoutAdapters() {
        String result;
        try {
            new Order("o-4", 0);
            result = "만들어짐";
        } catch (IllegalArgumentException e) {
            result = "IllegalArgumentException: " + e.getMessage();
        }
        assertThat(result).startsWith("IllegalArgumentException");
    }
}
