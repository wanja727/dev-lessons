package lab.devlessons.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lab.devlessons.support.BrokenFiles;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.SourceChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 레슨 "팩토리"(factory)의 주장을 확인한다.
 */
class FactoryTest {

    private static final String LESSON = "factory";

    @Test
    @SuppressWarnings({"removal", "deprecation"})
    @DisplayName("Integer.valueOf(100) 두 번은 같은 객체, new Integer(100)과는 다르고, valueOf(1000) 두 번은 다르다")
    void valueOfCaches() {
        Integer a = Integer.valueOf(100);
        Integer b = Integer.valueOf(100);
        Integer c = new Integer(100);
        Integer big1 = Integer.valueOf(1000);
        Integer big2 = Integer.valueOf(1000);

        assertThat(a == b).isTrue();
        assertThat(a == c).isFalse();
        assertThat(big1 == big2).isFalse();
        assertThat(Boolean.valueOf("true") == Boolean.valueOf("true")).isTrue();
        CiOutput.record(LESSON, "valueof", "a == b                → " + (a == b) + "\na == c                → " + (a == c)
                + "\nvalueOf(1000) == valueOf(1000) → " + (big1 == big2));
    }

    @Test
    @DisplayName("List.of의 실제 클래스: 원소 1개와 2개는 List12, 3개 이상은 ListN")
    void listOfHiddenClasses() {
        String one = List.of("a").getClass().getSimpleName();
        String two = List.of("a", "b").getClass().getSimpleName();
        String three = List.of("a", "b", "c").getClass().getSimpleName();

        assertThat(one).isEqualTo("List12");
        assertThat(two).isEqualTo("List12");
        assertThat(three).isEqualTo("ListN");
        CiOutput.record(LESSON, "listof", "List.of(\"a\")           → " + one + "\nList.of(\"a\", \"b\")      → " + two
                + "\nList.of(\"a\", \"b\", \"c\") → " + three);
    }

    private static final String ORDER = """
            import java.util.List;
            class Order {
              private final int total;
              private Order(int total) { this.total = total; }
              static Order create(List<Integer> prices) {
                return new Order(prices.stream().mapToInt(Integer::intValue).sum());
              }
              int total() { return total; }
            }
            """;

    @Test
    @DisplayName("private 생성자를 밖에서 부르면 컴파일 오류, private 생성자만 있는 클래스를 상속해도 컴파일 오류")
    void privateConstructor() {
        BrokenFiles outside = BrokenFiles.compile(Map.of("Order", ORDER, "Shop", """
                class Shop {
                  Order order() { return new Order(0); }
                }
                """));
        BrokenFiles subclass = BrokenFiles.compile(Map.of("Order", ORDER, "VipOrder", """
                class VipOrder extends Order {
                  VipOrder() { super(0); }
                }
                """));
        BrokenFiles ok = BrokenFiles.compile(Map.of("Order", ORDER, "Shop", """
                class Shop {
                  Order order() { return Order.create(java.util.List.of(1000, 2000)); }
                }
                """));

        assertThat(outside.files()).containsExactly("Shop");
        assertThat(outside.messages().get(0)).contains("has private access");
        assertThat(subclass.files()).containsExactly("VipOrder");
        assertThat(ok.ok()).isTrue();
        CiOutput.record(LESSON, "private", "[다른 클래스에서 new Order(0)]\nerror: "
                + outside.messages().get(0).substring("Shop: ".length())
                + "\n\n[class VipOrder extends Order]\nerror: " + subclass.messages().get(0).substring("VipOrder: ".length())
                + "\n\n[Order.create(List.of(1000, 2000))]\n→ 컴파일 성공");
    }

    // ── 업체를 바꿀 때 고치는 파일 ──

    private static final String[] SERVICES = {"SignupService", "OrderService", "DeliveryService", "RefundService",
            "CouponService", "ReviewService", "PointService"};

    private static Map<String, String> direct(String impl) {
        Map<String, String> src = new HashMap<>();
        for (String s : SERVICES) {
            src.put(s, "class %s { Notifier n = new %s(); }".formatted(s, impl));
        }
        return src;
    }

    private static Map<String, String> factory(String impl) {
        Map<String, String> src = new HashMap<>();
        for (String s : SERVICES) {
            src.put(s, "class %s { Notifier n = NotifierFactory.of(Channel.SMS); }".formatted(s));
        }
        src.put("NotifierFactory", """
                class NotifierFactory {
                  static Notifier of(Channel ch) {
                    return switch (ch) {
                      case EMAIL -> new MailNotifier();
                      case SMS -> new %s();
                    };
                  }
                }
                """.formatted(impl));
        return src;
    }

    @Test
    @DisplayName("서비스 일곱 곳이 직접 new하면 업체를 바꿀 때 일곱 파일, 팩토리로 모으면 팩토리 한 파일과 새 구현만 바뀐다")
    void switchVendor() {
        Map<String, String> factoryAfter = factory("SolapiSmsNotifier");
        factoryAfter.put("SolapiSmsNotifier", "class SolapiSmsNotifier implements Notifier {}");
        Map<String, String> factoryBefore = factory("AligoSmsNotifier");
        factoryBefore.put("AligoSmsNotifier", "class AligoSmsNotifier implements Notifier {}");
        factoryAfter.put("AligoSmsNotifier", "class AligoSmsNotifier implements Notifier {}");

        Map<String, String> compiled = new HashMap<>(factoryAfter);
        compiled.put("Notifier", "interface Notifier {}");
        compiled.put("MailNotifier", "class MailNotifier implements Notifier {}");
        compiled.put("Channel", "enum Channel { EMAIL, SMS }");
        assertThat(BrokenFiles.compile(compiled).ok()).isTrue();

        SourceChange d = SourceChange.between(direct("AligoSmsNotifier"), direct("SolapiSmsNotifier"));
        SourceChange f = SourceChange.between(factoryBefore, factoryAfter);

        assertThat(d.changed()).hasSize(7);
        assertThat(f.changed()).containsExactly("NotifierFactory");
        assertThat(f.added()).containsExactly("SolapiSmsNotifier");
        CiOutput.record(LESSON, "vendor", "[문자 업체를 바꾸기]\n\n서비스마다 직접 new\n고친 파일 " + d.changed().size() + "개 → "
                + String.join(", ", d.changed()) + "\n\n팩토리로 모았을 때\n고친 파일 → " + String.join(", ", f.changed())
                + "\n새로 만든 파일 → " + String.join(", ", f.added()) + "\n그대로인 서비스 → " + SERVICES.length + "개");
    }

    // ── @Bean 메서드 ──

    static class TossClient {
        final String key;

        TossClient(String key) {
            this.key = key;
        }
    }

    @Configuration
    static class PaymentConfig {
        @Bean
        TossClient tossClient() {
            return new TossClient("test-key");
        }
    }

    @Test
    @DisplayName("@Bean 메서드가 돌려준 객체가 빈이 되고, 두 번 꺼내도 같은 객체다")
    void beanMethodIsFactory() {
        try (var ctx = new AnnotationConfigApplicationContext(PaymentConfig.class)) {
            TossClient a = ctx.getBean(TossClient.class);
            TossClient b = ctx.getBean(TossClient.class);

            assertThat(a.key).isEqualTo("test-key");
            assertThat(a).isSameAs(b);
            CiOutput.record(LESSON, "bean", "ctx.getBean(TossClient.class).key → " + a.key
                    + "\n두 번 꺼낸 객체가 같은가 → " + (a == b));
        }
    }

    @Test
    @DisplayName("Optional.of(null)은 NullPointerException, ofNullable(null)은 빈 Optional")
    void optionalFactories() {
        String name = null;

        assertThatThrownBy(() -> Optional.of(name)).isInstanceOf(NullPointerException.class);
        assertThat(Optional.ofNullable(name)).isEmpty();
        CiOutput.record(LESSON, "optional", "Optional.of(null)         → NullPointerException\nOptional.ofNullable(null) → "
                + Optional.ofNullable(name));
    }
}
