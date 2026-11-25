package lab.devlessons.mvcflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** 레슨 "스프링 MVC 요청 흐름"(S6)의 주장을 컨트롤러 하나를 띄운 작은 웹 앱(MiniWeb)으로 확인한다. */
class MvcFlowTest {

    private static final String LESSON = "spring-mvc-flow";

    public enum OrderStatus { PAID, CANCELED }

    public record Order(long id, String menu, int quantity, OrderStatus status) {
    }

    public record CreateOrder(String menu, int quantity) {
    }

    /** 요청 본문을 엔티티처럼 받는 흔한 실수를 보이려는 클래스. 모든 필드에 세터가 있다 */
    public static class OrderEntity {
        private Long id;
        private String menu;
        private int quantity;
        private OrderStatus status = OrderStatus.CANCELED;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getMenu() { return menu; }
        public void setMenu(String menu) { this.menu = menu; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public OrderStatus getStatus() { return status; }
        public void setStatus(OrderStatus status) { this.status = status; }
    }

    static final AtomicInteger FIND_CALLS = new AtomicInteger();

    @RestController
    @RequestMapping("/orders")
    static class OrderController {

        @GetMapping("/{id}")
        Order find(@PathVariable long id) {
            FIND_CALLS.incrementAndGet();
            return new Order(id, "latte", 2, OrderStatus.PAID);
        }

        @GetMapping
        List<Order> list(@RequestParam OrderStatus status) {
            return List.of(new Order(1, "latte", 2, status));
        }

        @PostMapping
        ResponseEntity<Order> create(@RequestBody CreateOrder req) {
            Order o = new Order(42, req.menu(), req.quantity(), OrderStatus.PAID);
            return ResponseEntity.created(URI.create("/orders/" + o.id())).body(o);
        }

        @PostMapping("/echo")
        CreateOrder echo(@RequestBody CreateOrder req) {
            return req;
        }

        @PostMapping("/entity")
        OrderEntity entity(@RequestBody OrderEntity req) {
            return req;
        }
    }

    @RestController
    static class PingController {
        @GetMapping("/ping")
        String ping() {
            return "pong";
        }
    }

    private static MiniWeb web;

    @BeforeAll
    static void start() {
        web = MiniWeb.start(OrderController.class, PingController.class);
    }

    @AfterAll
    static void stop() {
        web.close();
    }

    private static HttpResponse<String> send(String method, String path, String json, String accept) {
        HttpRequest.Builder b = web.request(path);
        if (json != null) {
            b.header("Content-Type", "application/json");
        }
        if (accept != null) {
            b.header("Accept", accept);
        }
        b.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        return web.exchange(b);
    }

    private static String shortBody(String body) {
        return body.replaceAll("\"timestamp\":\"[^\"]*\",", "").replaceAll("\"timestamp\":\\d+,", "");
    }

    @Test
    @DisplayName("RequestMappingHandlerMapping의 표에 컨트롤러의 경로와 메서드가 있다")
    void mappingTable() {
        RequestMappingHandlerMapping mapping = web.context().getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        Map<String, String> table = new TreeMap<>();
        mapping.getHandlerMethods().forEach((info, method) -> {
            if (method.getBeanType().getName().contains("MvcFlowTest")) {
                table.put(info.toString(), method.getBeanType().getSimpleName() + "." + method.getMethod().getName());
            }
        });

        assertThat(table.keySet()).anyMatch(k -> k.contains("GET") && k.contains("/orders/{id}"));
        CiOutput.record(LESSON, "mappings", table.entrySet().stream()
                .map(e -> e.getKey() + " → " + e.getValue()).collect(Collectors.joining("\n")));
    }

    @Test
    @DisplayName("없는 경로는 404, 경로는 있는데 HTTP 메서드가 다르면 405")
    void notFoundAndMethodNotAllowed() {
        HttpResponse<String> nf = send("GET", "/payments", null, null);
        HttpResponse<String> na = send("PUT", "/orders/1", null, null);

        assertThat(nf.statusCode()).isEqualTo(404);
        assertThat(na.statusCode()).isEqualTo(405);
        CiOutput.record(LESSON, "404-405", "GET /payments → " + nf.statusCode() + "\nPUT /orders/1 → " + na.statusCode()
                + " (Allow: " + na.headers().firstValue("Allow").orElse("") + ")");
    }

    @Test
    @DisplayName("GET /orders/abc는 컨트롤러를 부르지 않고 400이다")
    void typeMismatchBeforeController() {
        int before = FIND_CALLS.get();
        HttpResponse<String> res = send("GET", "/orders/abc", null, null);

        assertThat(res.statusCode()).isEqualTo(400);
        assertThat(FIND_CALLS.get()).isEqualTo(before);
        CiOutput.record(LESSON, "type-mismatch", "GET /orders/abc → " + res.statusCode() + "\n컨트롤러 실행 횟수: "
                + (FIND_CALLS.get() - before) + "\n" + shortBody(res.body()));
    }

    @Test
    @DisplayName("enum 쿼리 값은 이름과 대소문자까지 같아야 한다: status=paid는 400, PAID는 200. 빠지면 400")
    void enumParam() {
        HttpResponse<String> lower = send("GET", "/orders?status=paid", null, null);
        HttpResponse<String> upper = send("GET", "/orders?status=PAID", null, null);
        HttpResponse<String> missing = send("GET", "/orders", null, null);

        assertThat(lower.statusCode()).isEqualTo(400);
        assertThat(upper.statusCode()).isEqualTo(200);
        assertThat(missing.statusCode()).isEqualTo(400);
        CiOutput.record(LESSON, "enum-param", "GET /orders?status=paid → " + lower.statusCode()
                + "\nGET /orders?status=PAID → " + upper.statusCode() + "\nGET /orders (status 없음) → " + missing.statusCode());
    }

    @Test
    @DisplayName("본문 JSON에 없는 int 필드는 0, 모르는 필드는 무시하고 200. JSON 문법이 틀리면 400")
    void requestBodyBinding() {
        HttpResponse<String> partial = send("POST", "/orders/echo", "{\"menu\":\"latte\",\"coupon\":\"WELCOME\"}", null);
        HttpResponse<String> broken = send("POST", "/orders/echo", "{\"menu\":\"latte\",", null);

        assertThat(partial.statusCode()).isEqualTo(200);
        assertThat(partial.body()).isEqualTo("{\"menu\":\"latte\",\"quantity\":0}");
        assertThat(broken.statusCode()).isEqualTo(400);
        CiOutput.record(LESSON, "body", "보낸 본문: {\"menu\":\"latte\",\"coupon\":\"WELCOME\"}\n→ " + partial.statusCode()
                + ", 컨트롤러가 받은 것: " + partial.body() + "\n보낸 본문: {\"menu\":\"latte\", (끝이 잘림)\n→ " + broken.statusCode());
    }

    @Test
    @DisplayName("등록된 메시지 컨버터 목록에 문자열용과 잭슨 JSON용이 있다")
    void messageConverters() {
        RequestMappingHandlerAdapter adapter = web.context().getBean(RequestMappingHandlerAdapter.class);
        List<String> names = adapter.getMessageConverters().stream().map(c -> c.getClass().getSimpleName()).toList();

        assertThat(names).contains("StringHttpMessageConverter", "MappingJackson2HttpMessageConverter");
        CiOutput.record(LESSON, "converters", String.join("\n", names));
    }

    @Test
    @DisplayName("String 반환은 text/plain으로 따옴표 없이, 객체 반환은 application/json")
    void stringVersusObject() {
        HttpResponse<String> ping = send("GET", "/ping", null, null);
        HttpResponse<String> order = send("GET", "/orders/7", null, null);

        assertThat(ping.headers().firstValue("Content-Type").orElse("")).startsWith("text/plain");
        assertThat(ping.body()).isEqualTo("pong");
        assertThat(order.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        CiOutput.record(LESSON, "string-object", "GET /ping\nContent-Type: " + ping.headers().firstValue("Content-Type").orElse("")
                + "\n" + ping.body() + "\n\nGET /orders/7\nContent-Type: " + order.headers().firstValue("Content-Type").orElse("")
                + "\n" + order.body());
    }

    @Test
    @DisplayName("Accept: application/xml이면 만들 수 있는 컨버터가 없어 406")
    void notAcceptable() {
        HttpResponse<String> xml = send("GET", "/orders/7", null, "application/xml");
        HttpResponse<String> csv = send("GET", "/orders?status=PAID", null, "text/csv");

        assertThat(xml.statusCode()).isEqualTo(406);
        assertThat(csv.statusCode()).isEqualTo(406);
        CiOutput.record(LESSON, "406", "GET /orders/7, Accept: application/xml → " + xml.statusCode()
                + "\nGET /orders?status=PAID, Accept: text/csv → " + csv.statusCode());
    }

    @Test
    @DisplayName("ResponseEntity.created는 201과 Location 헤더")
    void created() {
        HttpResponse<String> res = send("POST", "/orders", "{\"menu\":\"latte\",\"quantity\":2}", null);

        assertThat(res.statusCode()).isEqualTo(201);
        assertThat(res.headers().firstValue("Location").orElse("")).isEqualTo("/orders/42");
        CiOutput.record(LESSON, "created", "POST /orders\n→ " + res.statusCode() + "\nLocation: "
                + res.headers().firstValue("Location").orElse("") + "\n" + res.body());
    }

    @Test
    @DisplayName("엔티티를 @RequestBody로 받으면 클라이언트가 보낸 id와 status가 들어가고, 요청 record에는 들어가지 않는다")
    void entityAsRequestBody() {
        String json = "{\"id\":999,\"menu\":\"latte\",\"quantity\":2,\"status\":\"PAID\"}";
        HttpResponse<String> entity = send("POST", "/orders/entity", json, null);
        HttpResponse<String> record = send("POST", "/orders/echo", json, null);

        assertThat(entity.body()).contains("\"id\":999").contains("\"status\":\"PAID\"");
        assertThat(record.body()).isEqualTo("{\"menu\":\"latte\",\"quantity\":2}");
        CiOutput.record(LESSON, "entity", "보낸 본문: " + json + "\n엔티티로 받음 → " + entity.body() + "\n요청 record로 받음 → " + record.body());
    }
}
