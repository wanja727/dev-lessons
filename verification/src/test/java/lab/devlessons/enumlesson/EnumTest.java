package lab.devlessons.enumlesson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.JavaSource;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 레슨 "enum"(J8)의 주장 가운데 DB를 쓰지 않는 것을 검증한다. DB에 저장하는 것은 EnumJpaTest가 본다.
 */
class EnumTest {

    private static final String LESSON = "enum";

    /** 코드 rich */
    enum RichStatus {
        CREATED("주문 접수", true),
        PAID("결제 완료", true),
        SHIPPED("배송 중", false),
        CANCELLED("취소", false);

        private final String label;
        private final boolean cancellable;

        RichStatus(String label, boolean cancellable) {
            this.label = label;
            this.cancellable = cancellable;
        }

        boolean canCancel() {
            return cancellable;
        }

        String label() {
            return label;
        }
    }

    /** 상수마다 다른 동작 */
    enum PayMethod {
        CARD {
            BigDecimal fee(BigDecimal amount) {
                return amount.multiply(new BigDecimal("0.03"));
            }
        },
        BANK {
            BigDecimal fee(BigDecimal amount) {
                return new BigDecimal("500");
            }
        };

        abstract BigDecimal fee(BigDecimal amount);
    }

    @Test
    @DisplayName("없는 이름으로 valueOf를 부르면 IllegalArgumentException, 대소문자도 맞아야 한다")
    void valueOfUnknown() {
        assertThat(OrderStatus.valueOf("PAID")).isSameAs(OrderStatus.PAID);
        assertThatThrownBy(() -> OrderStatus.valueOf("paid")).isInstanceOf(IllegalArgumentException.class);
        assertThat(OrderStatus.values()).containsExactly(
                OrderStatus.CREATED, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.CANCELLED);
        String message = null;
        try {
            OrderStatus.valueOf("paid");
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
        CiOutput.record(LESSON, "value-of", """
                OrderStatus.valueOf("PAID") → PAID
                OrderStatus.valueOf("paid")
                → IllegalArgumentException: %s""".formatted(message.replace("lab.devlessons.enumlesson.", "")));
    }

    @Test
    @DisplayName("int로 받으면 아무 숫자나 컴파일되고, enum으로 받으면 상수만 컴파일된다")
    void typeSafety() {
        JavaSource.Result withInt = JavaSource.compile("Shop", """
                public class Shop {
                  void changeStatus(long orderId, int status) {}
                  void run() { changeStatus(1L, 7); }
                }
                """);
        JavaSource.Result withEnum = JavaSource.compile("Shop", """
                enum OrderStatus { CREATED, PAID, SHIPPED, CANCELLED }
                public class Shop {
                  void changeStatus(long orderId, OrderStatus status) {}
                  void run() { changeStatus(1L, 7); }
                }
                """);

        assertThat(withInt.ok()).isTrue();
        assertThat(withEnum.ok()).isFalse();
        CiOutput.record(LESSON, "type-safety", """
                int로 받을 때 changeStatus(1L, 7)
                → 컴파일됨

                OrderStatus로 받을 때 changeStatus(1L, 7)
                → error: %s""".formatted(withEnum.errors().get(0).lines().findFirst().orElse("")));
    }

    @Test
    @DisplayName("상수는 하나뿐인 객체라 valueOf로 얻은 것과 ==가 true이고, null과의 ==는 false다")
    void singleInstance() {
        OrderStatus a = OrderStatus.valueOf("PAID");
        OrderStatus b = OrderStatus.PAID;
        OrderStatus none = null;

        assertThat(a == b).isTrue();
        assertThat(none == OrderStatus.PAID).isFalse();
        assertThatThrownBy(() -> none.equals(OrderStatus.PAID)).isInstanceOf(NullPointerException.class);
        CiOutput.record(LESSON, "single", """
                OrderStatus.valueOf("PAID") == OrderStatus.PAID → %s
                status(null) == OrderStatus.PAID                → %s
                status(null).equals(OrderStatus.PAID)           → NullPointerException""".formatted(a == b,
                none == OrderStatus.PAID));
    }

    @Test
    @DisplayName("new로 enum 객체를 만들 수 없다")
    void cannotInstantiate() {
        JavaSource.Result r = JavaSource.compile("Shop", """
                enum OrderStatus { CREATED, PAID }
                public class Shop { Object o = new OrderStatus(); }
                """);
        assertThat(r.ok()).isFalse();
        assertThat(r.errors()).anyMatch(e -> e.contains("enum classes may not be instantiated"));
    }

    @Test
    @DisplayName("코드 rich: 상수마다 필드를 갖고, 상수마다 다른 구현을 줄 수 있다")
    void richEnum() {
        assertThat(RichStatus.SHIPPED.canCancel()).isFalse();
        assertThat(RichStatus.PAID.canCancel()).isTrue();
        assertThat(PayMethod.CARD.fee(new BigDecimal("10000"))).isEqualByComparingTo("300");
        assertThat(PayMethod.BANK.fee(new BigDecimal("10000"))).isEqualByComparingTo("500");
        CiOutput.record(LESSON, "rich", """
                PAID.canCancel()    → %s
                SHIPPED.canCancel() → %s
                SHIPPED.label()     → %s

                CARD.fee(10000) → %s
                BANK.fee(10000) → %s""".formatted(RichStatus.PAID.canCancel(), RichStatus.SHIPPED.canCancel(),
                RichStatus.SHIPPED.label(), PayMethod.CARD.fee(new BigDecimal("10000")).stripTrailingZeros()
                        .toPlainString(), PayMethod.BANK.fee(new BigDecimal("10000"))));
    }

    @Test
    @DisplayName("default 없는 switch 식은 상수를 빠뜨리면 컴파일되지 않고, default가 있으면 컴파일된다")
    void switchExhaustive() {
        String header = "enum OrderStatus { CREATED, PAID, SHIPPED, CANCELLED }\n";
        JavaSource.Result missing = JavaSource.compile("Shop", header + """
                public class Shop {
                  String text(OrderStatus status) {
                    return switch (status) {
                      case CREATED -> "접수";
                      case PAID -> "결제";
                      case SHIPPED -> "배송";
                    };
                  }
                }
                """);
        JavaSource.Result withDefault = JavaSource.compile("Shop", header + """
                public class Shop {
                  String text(OrderStatus status) {
                    return switch (status) {
                      case CREATED -> "접수";
                      case PAID -> "결제";
                      default -> "기타";
                    };
                  }
                }
                """);
        JavaSource.Result oldStatement = JavaSource.compile("Shop", header + """
                public class Shop {
                  String text(OrderStatus status) {
                    String t = "";
                    switch (status) {
                      case CREATED: t = "접수"; break;
                      case PAID: t = "결제"; break;
                    }
                    return t;
                  }
                }
                """);

        assertThat(missing.ok()).isFalse();
        assertThat(withDefault.ok()).isTrue();
        assertThat(oldStatement.ok()).isTrue();
        CiOutput.record(LESSON, "switch", """
                default 없는 switch 식, CANCELLED 빠뜨림
                → error: %s

                default가 있는 switch 식 → 컴파일됨
                예전 방식의 switch 문      → 컴파일됨""".formatted(missing.errors().get(0)));
    }

    @Test
    @DisplayName("상수를 끼워 넣으면 뒤 상수의 ordinal이 1씩 늘고, 예전 번호로 읽으면 다른 상수가 나온다")
    void ordinalShifts() {
        int stored = OrderStatus.SHIPPED.ordinal();

        assertThat(stored).isEqualTo(2);
        assertThat(OrderStatusV2.SHIPPED.ordinal()).isEqualTo(3);
        assertThat(OrderStatusV2.CANCELLED.ordinal()).isEqualTo(4);
        assertThat(OrderStatusV2.values()[stored]).isEqualTo(OrderStatusV2.PREPARING);
        assertThat(OrderStatusV2.valueOf(OrderStatus.SHIPPED.name())).isEqualTo(OrderStatusV2.SHIPPED);
        CiOutput.record(LESSON, "ordinal", """
                끼워 넣기 전 SHIPPED.ordinal()   → %d
                끼워 넣은 뒤 SHIPPED.ordinal()   → %d
                끼워 넣은 뒤 values()[2]         → %s
                끼워 넣은 뒤 valueOf("SHIPPED")  → %s""".formatted(stored, OrderStatusV2.SHIPPED.ordinal(),
                OrderStatusV2.values()[stored], OrderStatusV2.valueOf("SHIPPED")));
    }

    record OrderView(long id, OrderStatus status) {
    }

    @Test
    @DisplayName("Jackson은 enum을 이름으로 쓰고 읽고, 없는 이름은 예외가 된다")
    void jacksonUsesName() throws Exception {
        ObjectMapper json = new ObjectMapper();
        String written = json.writeValueAsString(new OrderView(1, OrderStatus.PAID));
        OrderView read = json.readValue("{\"id\":1,\"status\":\"SHIPPED\"}", OrderView.class);

        assertThat(written).isEqualTo("{\"id\":1,\"status\":\"PAID\"}");
        assertThat(read.status()).isEqualTo(OrderStatus.SHIPPED);
        assertThatThrownBy(() -> json.readValue("{\"id\":1,\"status\":\"PIAD\"}", OrderView.class))
                .isInstanceOf(com.fasterxml.jackson.databind.exc.InvalidFormatException.class);
    }

    record ChangeRequest(OrderStatus status) {
    }

    @RestController
    static class OrderController {
        @GetMapping("/orders")
        String orders(@RequestParam OrderStatus status) {
            return "조회: " + status;
        }

        @PostMapping("/orders/status")
        String change(@RequestBody ChangeRequest request) {
            return "변경: " + request.status();
        }
    }

    @Test
    @DisplayName("스프링 MVC는 요청의 글자를 enum으로 바꾸고, 없는 이름이면 400으로 답한다")
    void springConvertsByName() {
        try (MiniWeb web = MiniWeb.start(OrderController.class)) {
            MiniWeb.Reply okParam = web.get("/orders?status=PAID");
            MiniWeb.Reply badParam = web.get("/orders?status=PIAD");
            MiniWeb.Reply okBody = web.postJson("/orders/status", "{\"status\":\"SHIPPED\"}");
            MiniWeb.Reply badBody = web.postJson("/orders/status", "{\"status\":\"PIAD\"}");

            assertThat(okParam.body()).isEqualTo("조회: PAID");
            assertThat(badParam.status()).isEqualTo(400);
            assertThat(okBody.body()).isEqualTo("변경: SHIPPED");
            assertThat(badBody.status()).isEqualTo(400);
            CiOutput.record(LESSON, "spring", """
                    GET /orders?status=PAID
                    → %d %s
                    GET /orders?status=PIAD
                    → %d

                    POST /orders/status {"status":"SHIPPED"}
                    → %d %s
                    POST /orders/status {"status":"PIAD"}
                    → %d""".formatted(okParam.status(), okParam.body(), badParam.status(),
                    okBody.status(), okBody.body(), badBody.status()));
        }
    }
}
