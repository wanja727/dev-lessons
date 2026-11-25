package lab.devlessons.immutable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.JavaSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * 레슨 "불변 객체"(J9)의 주장을 검증한다. 순수 JUnit 테스트, Jackson, 스프링 부트의 설정 바인딩.
 */
class ImmutableObjectTest {

    private static final String LESSON = "immutable-object";

    /** 코드 predict: final이고 setter가 없지만 넘겨받은 리스트를 그대로 가진다 */
    static final class Order {
        private final List<String> items;

        Order(List<String> items) {
            this.items = items;
        }

        List<String> getItems() {
            return items;
        }
    }

    /** 코드 copied: 받을 때 List.copyOf로 복사한다 */
    static final class CopiedOrder {
        private final List<String> items;

        CopiedOrder(List<String> items) {
            this.items = List.copyOf(items);
        }

        List<String> getItems() {
            return items;
        }
    }

    @Test
    @DisplayName("코드 predict: final 필드여도 바깥 리스트를 고치면 주문이 바뀐다(2). List.copyOf로 받으면 1")
    void predict() {
        List<String> cart = new ArrayList<>();
        cart.add("키보드");
        Order order = new Order(cart);
        CopiedOrder copied = new CopiedOrder(cart);

        cart.add("마우스");

        assertThat(order.getItems()).hasSize(2);
        assertThat(copied.getItems()).hasSize(1);
        CiOutput.record(LESSON, "predict", String.valueOf(order.getItems().size()));
        CiOutput.record(LESSON, "copied", String.valueOf(copied.getItems().size()));
    }

    @Test
    @DisplayName("List.copyOf로 만든 리스트에 add하면 UnsupportedOperationException")
    void copyOfIsUnmodifiable() {
        CopiedOrder order = new CopiedOrder(List.of("키보드"));
        assertThatThrownBy(() -> order.getItems().add("모니터"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("unmodifiableList는 원본을 비추는 창이라 원본의 변경이 보이고, List.copyOf는 보이지 않는다")
    void viewVersusCopy() {
        List<String> src = new ArrayList<>(List.of("A"));
        List<String> view = Collections.unmodifiableList(src);
        List<String> copy = List.copyOf(src);
        src.add("B");

        assertThat(view).hasSize(2);
        assertThat(copy).hasSize(1);
        assertThatThrownBy(() -> view.add("C")).isInstanceOf(UnsupportedOperationException.class);
        CiOutput.record(LESSON, "view-copy", """
                원본에 "B"를 더한 뒤
                unmodifiableList(src).size() → %d
                List.copyOf(src).size()      → %d""".formatted(view.size(), copy.size()));
    }

    /** 세 가지 지키는 방법을 같은 두 동작으로 돌린다 (위젯의 값) */
    static final class ViewOrder {
        private final List<String> items;

        ViewOrder(List<String> items) {
            this.items = Collections.unmodifiableList(items);
        }

        List<String> getItems() {
            return items;
        }
    }

    @Test
    @DisplayName("세 방법: 그대로 받음, unmodifiableList로 받음, List.copyOf로 받음")
    void threeWays() {
        List<String> cart1 = new ArrayList<>(List.of("키보드"));
        Order plain = new Order(cart1);
        cart1.add("마우스");
        plain.getItems().add("모니터");

        List<String> cart2 = new ArrayList<>(List.of("키보드"));
        ViewOrder view = new ViewOrder(cart2);
        cart2.add("마우스");
        Throwable viewAdd = null;
        try {
            view.getItems().add("모니터");
        } catch (UnsupportedOperationException e) {
            viewAdd = e;
        }

        List<String> cart3 = new ArrayList<>(List.of("키보드"));
        CopiedOrder copied = new CopiedOrder(cart3);
        cart3.add("마우스");
        Throwable copiedAdd = null;
        try {
            copied.getItems().add("모니터");
        } catch (UnsupportedOperationException e) {
            copiedAdd = e;
        }

        assertThat(plain.getItems()).containsExactly("키보드", "마우스", "모니터");
        assertThat(view.getItems()).containsExactly("키보드", "마우스");
        assertThat(viewAdd).isNotNull();
        assertThat(copied.getItems()).containsExactly("키보드");
        assertThat(copiedAdd).isNotNull();
        CiOutput.record(LESSON, "three-ways", """
                그대로 받음          → %s
                unmodifiableList    → %s
                List.copyOf         → %s""".formatted(plain.getItems(), view.getItems(), copied.getItems()));
    }

    @Test
    @DisplayName("final 배열과 final 리스트는 내용을 바꿀 수 있고, 다시 대입하면 컴파일되지 않는다")
    void finalIsNotImmutable() {
        final int[] scores = {1, 2, 3};
        scores[0] = 9;
        final List<String> items = new ArrayList<>();
        items.add("A");

        JavaSource.Result r = JavaSource.compile("Shop", """
                import java.util.*;
                public class Shop {
                  void run() {
                    final List<String> items = new ArrayList<>();
                    items = List.of("A");
                  }
                }
                """);

        assertThat(scores[0]).isEqualTo(9);
        assertThat(items).containsExactly("A");
        assertThat(r.ok()).isFalse();
        CiOutput.record(LESSON, "final", """
                final int[] scores = {1, 2, 3};
                scores[0] = 9;        → 됨, scores[0] = %d
                final List items; items.add("A") → 됨
                items = List.of("A")  → error: %s""".formatted(scores[0], r.errors().get(0)));
    }

    @Test
    @DisplayName("LocalDate.plusDays는 새 객체를 돌려준다. 받지 않으면 원래 날짜는 그대로다")
    void localDateIsImmutable() {
        LocalDate d = LocalDate.of(2026, 1, 31);
        d.plusDays(1);
        assertThat(d).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(d.plusDays(1)).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    record Money(long amount, String currency) {
        Money plus(long more) {
            return new Money(amount + more, currency);
        }
    }

    @Test
    @DisplayName("record는 구성 요소가 같으면 equals가 true이고 hashCode가 같다. ==는 false다")
    void recordEquality() {
        Money a = new Money(1000, "KRW");
        Money b = new Money(1000, "KRW");
        Money c = a;
        c = c.plus(500);

        assertThat(a == b).isFalse();
        assertThat(a.equals(b)).isTrue();
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.amount()).isEqualTo(1000);
        assertThat(c.amount()).isEqualTo(1500);
        CiOutput.record(LESSON, "record", """
                a == b        → %s
                a.equals(b)   → %s
                a.hashCode() == b.hashCode() → %s
                a.toString()  → %s""".formatted(a == b, a.equals(b), a.hashCode() == b.hashCode(), a));
    }

    @Test
    @DisplayName("record의 필드에는 대입할 수 없고, 접근자 이름에 get이 없다")
    void recordAccessors() {
        String header = "record Point(int x, int y) {}\n";
        JavaSource.Result assign = JavaSource.compile("Shop", header
                + "public class Shop { void run(Point p) { p.x = 5; } }");
        JavaSource.Result getter = JavaSource.compile("Shop", header
                + "public class Shop { int run(Point p) { return p.getX(); } }");
        JavaSource.Result accessor = JavaSource.compile("Shop", header
                + "public class Shop { int run(Point p) { return p.x(); } }");

        assertThat(assign.ok()).isFalse();
        assertThat(getter.ok()).isFalse();
        assertThat(accessor.ok()).isTrue();
    }

    record PlainResponse(long id, List<String> items) {
    }

    record CopiedResponse(long id, List<String> items) {
        CopiedResponse {
            items = List.copyOf(items);
        }
    }

    @Test
    @DisplayName("간결한 생성자가 없는 record는 넘겨받은 리스트를 그대로 가리키고, 있으면 복사본을 가진다")
    void recordWithList() {
        List<String> entityItems = new ArrayList<>(List.of("키보드"));
        PlainResponse plain = new PlainResponse(1, entityItems);
        plain.items().add("모니터");

        List<String> entityItems2 = new ArrayList<>(List.of("키보드"));
        CopiedResponse copied = new CopiedResponse(1, entityItems2);
        Throwable error = null;
        try {
            copied.items().add("모니터");
        } catch (UnsupportedOperationException e) {
            error = e;
        }

        assertThat(entityItems).containsExactly("키보드", "모니터");
        assertThat(entityItems2).containsExactly("키보드");
        assertThat(error).isInstanceOf(UnsupportedOperationException.class);
        CiOutput.record(LESSON, "record-list", """
                간결한 생성자 없음
                response.items().add("모니터") 뒤 엔티티의 리스트 → %s

                간결한 생성자에서 List.copyOf
                response.items().add("모니터") → %s
                엔티티의 리스트 → %s""".formatted(entityItems, error.getClass().getSimpleName(), entityItems2));
    }

    record OrderRequest(Long productId, Integer quantity) {
    }

    @Test
    @DisplayName("Jackson은 record를 JSON에서 만들고 JSON으로 쓴다")
    void jacksonRecord() throws Exception {
        ObjectMapper json = new ObjectMapper();
        OrderRequest read = json.readValue("{\"productId\":7,\"quantity\":2}", OrderRequest.class);
        String written = json.writeValueAsString(new CopiedResponse(1, List.of("키보드")));

        assertThat(read).isEqualTo(new OrderRequest(7L, 2));
        assertThat(written).isEqualTo("{\"id\":1,\"items\":[\"키보드\"]}");
        CiOutput.record(LESSON, "jackson", """
                읽기: {"productId":7,"quantity":2}
                → %s

                쓰기: new OrderResponse(1, List.of("키보드"))
                → %s""".formatted(read.toString().replace("OrderRequest", "OrderRequest"), written));
    }

    @ConfigurationProperties("pay")
    record PayProperties(String url, Duration timeout) {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PayProperties.class)
    static class PayConfig {
    }

    @Test
    @DisplayName("@ConfigurationProperties가 붙은 record에 설정 값이 생성자로 들어온다")
    void configurationPropertiesRecord() {
        new ApplicationContextRunner()
                .withUserConfiguration(PayConfig.class)
                .withPropertyValues("pay.url=https://pay.example.com", "pay.timeout=3s")
                .run(ctx -> {
                    PayProperties props = ctx.getBean(PayProperties.class);
                    assertThat(props.url()).isEqualTo("https://pay.example.com");
                    assertThat(props.timeout()).isEqualTo(Duration.ofSeconds(3));
                    CiOutput.record(LESSON, "config", """
                            pay.url=https://pay.example.com
                            pay.timeout=3s
                            → %s""".formatted(props));
                });
    }
}
