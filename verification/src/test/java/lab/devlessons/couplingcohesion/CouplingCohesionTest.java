package lab.devlessons.couplingcohesion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import lab.devlessons.support.BrokenFiles;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.SourceChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "결합도와 응집도"(coupling-cohesion)의 주장을 확인한다.
 * 결합도는 "클래스 하나를 바꾸면 몇 파일이 깨지나"를 컴파일해서(BrokenFiles),
 * 응집도는 "규칙 하나를 바꾸면 몇 파일을 고치나"를 바꾸기 전후 소스를 비교해서(SourceChange) 센다.
 */
class CouplingCohesionTest {

    private static final String LESSON = "coupling-cohesion";
    private static final String[] SCREENS = {"OrderPage", "PaymentPage", "ReceiptPage", "DeliveryPage", "AdminPage"};

    private static String address(String getter) {
        return """
                class Address {
                  private final String zip;
                  Address(String zip) { this.zip = zip; }
                  String %s() { return zip; }
                }
                """.formatted(getter);
    }

    private static final String MEMBER = """
            class Member {
              private final Address address;
              Member(Address a) { this.address = a; }
              Address getAddress() { return address; }
            }
            """;

    private static String order(String getter, boolean demeter) {
        return """
                class Order {
                  private final Member member;
                  Order(Member m) { this.member = m; }
                  Member getMember() { return member; }
                %s
                }
                """.formatted(demeter ? """
                  boolean isRemote() {
                    return member.getAddress().%s().startsWith("63");
                  }
                """.formatted(getter) : "");
    }

    private static String screen(String name, boolean demeter) {
        String body = demeter
                ? "boolean remote = order.isRemote();"
                : "String zip = order.getMember().getAddress().getZipCode();\n    boolean remote = zip.startsWith(\"63\");";
        return """
                class %s {
                  void show(Order order) {
                    %s
                  }
                }
                """.formatted(name, body);
    }

    private static Map<String, String> chainCode(String getter, boolean demeter) {
        Map<String, String> src = new HashMap<>();
        src.put("Address", address(getter));
        src.put("Member", MEMBER);
        src.put("Order", order("getZipCode", demeter));
        for (String s : SCREENS) {
            src.put(s, screen(s, demeter));
        }
        return src;
    }

    @Test
    @DisplayName("getZipCode를 getPostalCode로 바꾸면 줄줄이 호출하는 화면 다섯이 깨지고, isRemote()를 쓰면 Order 하나만 깨진다")
    void renameBreaksChainCallers() {
        assertThat(BrokenFiles.compile(chainCode("getZipCode", false)).ok()).isTrue();
        assertThat(BrokenFiles.compile(chainCode("getZipCode", true)).ok()).isTrue();

        BrokenFiles chain = BrokenFiles.compile(chainCode("getPostalCode", false));
        BrokenFiles demeter = BrokenFiles.compile(chainCode("getPostalCode", true));

        assertThat(chain.files()).containsExactlyInAnyOrder(SCREENS);
        assertThat(demeter.files()).containsExactly("Order");

        CiOutput.record(LESSON, "rename", "[Address.getZipCode()를 getPostalCode()로 바꾸기]\n\n"
                + "코드 chain을 쓰는 화면 다섯\n→ 컴파일 오류 파일 " + chain.files().size() + "개: "
                + String.join(", ", chain.files()) + "\n\n"
                + "코드 demeter를 쓰는 화면 다섯\n→ 컴파일 오류 파일 " + demeter.files().size() + "개: "
                + String.join(", ", demeter.files()));
    }

    // ── 응집도: 배송비 규칙 ──

    private static String scattered(String cls, int fee, boolean remote) {
        return """
                class %s {
                  int fee(Order order) {
                    int fee = order.total() >= 50000 ? 0 : %d;
                %s    return fee;
                  }
                }
                """.formatted(cls, fee, remote ? "    if (order.isRemote()) fee += 3000;\n" : "");
    }

    private static Map<String, String> scatteredCode(int fee) {
        return Map.of(
                "OrderPage", scattered("OrderPage", fee, false),
                "Payment", scattered("Payment", fee, true),
                "Receipt", scattered("Receipt", fee, false));
    }

    private static String cohesiveOrder(int fee) {
        return """
                record Order(int total, boolean isRemote) {
                  int shippingFee() {
                    int fee = total >= 50000 ? 0 : %d;
                    if (isRemote) fee += 3000;
                    return fee;
                  }
                }
                """.formatted(fee);
    }

    private static Map<String, String> cohesiveCode(int fee) {
        Map<String, String> src = new HashMap<>();
        src.put("Order", cohesiveOrder(fee));
        for (String cls : new String[] {"OrderPage", "Payment", "Receipt"}) {
            src.put(cls, """
                    class %s {
                      int fee(Order order) {
                        return order.shippingFee();
                      }
                    }
                    """.formatted(cls));
        }
        return src;
    }

    @Test
    @DisplayName("배송비를 3500원으로 올리면 코드 scattered는 세 파일, 코드 cohesive는 Order 하나를 고친다")
    void changingFeeRule() {
        SourceChange scattered = SourceChange.between(scatteredCode(3000), scatteredCode(3500));
        SourceChange cohesive = SourceChange.between(cohesiveCode(3000), cohesiveCode(3500));

        assertThat(scattered.changed()).containsExactly("OrderPage", "Payment", "Receipt");
        assertThat(cohesive.changed()).containsExactly("Order");

        CiOutput.record(LESSON, "fee-change", "[기본 배송비 3000원을 3500원으로]\n\n코드 scattered\n" + scattered.describe()
                + "\n\n코드 cohesive\n" + cohesive.describe());
    }

    // 영수증만 빠뜨린 코드를 실제로 돌린다
    record Bill(int total, boolean isRemote) {
    }

    static int paymentFee(Bill o) {
        int fee = o.total() >= 50000 ? 0 : 3500;
        if (o.isRemote()) {
            fee += 3000;
        }
        return fee;
    }

    static int receiptFeeForgotten(Bill o) {
        return o.total() >= 50000 ? 0 : 3000;
    }

    @Test
    @DisplayName("결제만 고치고 영수증을 빠뜨리면 3만 원 주문의 배송비가 결제 3500, 영수증 3000으로 어긋난다")
    void forgottenReceipt() {
        Bill order = new Bill(30000, false);

        assertThat(paymentFee(order)).isEqualTo(3500);
        assertThat(receiptFeeForgotten(order)).isEqualTo(3000);

        Bill remote = new Bill(30000, true);
        CiOutput.record(LESSON, "forgot", "3만 원 주문, 도서산간 아님\n결제의 배송비   → " + paymentFee(order)
                + "\n영수증의 배송비 → " + receiptFeeForgotten(order)
                + "\n\n3만 원 주문, 도서산간\n결제의 배송비   → " + paymentFee(remote)
                + "\n영수증의 배송비 → " + receiptFeeForgotten(remote));
    }

    // ── 실무: 쿠폰 규칙 ──

    private static String couponDirect(int min) {
        return """
                class OrderService {
                  CouponRepository coupons;
                  void order(long couponId, int total) {
                    Coupon c = coupons.find(couponId);
                    if (c.isExpired() || total < %d) {
                      throw new IllegalStateException();
                    }
                    c.setUsed(true);
                  }
                }
                """.formatted(min);
    }

    private static final String COUPON_DATA = """
            class Coupon {
              boolean used; boolean expired;
              boolean isExpired() { return expired; }
              void setUsed(boolean u) { used = u; }
            }
            """;

    private static String couponTell(int min) {
        return """
                class Coupon {
                  boolean used; boolean expired;
                  void use(int orderTotal) {
                    if (expired || orderTotal < %d) {
                      throw new IllegalStateException();
                    }
                    used = true;
                  }
                }
                """.formatted(min);
    }

    private static final String ORDER_TELL = """
            class OrderService {
              CouponRepository coupons;
              void order(long couponId, int total) {
                coupons.find(couponId).use(total);
              }
            }
            """;

    private static final String REPO = """
            interface CouponRepository {
              Coupon find(long id);
            }
            """;

    @Test
    @DisplayName("쿠폰의 최소 주문 금액을 바꾸면, 주문 서비스가 쿠폰을 직접 다루는 코드는 OrderService를, 쿠폰에 시키는 코드는 Coupon을 고친다")
    void couponRule() {
        Map<String, String> directBefore = Map.of("OrderService", couponDirect(10000), "Coupon", COUPON_DATA,
                "CouponRepository", REPO);
        Map<String, String> directAfter = Map.of("OrderService", couponDirect(20000), "Coupon", COUPON_DATA,
                "CouponRepository", REPO);
        Map<String, String> tellBefore = Map.of("OrderService", ORDER_TELL, "Coupon", couponTell(10000),
                "CouponRepository", REPO);
        Map<String, String> tellAfter = Map.of("OrderService", ORDER_TELL, "Coupon", couponTell(20000),
                "CouponRepository", REPO);

        assertThat(BrokenFiles.compile(directAfter).ok()).isTrue();
        assertThat(BrokenFiles.compile(tellAfter).ok()).isTrue();
        SourceChange direct = SourceChange.between(directBefore, directAfter);
        SourceChange tell = SourceChange.between(tellBefore, tellAfter);

        assertThat(direct.changed()).containsExactly("OrderService");
        assertThat(tell.changed()).containsExactly("Coupon");

        CiOutput.record(LESSON, "coupon", "[쿠폰 최소 주문 금액 1만 원을 2만 원으로]\n\n주문 서비스가 쿠폰을 직접 다룰 때\n"
                + direct.describe() + "\n\n쿠폰에 시킬 때\n" + tell.describe());
    }
}
