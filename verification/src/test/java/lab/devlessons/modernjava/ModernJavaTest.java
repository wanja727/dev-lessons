package lab.devlessons.modernjava;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.JavaSource;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 레슨 "자바 17과 21에서 달라진 것"(J18)의 주장을 검증한다.
 * 종류를 추가했을 때의 컴파일 결과는 테스트 안에서 javac를 돌려 본다 (자바 21, 미리 보기 기능은 켜지 않는다).
 */
class ModernJavaTest {

    private static final String LESSON = "modern-java";

    // 코드 sealed
    sealed interface PaymentResult permits Approved, Declined, Failed {
    }

    record Approved(String txId, long amount) implements PaymentResult {
    }

    record Declined(String reason) implements PaymentResult {
    }

    record Failed(Exception cause) implements PaymentResult {
    }

    // 코드 chain
    static String chain(PaymentResult r) {
        if (r instanceof Approved a) {
            return "승인 " + a.txId();
        } else if (r instanceof Declined d) {
            return "거절 " + d.reason();
        } else {
            return "오류";
        }
    }

    // 코드 switchType
    static String switchType(PaymentResult r) {
        return switch (r) {
            case Approved a -> "승인 " + a.txId();
            case Declined d -> "거절 " + d.reason();
            case Failed f -> "오류";
        };
    }

    // 코드 recordPattern
    static String recordPattern(PaymentResult r) {
        return switch (r) {
            case Approved(String txId, long amount) when amount >= 1_000_000 -> "고액 승인 " + txId;
            case Approved(String txId, long amount) -> "승인 " + txId;
            case Declined(String reason) -> "거절 " + reason;
            case Failed(Exception cause) -> "오류";
        };
    }

    static final List<PaymentResult> THREE = List.of(new Approved("T1", 10_000), new Declined("잔액 부족"),
            new Failed(new IllegalStateException("타임아웃")));

    @Test
    @DisplayName("instanceof 사슬과 default 없는 switch는 세 종류에 같은 문자열을 돌려준다")
    void chainAndSwitchAgree() {
        List<String> a = THREE.stream().map(ModernJavaTest::chain).toList();
        List<String> b = THREE.stream().map(ModernJavaTest::switchType).toList();
        assertThat(a).isEqualTo(b).containsExactly("승인 T1", "거절 잔액 부족", "오류");
        CiOutput.record(LESSON, "agree", """
                        chain       switchType
                Approved  → %s     %s
                Declined  → %s  %s
                Failed    → %s        %s""".formatted(a.get(0), b.get(0), a.get(1), b.get(1), a.get(2), b.get(2)));
    }

    static String firstError(JavaSource.Result r) {
        if (r.ok()) {
            return "컴파일됨";
        }
        String e = r.errors().get(0);
        return "컴파일 오류: " + e.lines().findFirst().orElse(e).replaceAll("^.*?error: ", "");
    }

    static final String SEALED_SOURCE = """
            sealed interface PaymentResult
                permits Approved, Declined, Failed {}
            record Approved(String txId, long amount) implements PaymentResult {}
            record Declined(String reason) implements PaymentResult {}
            record Failed(Exception cause) implements PaymentResult {}
            """;

    @Test
    @DisplayName("permits에 없는 타입은 sealed 인터페이스를 구현할 수 없고, 허용된 자식 클래스는 final, sealed, non-sealed 가운데 하나를 밝혀야 한다")
    void sealedRules() {
        JavaSource.Result refunded = JavaSource.compile(Map.of(
                "PaymentResult", SEALED_SOURCE,
                "Refunded", "record Refunded(long amount) implements PaymentResult {}"));
        JavaSource.Result circle = JavaSource.compile("Shape", """
                sealed class Shape permits Circle, Square {}
                class Circle extends Shape {}
                final class Square extends Shape {}
                """);
        JavaSource.Result circleFinal = JavaSource.compile("Shape", """
                sealed class Shape permits Circle, Square {}
                final class Circle extends Shape {}
                non-sealed class Square extends Shape {}
                """);

        assertThat(refunded.ok()).isFalse();
        assertThat(circle.ok()).isFalse();
        assertThat(circleFinal.ok()).isTrue();
        CiOutput.record(LESSON, "sealed-rules", """
                record Refunded(...) implements PaymentResult {}
                → %s

                class Circle extends Shape {}   (아무것도 안 씀)
                → %s

                final class Circle, non-sealed class Square
                → %s""".formatted(firstError(refunded), firstError(circle), firstError(circleFinal)));
    }

    static Map<String, String> withPending(String messageSource) {
        return Map.of(
                "PaymentResult", """
                        sealed interface PaymentResult
                            permits Approved, Declined, Failed, Pending {}
                        record Approved(String txId, long amount) implements PaymentResult {}
                        record Declined(String reason) implements PaymentResult {}
                        record Failed(Exception cause) implements PaymentResult {}
                        record Pending(String txId) implements PaymentResult {}
                        """,
                "Messages", messageSource);
    }

    static final String CHAIN_SOURCE = """
            public class Messages {
              public static String message(PaymentResult r) {
                if (r instanceof Approved a) {
                  return "승인 " + a.txId();
                } else if (r instanceof Declined d) {
                  return "거절 " + d.reason();
                } else {
                  return "오류";
                }
              }
              public static String pending() { return message(new Pending("T1")); }
            }
            """;

    static final String SWITCH_SOURCE = """
            public class Messages {
              public static String message(PaymentResult r) {
                return switch (r) {
                  case Approved a -> "승인 " + a.txId();
                  case Declined d -> "거절 " + d.reason();
                  case Failed f -> "오류";
                };
              }
            }
            """;

    static final String DEFAULT_SOURCE = """
            public class Messages {
              public static String message(PaymentResult r) {
                return switch (r) {
                  case Approved a -> "승인 " + a.txId();
                  case Declined d -> "거절 " + d.reason();
                  default -> "오류";
                };
              }
              public static String pending() { return message(new Pending("T1")); }
            }
            """;

    static String callPending(JavaSource.Result r) throws Exception {
        return (String) r.load("Messages").getMethod("pending").invoke(null);
    }

    @Test
    @DisplayName("Pending을 추가하면 default 없는 switch만 컴파일되지 않고, instanceof 사슬과 default가 있는 switch는 컴파일되어 Pending을 오류로 처리한다")
    void addingPending() throws Exception {
        JavaSource.Result chain = JavaSource.compile(withPending(CHAIN_SOURCE));
        JavaSource.Result sw = JavaSource.compile(withPending(SWITCH_SOURCE));
        JavaSource.Result withDefault = JavaSource.compile(withPending(DEFAULT_SOURCE));

        assertThat(chain.ok()).isTrue();
        assertThat(sw.ok()).isFalse();
        assertThat(withDefault.ok()).isTrue();
        String chainPending = callPending(chain);
        String defaultPending = callPending(withDefault);
        assertThat(chainPending).isEqualTo("오류");
        assertThat(defaultPending).isEqualTo("오류");

        CiOutput.record(LESSON, "predict", """
                Pending을 permits에 추가하고 두 메서드는 그대로 컴파일
                chain (instanceof와 else)  → %s
                switchType (default 없음)  → %s

                chain에 new Pending("T1")을 넘기면 → "%s\"""".formatted(firstError(chain), firstError(sw),
                chainPending));
        CiOutput.record(LESSON, "default", """
                default -> "오류"를 넣은 switch에 Pending 추가
                → %s
                new Pending("T1")을 넘기면 → "%s\"""".formatted(firstError(withDefault), defaultPending));
    }

    @Test
    @DisplayName("넓은 타입의 case를 먼저 쓰면 컴파일되지 않고, case null이 없는 switch에 null을 넘기면 NullPointerException이다")
    void orderAndNull() {
        JavaSource.Result dominated = JavaSource.compile("Kinds", """
                class Kinds {
                  static String kind(Object o) {
                    return switch (o) {
                      case Object x -> "객체";
                      case String s -> "문자열";
                    };
                  }
                }
                """);
        Exception npe = null;
        try {
            switchType(null);
        } catch (NullPointerException e) {
            npe = e;
        }
        String withNull = withCaseNull(null);

        assertThat(dominated.ok()).isFalse();
        assertThat(npe).isInstanceOf(NullPointerException.class);
        assertThat(withNull).isEqualTo("결과 없음");
        CiOutput.record(LESSON, "order", "case Object x를 case String s보다 먼저 씀\n→ " + firstError(dominated));
        CiOutput.record(LESSON, "null", """
                switchType(null)  (case null 없음)
                → %s

                case null -> "결과 없음"을 더한 switch에 null
                → %s""".formatted(npe.getClass().getSimpleName(), withNull));
    }

    static String withCaseNull(PaymentResult r) {
        return switch (r) {
            case null -> "결과 없음";
            case Approved a -> "승인 " + a.txId();
            case Declined d -> "거절 " + d.reason();
            case Failed f -> "오류";
        };
    }

    record Point(int x, int y) {
    }

    record Member(String name) {
    }

    record Money(long amount) {
    }

    record Order(Member member, Money total) {
    }

    static String nested(Object o) {
        return switch (o) {
            case Order(Member(String name), Money(long amount)) -> name + "님 " + amount + "원";
            default -> "주문 아님";
        };
    }

    @Test
    @DisplayName("record 패턴은 구성 요소를 꺼내고, 가드와 중첩과 instanceof에도 쓴다")
    void recordPatterns() {
        String big = recordPattern(new Approved("T1", 2_000_000));
        String small = recordPattern(new Approved("T2", 10_000));
        Object o = new Point(3, 4);
        int sum = -1;
        if (o instanceof Point(int x, int y)) {
            sum = x + y;
        }
        String order = nested(new Order(new Member("김"), new Money(12_000)));

        assertThat(big).isEqualTo("고액 승인 T1");
        assertThat(small).isEqualTo("승인 T2");
        assertThat(sum).isEqualTo(7);
        assertThat(order).isEqualTo("김님 12000원");
        CiOutput.record(LESSON, "record-pattern", """
                message(new Approved("T1", 2_000_000)) → %s
                message(new Approved("T2", 10_000))    → %s""".formatted(big, small));
        CiOutput.record(LESSON, "nested", """
                o instanceof Point(int x, int y) → x + y = %d
                case Order(Member(String name), Money(long amount))
                → %s""".formatted(sum, order));
    }

    @Test
    @DisplayName("밑줄 패턴은 자바 21에서 미리 보기 옵션 없이 컴파일되지 않는다")
    void underscoreIsPreview() {
        JavaSource.Result r = JavaSource.compile(Map.of(
                "PaymentResult", SEALED_SOURCE,
                "Messages", """
                        class Messages {
                          static String message(PaymentResult r) {
                            return switch (r) {
                              case Approved(var txId, _) -> "승인 " + txId;
                              case Declined d -> "거절";
                              case Failed f -> "오류";
                            };
                          }
                        }
                        """));
        assertThat(r.ok()).isFalse();
        CiOutput.record(LESSON, "underscore", "case Approved(var txId, _)  (자바 21, --enable-preview 없음)\n→ "
                + firstError(r));
    }

    // 실무 구역: 서비스의 결과를 컨트롤러에서 응답으로
    static class PaymentService {
        PaymentResult pay(String kind) {
            return switch (kind) {
                case "approved" -> new Approved("T1", 10_000);
                case "declined" -> new Declined("잔액 부족");
                default -> new Failed(new IllegalStateException("PG 타임아웃"));
            };
        }
    }

    record ApprovedView(String txId, long amount) {
    }

    record ErrorView(String message) {
    }

    @RestController
    static class PaymentController {
        private final PaymentService service = new PaymentService();

        @PostMapping("/payments")
        ResponseEntity<?> pay(@RequestParam String kind) {
            PaymentResult result = service.pay(kind);
            return switch (result) {
                case Approved a -> ResponseEntity.ok(new ApprovedView(a.txId(), a.amount()));
                case Declined d -> ResponseEntity.status(402).body(new ErrorView(d.reason()));
                case Failed f -> ResponseEntity.status(502).body(new ErrorView("결제사 오류"));
            };
        }
    }

    @Test
    @DisplayName("컨트롤러의 switch가 세 결과에 200, 402, 502를 돌려준다")
    void controllerMapsResults() {
        try (MiniWeb web = MiniWeb.start(PaymentController.class)) {
            MiniWeb.Reply ok = web.postJson("/payments?kind=approved", "{}");
            MiniWeb.Reply declined = web.postJson("/payments?kind=declined", "{}");
            MiniWeb.Reply failed = web.postJson("/payments?kind=failed", "{}");

            assertThat(ok.status()).isEqualTo(200);
            assertThat(declined.status()).isEqualTo(402);
            assertThat(failed.status()).isEqualTo(502);
            CiOutput.record(LESSON, "controller", """
                    Approved → %d %s
                    Declined → %d %s
                    Failed   → %d %s""".formatted(ok.status(), ok.body(), declined.status(), declined.body(),
                    failed.status(), failed.body()));
        }
    }

    static int lengthOf(Order order) {
        return order.member().name().length();
    }

    @Test
    @DisplayName("var, 텍스트 블록, Stream.toList, 자세한 NPE 메시지, getFirst와 getLast")
    void smallFeatures() {
        var orders = new ArrayList<String>();
        orders.add("A-1");
        orders.add("A-2");
        orders.add("A-3");
        String json = """
                {"id": 1,
                 "status": "PAID"}""";
        List<String> upper = Stream.of("a", "b").map(String::toUpperCase).toList();
        String npe = "";
        try {
            lengthOf(new Order(new Member(null), new Money(0)));
        } catch (NullPointerException e) {
            npe = e.getMessage();
        }
        boolean unmodifiable;
        try {
            upper.add("C");
            unmodifiable = false;
        } catch (UnsupportedOperationException e) {
            unmodifiable = true;
        }

        assertThat(orders.getFirst()).isEqualTo("A-1");
        assertThat(orders.getLast()).isEqualTo("A-3");
        assertThat(json).contains("\n");
        assertThat(upper).containsExactly("A", "B");
        assertThat(unmodifiable).isTrue();
        assertThat(npe).contains("because");
        CiOutput.record(LESSON, "small", """
                var orders = new ArrayList<String>() → %s
                orders.getFirst(), getLast()         → %s, %s
                텍스트 블록의 줄 수                   → %d
                Stream.of("a","b")...toList()        → %s (add하면 %s)
                order.member().name().length()의 NPE →
                  %s""".formatted(orders.getClass().getSimpleName(), orders.getFirst(), orders.getLast(),
                json.lines().count(), upper, unmodifiable ? "UnsupportedOperationException" : "됨",
                npe.replace("lab.devlessons.modernjava.ModernJavaTest$", "")));
    }
}
