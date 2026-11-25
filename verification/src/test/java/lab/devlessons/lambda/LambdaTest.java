package lab.devlessons.lambda;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.JavaSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "람다와 함수형 인터페이스"(J12)의 주장을 검증한다.
 * 컴파일되는지는 테스트 안에서 javac(자바 21)로 확인한다. JdbcTemplate은 LambdaJdbcTest가 본다.
 */
class LambdaTest {

    private static final String LESSON = "lambda";

    record Order(long id, int price, String name) {
        int getPrice() {
            return price;
        }

        long getId() {
            return id;
        }
    }

    static class ExpensiveFilter implements Predicate<Order> {
        public boolean test(Order o) {
            return o.getPrice() >= 10000;
        }
    }

    @Test
    @DisplayName("클래스, 익명 클래스, 람다로 만든 세 Predicate는 같은 결과를 준다")
    void threeWaysSameResult() {
        Predicate<Order> p1 = new ExpensiveFilter();
        Predicate<Order> p2 = new Predicate<>() {
            public boolean test(Order o) {
                return o.getPrice() >= 10000;
            }
        };
        Predicate<Order> p3 = o -> o.getPrice() >= 10000;
        Order cheap = new Order(1, 5000, "펜");
        Order pricey = new Order(2, 15000, "키보드");

        for (Predicate<Order> p : List.of(p1, p2, p3)) {
            assertThat(p.test(cheap)).isFalse();
            assertThat(p.test(pricey)).isTrue();
        }
        CiOutput.record(LESSON, "three", """
                금액      클래스  익명 클래스  람다
                5000     %s  %s      %s
                15000    %s   %s       %s""".formatted(p1.test(cheap), p2.test(cheap), p3.test(cheap),
                p1.test(pricey), p2.test(pricey), p3.test(pricey)));
    }

    @Test
    @DisplayName("추상 메서드가 둘인 인터페이스에 람다를 대입하면 컴파일되지 않는다")
    void notFunctional() {
        JavaSource.Result r = JavaSource.compile("Shop", """
                interface Validator {
                  boolean validate(Object o);
                  String name();
                }
                public class Shop { Validator v = o -> o != null; }
                """);
        assertThat(r.ok()).isFalse();
        assertThat(r.errors()).anyMatch(e -> e.contains("not a functional interface"));
        CiOutput.record(LESSON, "not-functional", "error: " + r.errors().get(0).lines().findFirst().orElse(""));
    }

    @Test
    @DisplayName("람다는 정의할 때가 아니라 get()을 부를 때마다 실행된다")
    void lazy() {
        List<String> log = new ArrayList<>();
        Supplier<Order> s = () -> {
            log.add("주문 만드는 중");
            return new Order(0, 0, "");
        };
        log.add("람다를 정의함");
        Order a = s.get();
        Order b = s.get();

        assertThat(log).containsExactly("람다를 정의함", "주문 만드는 중", "주문 만드는 중");
        assertThat(a).isNotSameAs(b);
        CiOutput.record(LESSON, "lazy", String.join("\n", log));
    }

    @Test
    @DisplayName("removeIf에 넘긴 람다는 원소마다 한 번 실행된다")
    void calledPerElement() {
        List<String> log = new ArrayList<>();
        List<Order> orders = new ArrayList<>(List.of(new Order(1, 500, "a"), new Order(2, 3000, "b"),
                new Order(3, 800, "c"), new Order(4, 9000, "d"), new Order(5, 100, "e")));
        orders.removeIf(o -> {
            log.add("test " + o.getId());
            return o.getPrice() < 1000;
        });
        assertThat(log).hasSize(5);
        assertThat(orders).extracting(Order::getId).containsExactly(2L, 4L);
    }

    static final String CAPTURE_HEADER = """
            import java.util.*;
            import java.util.function.*;
            public class C {
              int count;
              void run(List<Integer> orders) {
            """;

    static boolean compiles(String body) {
        return JavaSource.compile("C", CAPTURE_HEADER + body + "\n  }\n}\n").ok();
    }

    @Test
    @DisplayName("코드 predict(람다 안에서 바깥 지역 변수에 누적)는 컴파일되지 않는다")
    void predict() {
        JavaSource.Result r = JavaSource.compile("C", CAPTURE_HEADER + """
                    int total = 0;
                    orders.forEach(o -> total += o);
                    System.out.println(total);
                  }
                }
                """);
        assertThat(r.ok()).isFalse();
        assertThat(r.errors()).anyMatch(e -> e.contains("effectively final"));
        CiOutput.record(LESSON, "predict", "error: " + r.errors().get(0).lines().findFirst().orElse(""));
    }

    @Test
    @DisplayName("위젯의 일곱 조각이 적힌 대로 컴파일되거나 컴파일되지 않는다")
    void captureRules() {
        Map<String, Boolean> expected = new LinkedHashMap<>();
        expected.put("int min = 10000;\n    Predicate<Integer> p = o -> o >= min;", true);
        expected.put("int min = 10000;\n    Predicate<Integer> p = o -> o >= min;\n    min = 20000;", false);
        expected.put("int total = 0;\n    orders.forEach(o -> total += o);", false);
        expected.put("List<Integer> result = new ArrayList<>();\n    orders.forEach(o -> result.add(o));", true);
        expected.put("orders.forEach(o -> this.count++);", true);
        expected.put("for (int i = 0; i < 3; i++) {\n      Runnable r = () -> System.out.println(i);\n    }", false);
        expected.put("for (Integer o : orders) {\n      Runnable r = () -> System.out.println(o);\n    }", true);

        StringBuilder text = new StringBuilder();
        expected.forEach((body, ok) -> {
            boolean actual = compiles(body);
            assertThat(actual).as(body).isEqualTo(ok);
            text.append(actual ? "컴파일됨    " : "컴파일 오류 ").append(body.replace("\n    ", " ").replace("\n      ", " ")
                    .replace("\n    }", " }")).append('\n');
        });
        CiOutput.record(LESSON, "capture", text.toString().strip());
    }

    @Test
    @DisplayName("메서드 참조 네 가지는 나란히 적은 람다와 같은 결과를 준다")
    void methodReferences() {
        String name = "kim";
        Order order = new Order(1, 12000, "키보드");

        Function<String, Integer> a = Integer::parseInt;
        Function<String, Integer> aL = s -> Integer.parseInt(s);
        Supplier<Integer> b = name::length;
        Supplier<Integer> bL = () -> name.length();
        Function<Order, Integer> c = Order::getPrice;
        Function<Order, Integer> cL = o -> o.getPrice();
        Supplier<List<Order>> d = ArrayList::new;
        Supplier<List<Order>> dL = () -> new ArrayList<>();

        assertThat(a.apply("42")).isEqualTo(aL.apply("42"));
        assertThat(b.get()).isEqualTo(bL.get());
        assertThat(c.apply(order)).isEqualTo(cL.apply(order));
        assertThat(d.get()).isEqualTo(dL.get());
        CiOutput.record(LESSON, "refs", """
                Integer::parseInt  .apply("42") → %d
                name::length       .get()       → %d
                Order::getPrice    .apply(키보드) → %d
                ArrayList::new     .get()       → %s""".formatted(a.apply("42"), b.get(), c.apply(order), d.get()));
    }

    @Test
    @DisplayName("Comparator.comparing(Order::getPrice).reversed()는 금액이 큰 주문을 앞에 둔다")
    void comparator() {
        List<Order> orders = new ArrayList<>(List.of(new Order(1, 3000, "a"), new Order(2, 1000, "b"),
                new Order(3, 2000, "c")));
        orders.sort(Comparator.comparing(Order::getPrice).reversed());

        assertThat(orders).extracting(Order::getPrice).containsExactly(3000, 2000, 1000);
        CiOutput.record(LESSON, "sort", "정렬 전 금액: [3000, 1000, 2000]\n정렬 후 금액: "
                + orders.stream().map(Order::getPrice).toList());
    }

    @Test
    @DisplayName("Consumer 람다 안에서 체크 예외를 던지는 메서드를 그대로 부르면 컴파일되지 않는다")
    void checkedInLambda() {
        JavaSource.Result r = JavaSource.compile("Cleaner", """
                import java.nio.file.*;
                import java.util.*;
                public class Cleaner {
                  void run(List<Path> paths) {
                    paths.forEach(p -> Files.delete(p));
                  }
                }
                """);
        assertThat(r.ok()).isFalse();
        assertThat(r.errors()).anyMatch(e -> e.contains("unreported exception java.io.IOException"));
        CiOutput.record(LESSON, "checked", "error: " + r.errors().get(0).lines().findFirst().orElse(""));
    }

    @Test
    @DisplayName("고친 코드: 스트림으로 합계를 돌려받는다")
    void streamSum() {
        List<Order> orders = List.of(new Order(1, 10000, "a"), new Order(2, 20000, "b"));
        int total = orders.stream().mapToInt(Order::getPrice).sum();
        assertThat(total).isEqualTo(30000);
        CiOutput.record(LESSON, "sum", "orders.stream().mapToInt(Order::getPrice).sum()\n→ " + total);
    }
}
