package lab.devlessons.wrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 레슨 "래퍼 클래스와 오토박싱"(J3)의 주장을 검증한다. JVM 옵션은 기본값이다(캐시 범위를 늘리는 옵션을 쓰지 않는다).
 */
class WrapperAutoboxingTest {

    private static final String LESSON = "wrapper-autoboxing";

    @Test
    @DisplayName("코드 predict: Long 127끼리의 ==는 true, 128끼리는 false")
    void predict() {
        Long a = 127L;
        Long b = 127L;
        Long c = 128L;
        Long d = 128L;

        assertThat(a == b).isTrue();
        assertThat(c == d).isFalse();
        assertThat(c.equals(d)).isTrue();
        CiOutput.record(LESSON, "predict", (a == b) + "\n" + (c == d));
    }

    @Test
    @DisplayName("Integer도 -128부터 127까지만 ==가 true이고, equals는 언제나 값을 비교한다")
    void integerCacheRange() {
        StringBuilder text = new StringBuilder("값       a == b   a.equals(b)\n");
        for (int v : new int[] {-128, 127, 128, 10000}) {
            Integer a = v;
            Integer b = v;
            boolean same = a == b;
            assertThat(same).isEqualTo(v >= -128 && v <= 127);
            assertThat(a.equals(b)).isTrue();
            text.append("%-8d %-8s %s%n".formatted(v, same, a.equals(b)).replace("\r", ""));
        }
        CiOutput.record(LESSON, "cache-range", text.toString().strip());
    }

    @Test
    @DisplayName("한쪽이 기본형이면 언박싱되어 값으로 비교되고, equals는 타입까지 본다")
    void mixedComparison() {
        Integer x = 1000;
        int y = 1000;
        Long c = 128L;
        long e = 128L;

        assertThat(x == y).isTrue();
        assertThat(c == e).isTrue();
        assertThat(Long.valueOf(1).equals(1)).isFalse();
        assertThat(Long.valueOf(1).equals(1L)).isTrue();
        assertThat(Objects.equals(null, null)).isTrue();
        CiOutput.record(LESSON, "mixed", """
                Integer x = 1000; int y = 1000;
                x == y                      → %s
                Long.valueOf(1).equals(1)   → %s
                Long.valueOf(1).equals(1L)  → %s""".formatted(x == y,
                Long.valueOf(1).equals(1), Long.valueOf(1).equals(1L)));
    }

    @Test
    @DisplayName("박싱과 언박싱이 일어나는 곳: 컬렉션에 넣고 꺼낼 때, 연산할 때")
    void whereBoxingHappens() {
        List<Integer> list = new ArrayList<>();
        list.add(3);
        int first = list.get(0);
        Integer a = 1000;
        Integer b = 1000;
        int sum = a + b;

        assertThat(first).isEqualTo(3);
        assertThat(sum).isEqualTo(2000);
        assertThat(a < b).isFalse();
    }

    @Test
    @DisplayName("null인 래퍼가 언박싱되는 자리에서 NullPointerException이 난다")
    void nullUnboxing() {
        Integer count = null;
        Map<String, Integer> stock = new HashMap<>(Map.of("A", 3));

        assertThatThrownBy(() -> {
            int n = count;
        }).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> {
            if (count == 0) {
                throw new AssertionError();
            }
        }).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> {
            int n = stock.get("B");
        }).isInstanceOf(NullPointerException.class);
        assertThat(stock.getOrDefault("B", 0)).isZero();

        String message = null;
        try {
            if (count == 0) {
                message = "no";
            }
        } catch (NullPointerException ex) {
            message = ex.getMessage();
        }
        CiOutput.record(LESSON, "null-unboxing", """
                Integer count = null;
                if (count == 0) ...
                → NullPointerException: %s""".formatted(message));
    }

    @Test
    @DisplayName("삼항 연산자에서 int와 null인 Integer가 섞이면 언박싱하다 NullPointerException이 난다")
    void ternaryUnboxing() {
        boolean flag = false;
        Integer nullable = null;

        assertThatThrownBy(() -> {
            Integer r = flag ? 1 : nullable;
        }).isInstanceOf(NullPointerException.class);
    }

    // 실무 구역: 엔티티 모양의 클래스. id는 DB에서 읽을 때마다 따로 박싱된다
    static class Member {
        private final Long id;

        Member(long id) {
            this.id = Long.valueOf(id);
        }

        Long getId() {
            return id;
        }
    }

    @Test
    @DisplayName("엔티티의 Long id를 ==로 비교하면 127 이하에서만 true이고, equals는 언제나 true다")
    void entityIdComparison() {
        StringBuilder text = new StringBuilder();
        for (long id : new long[] {100, 300}) {
            Member m1 = new Member(id);
            Member m2 = new Member(id);
            boolean eq = m1.getId() == m2.getId();
            boolean equals = m1.getId().equals(m2.getId());
            assertThat(eq).isEqualTo(id <= 127);
            assertThat(equals).isTrue();
            assertThat(Objects.equals(m1.getId(), m2.getId())).isTrue();
            text.append("""
                    id = %d
                    m1.getId() == m2.getId()       → %s
                    m1.getId().equals(m2.getId())  → %s

                    """.formatted(id, eq, equals));
        }
        CiOutput.record(LESSON, "entity-id", text.toString().strip());
    }

    record IntQuantity(int quantity) {
    }

    record BoxedQuantity(Integer quantity) {
    }

    @Test
    @DisplayName("Jackson으로 {}를 읽으면 int 필드는 0, Integer 필드는 null이다")
    void jacksonMissingField() throws Exception {
        ObjectMapper json = new ObjectMapper();
        IntQuantity a = json.readValue("{}", IntQuantity.class);
        BoxedQuantity b = json.readValue("{}", BoxedQuantity.class);

        assertThat(a.quantity()).isZero();
        assertThat(b.quantity()).isNull();
        CiOutput.record(LESSON, "jackson", """
                요청 본문: {}
                record OrderRequest(int quantity)     → quantity = %d
                record OrderRequest(Integer quantity) → quantity = %s""".formatted(a.quantity(), b.quantity()));
    }

    @RestController
    static class PageController {
        @GetMapping("/orders")
        String orders(@RequestParam(required = false) int page) {
            return "page=" + page;
        }

        @GetMapping("/orders-boxed")
        String ordersBoxed(@RequestParam(required = false) Integer page) {
            return "page=" + page;
        }

        @GetMapping("/orders-default")
        String ordersDefault(@RequestParam(defaultValue = "0") int page) {
            return "page=" + page;
        }
    }

    @Test
    @DisplayName("@RequestParam(required = false) int에 값이 없으면 500, Integer는 null, defaultValue는 0")
    void requestParamPrimitive() {
        try (MiniWeb web = MiniWeb.start(PageController.class)) {
            MiniWeb.Reply bad = web.get("/orders");
            MiniWeb.Reply boxed = web.get("/orders-boxed");
            MiniWeb.Reply fixed = web.get("/orders-default");

            assertThat(bad.status()).isEqualTo(500);
            assertThat(bad.body()).contains("primitive");
            assertThat(boxed.status()).isEqualTo(200);
            assertThat(boxed.body()).isEqualTo("page=null");
            assertThat(fixed.body()).isEqualTo("page=0");
            CiOutput.record(LESSON, "request-param", """
                    GET /orders          (int page)
                    → %d %s

                    GET /orders-boxed    (Integer page)
                    → %d %s

                    GET /orders-default  (defaultValue = "0")
                    → %d %s""".formatted(bad.status(), message(bad.body()), boxed.status(), boxed.body(),
                    fixed.status(), fixed.body()));
        }
    }

    private static String message(String errorJson) {
        try {
            return new ObjectMapper().readTree(errorJson).path("message").asText();
        } catch (Exception e) {
            return errorJson;
        }
    }
}
