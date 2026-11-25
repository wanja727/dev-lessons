package lab.devlessons.oopbasics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lab.devlessons.support.BrokenFiles;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.SourceChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "객체지향의 네 가지 특징"(oop-four-pillars)의 주장을 확인한다.
 * 지갑과 결제 코드는 레슨의 코드와 같은 모양으로 이 파일 안에 두었다.
 * 결제 수단을 더할 때 고치는 파일은 더하기 전후의 소스 문자열을 비교한다(SourceChange).
 */
class OopFourPillarsTest {

    private static final String LESSON = "oop-four-pillars";

    // ── 코드 publicWallet ──
    static class PublicWallet {
        public int balance;
    }

    // ── 코드 wallet ──
    static class PointWallet {
        private int balance;

        void use(int amount) {
            if (amount > balance) {
                throw new IllegalStateException("잔액 부족");
            }
            balance -= amount;
        }

        void charge(int amount) {
            balance += amount;
        }

        int balance() {
            return balance;
        }
    }

    // ── 코드 setterWallet ──
    static class SetterWallet {
        private int balance;

        int getBalance() {
            return balance;
        }

        void setBalance(int b) {
            balance = b;
        }
    }

    @Test
    @DisplayName("코드 publicWallet: 잔액 1000에서 3000을 빼면 -2000이 된다")
    void publicFieldGoesNegative() {
        PublicWallet wallet = new PublicWallet();
        wallet.balance = 1000;

        wallet.balance -= 3000;

        assertThat(wallet.balance).isEqualTo(-2000);
        CiOutput.record(LESSON, "public", "잔액 1000에서 wallet.balance -= 3000\n→ 잔액: " + wallet.balance);
    }

    @Test
    @DisplayName("코드 wallet: use(3000)은 IllegalStateException이고 잔액은 1000 그대로다")
    void encapsulatedWalletRejects() {
        PointWallet wallet = new PointWallet();
        wallet.charge(1000);

        String error = null;
        try {
            wallet.use(3000);
        } catch (IllegalStateException e) {
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
        }

        assertThat(error).isEqualTo("IllegalStateException: 잔액 부족");
        assertThat(wallet.balance()).isEqualTo(1000);
        CiOutput.record(LESSON, "wallet", "잔액 1000에서 wallet.use(3000)\n→ " + error + "\n→ 잔액: " + wallet.balance());
    }

    @Test
    @DisplayName("private 필드를 다른 클래스에서 바꾸면 컴파일 오류다")
    void privateFieldIsCompileError() {
        BrokenFiles r = BrokenFiles.compile(Map.of(
                "PointWallet", """
                        class PointWallet {
                          private int balance;
                        }
                        """,
                "OrderService", """
                        class OrderService {
                          void order(PointWallet wallet) {
                            wallet.balance -= 3000;
                          }
                        }
                        """));

        assertThat(r.files()).containsExactly("OrderService");
        assertThat(r.messages().get(0)).contains("balance has private access in PointWallet");
        CiOutput.record(LESSON, "private", "오류가 난 파일: OrderService.java\nerror: "
                + r.messages().get(0).substring("OrderService: ".length()));
    }

    @Test
    @DisplayName("코드 setterWallet: 꺼내서 빼고 다시 넣으면 -2000이 된다")
    void setterGoesNegative() {
        SetterWallet wallet = new SetterWallet();
        wallet.setBalance(1000);

        int b = wallet.getBalance();
        wallet.setBalance(b - 3000);

        assertThat(wallet.getBalance()).isEqualTo(-2000);
        CiOutput.record(LESSON, "setter", "잔액 1000에서\nint b = wallet.getBalance();\nwallet.setBalance(b - 3000);\n→ 잔액: "
                + wallet.getBalance());
    }

    // ── 코드 history ──
    static class History {
        private final List<Long> history = new ArrayList<>(List.of(1000L, 500L));

        List<Long> getHistory() {
            return history;
        }

        List<Long> getHistoryView() {
            return Collections.unmodifiableList(history);
        }

        List<Long> getHistoryCopy() {
            return List.copyOf(history);
        }

        int size() {
            return history.size();
        }
    }

    @Test
    @DisplayName("내부 리스트를 그대로 돌려주면 밖의 clear()가 안을 비우고, 보기나 복사본은 add에서 UnsupportedOperationException")
    void collectionGetter() {
        History wallet = new History();
        wallet.getHistory().clear();
        assertThat(wallet.size()).isZero();

        History safe = new History();
        assertThatThrownBy(() -> safe.getHistoryView().add(1L)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> safe.getHistoryCopy().add(1L)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(safe.size()).isEqualTo(2);

        CiOutput.record(LESSON, "history", "기록 2건인 지갑에서\nwallet.getHistory().clear()\n→ 지갑의 기록: " + wallet.size() + "건\n\n"
                + "unmodifiableList를 돌려주면\nview.add(1L)\n→ UnsupportedOperationException\n→ 지갑의 기록: " + safe.size() + "건");
    }

    // ── 질문 4: 결제 수단을 더할 때 고치는 파일 ──

    private static final String PAYMENT = """
            interface Payment {
              void pay(int amount);
            }
            """;
    private static final String CARD = """
            class CardPayment implements Payment {
              public void pay(int amount) {}
            }
            """;
    private static final String POINT = """
            class PointPayment implements Payment {
              public void pay(int amount) {}
            }
            """;
    private static final String CHECKOUT = """
            class Checkout {
              void checkout(Payment payment, int amount) {
                payment.pay(amount);
              }
            }
            """;
    private static final String KAKAO = """
            class KakaoPayment implements Payment {
              public void pay(int amount) {}
            }
            """;
    private static final String CHECKOUT_IF = """
            class Checkout {
              void checkout(String method, int amount) {
                if (method.equals("CARD")) {
                  // 카드사 API
                } else if (method.equals("POINT")) {
                  // 지갑에서 뺀다
                }
              }
            }
            """;
    private static final String CHECKOUT_IF_KAKAO = """
            class Checkout {
              void checkout(String method, int amount) {
                if (method.equals("CARD")) {
                  // 카드사 API
                } else if (method.equals("POINT")) {
                  // 지갑에서 뺀다
                } else if (method.equals("KAKAO")) {
                  // 카카오페이 API
                }
              }
            }
            """;

    @Test
    @DisplayName("결제 수단을 더하면 if판은 Checkout을 고치고, 다형성판은 KakaoPayment만 새로 생긴다")
    void addingPaymentMethod() {
        Map<String, String> before = Map.of("Payment", PAYMENT, "CardPayment", CARD, "PointPayment", POINT,
                "Checkout", CHECKOUT);
        Map<String, String> after = Map.of("Payment", PAYMENT, "CardPayment", CARD, "PointPayment", POINT,
                "Checkout", CHECKOUT, "KakaoPayment", KAKAO);
        SourceChange poly = SourceChange.between(before, after);
        SourceChange ifs = SourceChange.between(Map.of("Checkout", CHECKOUT_IF), Map.of("Checkout", CHECKOUT_IF_KAKAO));

        assertThat(BrokenFiles.compile(after).ok()).isTrue();
        assertThat(BrokenFiles.compile(Map.of("Checkout", CHECKOUT_IF_KAKAO)).ok()).isTrue();
        assertThat(poly.changed()).isEmpty();
        assertThat(poly.added()).containsExactly("KakaoPayment");
        assertThat(ifs.changed()).containsExactly("Checkout");

        CiOutput.record(LESSON, "add-payment", "[코드 checkoutIf에 카카오페이 더하기]\n" + ifs.describe()
                + "\n\n[코드 checkout에 카카오페이 더하기]\n" + poly.describe());
    }
}
