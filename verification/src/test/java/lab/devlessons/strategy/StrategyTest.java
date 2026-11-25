package lab.devlessons.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import lab.devlessons.support.BrokenFiles;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.SourceChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 레슨 "전략 패턴"(strategy-pattern)의 주장을 확인한다.
 */
class StrategyTest {

    private static final String LESSON = "strategy-pattern";

    interface ShippingFeePolicy {
        int fee(int total, int boxes);
    }

    static class BasicFee implements ShippingFeePolicy {
        public int fee(int total, int boxes) {
            return total >= 50000 ? 0 : 3000;
        }
    }

    static class FreeFee implements ShippingFeePolicy {
        public int fee(int total, int boxes) {
            return 0;
        }
    }

    // ── 코드 context ──
    static class Checkout {
        private ShippingFeePolicy policy;

        Checkout(ShippingFeePolicy policy) {
            this.policy = policy;
        }

        void change(ShippingFeePolicy p) {
            this.policy = p;
        }

        int pay(int total, int boxes) {
            return total + policy.fee(total, boxes);
        }
    }

    @Test
    @DisplayName("코드 context: 기본 정책으로 3만 원(상자 2개)은 33000, FreeFee로 바꾸면 30000")
    void swapStrategyAtRuntime() {
        Checkout checkout = new Checkout(new BasicFee());
        int first = checkout.pay(30000, 2);
        checkout.change(new FreeFee());
        int second = checkout.pay(30000, 2);

        assertThat(first).isEqualTo(33000);
        assertThat(second).isEqualTo(30000);
        assertThat(new Checkout(new FreeFee()).pay(40000, 1)).isEqualTo(40000);
        CiOutput.record(LESSON, "swap", "new Checkout(new BasicFee()).pay(30000, 2) → " + first
                + "\nchange(new FreeFee()) 뒤 pay(30000, 2)    → " + second);
    }

    @Test
    @DisplayName("지역별 배송비를 더하면 if판은 계산 클래스를, 전략판은 조립 코드를 고치고 RegionFee가 생긴다")
    void addingPolicy() {
        Map<String, String> ifBefore = Map.of("ShippingFeeCalculator", """
                class ShippingFeeCalculator {
                  String mode = "BASIC";
                  int fee(int total, int boxes) {
                    if (mode.equals("FREE")) {
                      return 0;
                    } else if (mode.equals("BUNDLE")) {
                      return boxes * 3000;
                    }
                    return total >= 50000 ? 0 : 3000;
                  }
                }
                """, "Checkout", "class Checkout { ShippingFeeCalculator calc; }");
        Map<String, String> ifAfter = new HashMap<>(ifBefore);
        ifAfter.put("ShippingFeeCalculator", """
                class ShippingFeeCalculator {
                  String mode = "BASIC";
                  int fee(int total, int boxes) {
                    if (mode.equals("FREE")) {
                      return 0;
                    } else if (mode.equals("BUNDLE")) {
                      return boxes * 3000;
                    } else if (mode.equals("REGION")) {
                      return 5000;
                    }
                    return total >= 50000 ? 0 : 3000;
                  }
                }
                """);
        Map<String, String> before = new HashMap<>(Map.of(
                "ShippingFeePolicy", "interface ShippingFeePolicy { int fee(int total, int boxes); }",
                "BasicFee", "class BasicFee implements ShippingFeePolicy { public int fee(int t, int b) { return t >= 50000 ? 0 : 3000; } }",
                "FreeFee", "class FreeFee implements ShippingFeePolicy { public int fee(int t, int b) { return 0; } }",
                "BundleFee", "class BundleFee implements ShippingFeePolicy { public int fee(int t, int b) { return b * 3000; } }",
                "Checkout", "class Checkout { ShippingFeePolicy policy; Checkout(ShippingFeePolicy p) { policy = p; } }",
                "Wiring", "class Wiring { Checkout c = new Checkout(new BasicFee()); }"));
        Map<String, String> after = new HashMap<>(before);
        after.put("RegionFee", "class RegionFee implements ShippingFeePolicy { public int fee(int t, int b) { return 5000; } }");
        after.put("Wiring", "class Wiring { Checkout c = new Checkout(new RegionFee()); }");

        assertThat(BrokenFiles.compile(after).ok()).isTrue();
        SourceChange ifs = SourceChange.between(ifBefore, ifAfter);
        SourceChange strategy = SourceChange.between(before, after);

        assertThat(ifs.changed()).containsExactly("ShippingFeeCalculator");
        assertThat(strategy.changed()).containsExactly("Wiring");
        assertThat(strategy.added()).containsExactly("RegionFee");
        CiOutput.record(LESSON, "add-region", "[지역별 배송비 더하기]  (Wiring이 조립 코드예요)\n\nif로 나눈 코드\n" + ifs.describe()
                + "\n\n전략으로 나눈 코드\n" + strategy.describe());
    }

    @Test
    @DisplayName("람다 전략: bundle로 3만 원, 상자 3개는 39000이고 free는 클래스 FreeFee와 같다")
    void lambdaStrategy() {
        ShippingFeePolicy free = (t, b) -> 0;
        ShippingFeePolicy bundle = (t, b) -> b * 3000;

        assertThat(new Checkout(bundle).pay(30000, 3)).isEqualTo(39000);
        assertThat(new Checkout(free).pay(30000, 2)).isEqualTo(new Checkout(new FreeFee()).pay(30000, 2));
        CiOutput.record(LESSON, "lambda", "new Checkout(bundle).pay(30000, 3) → " + new Checkout(bundle).pay(30000, 3)
                + "\nnew Checkout(free).pay(30000, 2)   → " + new Checkout(free).pay(30000, 2));
    }

    record Order(long id, int total) {
    }

    @Test
    @DisplayName("Comparator는 정렬 방법이 아니라 무엇을 앞에 둘지만 정한다")
    void comparatorIsStrategy() {
        List<Order> orders = new ArrayList<>(List.of(new Order(1, 30000), new Order(2, 10000), new Order(3, 20000)));
        orders.sort(Comparator.comparing(Order::total));
        List<Long> byTotal = orders.stream().map(Order::id).toList();
        orders.sort(Comparator.comparing(Order::total).reversed());
        List<Long> byTotalDesc = orders.stream().map(Order::id).toList();

        assertThat(byTotal).containsExactly(2L, 3L, 1L);
        assertThat(byTotalDesc).containsExactly(1L, 3L, 2L);
        CiOutput.record(LESSON, "comparator", "금액 순       → id " + byTotal + "\n금액 큰 순    → id " + byTotalDesc);
    }

    // ── 코드 enumFee ──
    enum FeeType {
        BASIC {
            int fee(int t, int b) {
                return t >= 50000 ? 0 : 3000;
            }
        },
        FREE {
            int fee(int t, int b) {
                return 0;
            }
        };

        abstract int fee(int t, int b);
    }

    @Test
    @DisplayName("코드 enumFee: FeeType.FREE.fee(30000, 2)는 0, BASIC은 3000")
    void enumStrategy() {
        assertThat(FeeType.FREE.fee(30000, 2)).isZero();
        assertThat(FeeType.BASIC.fee(30000, 2)).isEqualTo(3000);
        assertThat(FeeType.valueOf("FREE").fee(30000, 2)).isZero();
    }

    // ── 스프링 ──
    @Component("basic")
    static class SpringBasicFee extends BasicFee {
    }

    @Component("free")
    static class SpringFreeFee extends FreeFee {
    }

    @Component
    static class FeeSelector {
        private final Map<String, ShippingFeePolicy> policies;

        FeeSelector(Map<String, ShippingFeePolicy> policies) {
            this.policies = policies;
        }

        int fee(String mode, int total, int boxes) {
            ShippingFeePolicy p = policies.get(mode);
            if (p == null) {
                throw new IllegalArgumentException("없는 정책: " + mode);
            }
            return p.fee(total, boxes);
        }
    }

    @Test
    @DisplayName("Map<String, ShippingFeePolicy>로 받으면 키가 빈 이름이고, 이름으로 골라 계산한다")
    void springMapInjection() {
        try (var ctx = new AnnotationConfigApplicationContext(SpringBasicFee.class, SpringFreeFee.class,
                FeeSelector.class)) {
            FeeSelector selector = ctx.getBean(FeeSelector.class);

            assertThat(selector.policies.keySet()).containsExactlyInAnyOrder("basic", "free");
            assertThat(selector.fee("basic", 30000, 2)).isEqualTo(3000);
            assertThat(selector.fee("free", 30000, 2)).isZero();
            assertThatThrownBy(() -> selector.fee("region", 30000, 2)).isInstanceOf(IllegalArgumentException.class);
            CiOutput.record(LESSON, "spring-map", "주입받은 맵의 키 → " + new TreeSet<>(selector.policies.keySet())
                    + "\nfee(\"basic\", 30000, 2)  → " + selector.fee("basic", 30000, 2)
                    + "\nfee(\"free\", 30000, 2)   → " + selector.fee("free", 30000, 2)
                    + "\nfee(\"region\", 30000, 2) → IllegalArgumentException: 없는 정책: region");
        }
    }
}
