package lab.devlessons.lspisp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lab.devlessons.support.BrokenFiles;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "리스코프 치환과 인터페이스 분리 원칙"(lsp-isp)의 주장을 확인한다.
 * 실행 결과는 이 파일 안의 클래스로, 컴파일 오류와 깨지는 파일은 소스 문자열을 컴파일해서(BrokenFiles) 본다.
 */
class LspIspTest {

    private static final String LESSON = "lsp-isp";

    // ── 코드 fat ──
    interface Payment {
        void pay(int amount);

        void refund();

        void partialRefund(int amount);
    }

    static class CardPayment implements Payment {
        int refunded;

        public void pay(int amount) {
        }

        public void refund() {
        }

        public void partialRefund(int amount) {
            refunded += amount;
        }
    }

    static class PointPayment implements Payment {
        int refunded;

        public void pay(int amount) {
        }

        public void refund() {
        }

        public void partialRefund(int amount) {
            refunded += amount;
        }
    }

    static class GiftCardPayment implements Payment {
        public void pay(int amount) {
        }

        public void refund() {
        }

        public void partialRefund(int amount) {
            throw new UnsupportedOperationException();
        }
    }

    // ── 코드 cancel ──
    static void cancelItem(Payment payment, int itemPrice) {
        payment.partialRefund(itemPrice);
    }

    @Test
    @DisplayName("코드 cancel: 카드와 포인트는 성공하고 상품권은 UnsupportedOperationException")
    void cancelWithThreePayments() {
        Map<String, Payment> payments = new LinkedHashMap<>();
        payments.put("CardPayment", new CardPayment());
        payments.put("PointPayment", new PointPayment());
        payments.put("GiftCardPayment", new GiftCardPayment());

        StringBuilder out = new StringBuilder("cancelItem(payment, 5000)\n");
        payments.forEach((name, p) -> {
            String result;
            try {
                cancelItem(p, 5000);
                result = "성공";
            } catch (RuntimeException e) {
                result = e.getClass().getSimpleName();
            }
            out.append(String.format("%-16s → %s%n", name, result));
        });

        assertThat(((CardPayment) payments.get("CardPayment")).refunded).isEqualTo(5000);
        assertThatThrownBy(() -> cancelItem(new GiftCardPayment(), 5000))
                .isInstanceOf(UnsupportedOperationException.class);
        CiOutput.record(LESSON, "cancel", out.toString().strip());
    }

    // ── 코드 minAmount ──
    static class Coupon {
        int apply(int price) {
            return price - 1000;
        }
    }

    static class BigOrderCoupon extends Coupon {
        @Override
        int apply(int price) {
            if (price < 50000) {
                throw new IllegalArgumentException("5만 원 이상만");
            }
            return price - 5000;
        }
    }

    @Test
    @DisplayName("코드 minAmount: 3만 원에 Coupon은 29000, BigOrderCoupon은 IllegalArgumentException")
    void strongerPrecondition() {
        List<Coupon> coupons = List.of(new Coupon(), new BigOrderCoupon());
        StringBuilder out = new StringBuilder("3만 원 주문에 쿠폰 적용\n");
        for (Coupon c : coupons) {
            String result;
            try {
                result = String.valueOf(c.apply(30000));
            } catch (IllegalArgumentException e) {
                result = "IllegalArgumentException";
            }
            out.append(String.format("%-14s → %s%n", c.getClass().getSimpleName(), result));
        }

        assertThat(new Coupon().apply(30000)).isEqualTo(29000);
        assertThatThrownBy(() -> new BigOrderCoupon().apply(30000)).isInstanceOf(IllegalArgumentException.class);
        CiOutput.record(LESSON, "coupon", out.toString().strip());
    }

    // ── 컴파일로 확인하는 것 ──

    private static String fatPayment(String type) {
        return """
                interface Payment {
                  void pay(int amount);
                  void refund();
                  void partialRefund(%s amount);
                }
                """.formatted(type);
    }

    private static String fatImpl(String name, boolean supports) {
        return """
                class %s implements Payment {
                  public void pay(int amount) {}
                  public void refund() {}
                  public void partialRefund(int amount) {%s}
                }
                """.formatted(name, supports ? "" : " throw new UnsupportedOperationException(); ");
    }

    private static Map<String, String> fatCode(String type) {
        return Map.of(
                "Payment", fatPayment(type),
                "CardPayment", fatImpl("CardPayment", true),
                "PointPayment", fatImpl("PointPayment", true),
                "GiftCardPayment", fatImpl("GiftCardPayment", false));
    }

    private static final String SPLIT_PAYMENT = """
            interface Payment {
              void pay(int amount);
              void refund();
            }
            """;

    private static String refundable(String type) {
        return """
                interface PartialRefundable {
                  void partialRefund(%s amount);
                }
                """.formatted(type);
    }

    private static String splitImpl(String name) {
        return """
                class %s implements Payment, PartialRefundable {
                  public void pay(int amount) {}
                  public void refund() {}
                  public void partialRefund(int amount) {}
                }
                """.formatted(name);
    }

    private static final String SPLIT_GIFT = """
            class GiftCardPayment implements Payment {
              public void pay(int amount) {}
              public void refund() {}
            }
            """;

    private static Map<String, String> splitCode(String type) {
        Map<String, String> src = new LinkedHashMap<>();
        src.put("Payment", SPLIT_PAYMENT);
        src.put("PartialRefundable", refundable(type));
        src.put("CardPayment", splitImpl("CardPayment"));
        src.put("PointPayment", splitImpl("PointPayment"));
        src.put("GiftCardPayment", SPLIT_GIFT);
        return src;
    }

    @Test
    @DisplayName("코드 cancelSplit에 상품권 결제를 넘기면 컴파일 오류다")
    void splitCatchesAtCompileTime() {
        Map<String, String> src = splitCode("int");
        src.put("Cancel", """
                class Cancel {
                  void cancelItem(PartialRefundable payment, int itemPrice) {
                    payment.partialRefund(itemPrice);
                  }
                  void run() {
                    cancelItem(new GiftCardPayment(), 5000);
                  }
                }
                """);
        assertThat(BrokenFiles.compile(splitCode("int")).ok()).isTrue();

        BrokenFiles r = BrokenFiles.compile(src);

        assertThat(r.files()).containsExactly("Cancel");
        assertThat(r.messages().get(0)).contains("GiftCardPayment cannot be converted to PartialRefundable");
        CiOutput.record(LESSON, "split-compile", "cancelItem(new GiftCardPayment(), 5000)\n→ 컴파일 오류\nerror: "
                + r.messages().get(0).substring("Cancel: ".length()));
    }

    @Test
    @DisplayName("partialRefund의 매개변수를 long으로 바꾸면 코드 fat은 구현 셋이, 코드 split은 PartialRefundable 구현 둘만 깨진다")
    void signatureChangeBreaksOnlyUsers() {
        assertThat(BrokenFiles.compile(fatCode("int")).ok()).isTrue();

        BrokenFiles fat = BrokenFiles.compile(fatCode("long"));
        BrokenFiles split = BrokenFiles.compile(splitCode("long"));

        assertThat(fat.files()).containsExactly("CardPayment", "GiftCardPayment", "PointPayment");
        assertThat(split.files()).containsExactly("CardPayment", "PointPayment");
        CiOutput.record(LESSON, "signature", "[partialRefund(int)를 partialRefund(long)으로]\n\n코드 fat\n→ 컴파일 오류 파일 "
                + fat.files().size() + "개: " + String.join(", ", fat.files()) + "\n\n코드 split\n→ 컴파일 오류 파일 "
                + split.files().size() + "개: " + String.join(", ", split.files()));
    }

    @Test
    @DisplayName("List.of가 돌려준 목록에 add하면 UnsupportedOperationException, new ArrayList로 복사하면 된다")
    void listOfIsUnmodifiable() {
        List<Long> ids = List.of(1L, 2L);
        String result;
        try {
            ids.add(3L);
            result = "성공";
        } catch (UnsupportedOperationException e) {
            result = "UnsupportedOperationException";
        }
        List<Long> copy = new ArrayList<>(ids);
        copy.add(3L);

        assertThat(result).isEqualTo("UnsupportedOperationException");
        assertThat(copy).containsExactly(1L, 2L, 3L);
        CiOutput.record(LESSON, "list-of", "List.of(1L, 2L).add(3L)\n→ " + result
                + "\n\nnew ArrayList<>(ids).add(3L)\n→ " + copy);
    }
}
