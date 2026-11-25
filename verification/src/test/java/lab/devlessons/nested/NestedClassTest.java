package lab.devlessons.nested;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.JavaSource;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 레슨 "중첩 클래스와 익명 클래스"(J16)의 주장을 검증한다.
 * 숨은 바깥 참조는 자바 21의 javac가 만든 클래스 파일을 리플렉션으로 본다.
 */
class NestedClassTest {

    private static final String LESSON = "nested-class";

    // 코드 predict
    static class Order {
        String name = "주문";
        final List<String> printed = new ArrayList<>();

        void print(String s) {
            printed.add(s);
        }

        void run() {
            Runnable a = new Runnable() {
                String name = "익명";

                public void run() {
                    print(this.name);
                }
            };
            Runnable b = () -> print(this.name);

            a.run();
            b.run();
        }

        void runOuterThis() {
            Runnable a = new Runnable() {
                String name = "익명";

                public void run() {
                    print(Order.this.name);
                }
            };
            a.run();
        }
    }

    @Test
    @DisplayName("익명 클래스 안의 this는 익명 클래스의 객체이고, 람다 안의 this는 바깥 객체다")
    void thisDiffers() {
        Order order = new Order();
        order.run();
        Order other = new Order();
        other.runOuterThis();

        assertThat(order.printed).containsExactly("익명", "주문");
        assertThat(other.printed).containsExactly("주문");
        CiOutput.record(LESSON, "predict", "new Order().run()\n" + String.join("\n", order.printed));
        CiOutput.record(LESSON, "outer-this", "print(Order.this.name)으로 바꾼 익명 클래스 → " + other.printed.get(0));
    }

    // 코드 inner
    static class Shop {
        private int total = 10000;

        class Item {
            int share() {
                return total / 2;
            }
        }

        static class Line {
        }

        class Plain {
            int one() {
                return 1;
            }
        }
    }

    static String fields(Class<?> c) {
        String s = Arrays.stream(c.getDeclaredFields())
                .map(f -> f.getType().getSimpleName() + " " + f.getName() + (f.isSynthetic() ? " (컴파일러가 넣음)" : ""))
                .collect(Collectors.joining(", "));
        return s.isEmpty() ? "(없음)" : s;
    }

    @Test
    @DisplayName("바깥의 필드를 쓰는 내부 클래스에는 바깥 타입의 숨은 필드가 있고, static 중첩 클래스와 바깥을 쓰지 않는 내부 클래스에는 없다")
    void hiddenOuterField() {
        Shop shop = new Shop();
        Shop.Item item = shop.new Item();
        Field[] itemFields = Shop.Item.class.getDeclaredFields();

        assertThat(item.share()).isEqualTo(5000);
        assertThat(itemFields).anyMatch(f -> f.getType() == Shop.class && f.isSynthetic());
        assertThat(Shop.Line.class.getDeclaredFields()).isEmpty();
        assertThat(Shop.Plain.class.getDeclaredFields()).noneMatch(f -> f.getType() == Shop.class);
        CiOutput.record(LESSON, "fields", """
                Item의 필드  → %s
                Line의 필드  → %s
                (바깥을 쓰지 않는 내부 클래스) Plain의 필드 → %s""".formatted(fields(Shop.Item.class),
                fields(Shop.Line.class), fields(Shop.Plain.class)).replace("Shop", "Order"));
    }

    static String firstError(JavaSource.Result r) {
        if (r.ok()) {
            return "컴파일됨";
        }
        String e = r.errors().get(0);
        return "컴파일 오류: " + e.lines().findFirst().orElse(e).replaceAll("^.*?error: ", "");
    }

    @Test
    @DisplayName("바깥 객체 없는 내부 클래스 new, static 중첩 클래스의 바깥 필드 사용, 람다 안의 같은 이름 선언은 컴파일되지 않고, 익명 클래스 안의 같은 이름은 컴파일된다")
    void compileRules() {
        JavaSource.Result newInner = JavaSource.compile("Order", """
                class Order {
                  int total = 10000;
                  class Item { }
                  static Item make() { return new Order.Item(); }
                }
                """);
        JavaSource.Result newLine = JavaSource.compile("Order", """
                class Order {
                  static class Line { }
                  static Line make() { return new Order.Line(); }
                }
                """);
        JavaSource.Result staticUsesField = JavaSource.compile("Team", """
                class Team {
                  String name;
                  static class Member {
                    String label() { return name; }
                  }
                }
                """);
        JavaSource.Result lambdaShadow = JavaSource.compile("Counter", """
                class Counter {
                  void run() {
                    int count = 1;
                    Runnable r = () -> { int count = 2; };
                  }
                }
                """);
        JavaSource.Result anonShadow = JavaSource.compile("Counter", """
                class Counter {
                  void run() {
                    int count = 1;
                    Runnable r = new Runnable() {
                      public void run() { int count = 2; }
                    };
                  }
                }
                """);

        assertThat(newInner.ok()).isFalse();
        assertThat(newLine.ok()).isTrue();
        assertThat(staticUsesField.ok()).isFalse();
        assertThat(lambdaShadow.ok()).isFalse();
        assertThat(anonShadow.ok()).isTrue();
        CiOutput.record(LESSON, "new-inner", """
                static 메서드에서 new Order.Item()
                → %s

                static 메서드에서 new Order.Line()
                → %s""".formatted(firstError(newInner), firstError(newLine)));
        CiOutput.record(LESSON, "static-field", "static class Member가 바깥의 name을 씀\n→ " + firstError(staticUsesField));
        CiOutput.record(LESSON, "shadow", """
                람다 안에서 int count = 2;
                → %s

                익명 클래스 안에서 int count = 2;
                → %s""".formatted(firstError(lambdaShadow), firstError(anonShadow)));
    }

    // 코드 listener
    static class BigOrder {
        byte[] data = new byte[10_000_000];

        class Watcher implements Runnable {
            public void run() {
                System.out.println(data.length);
            }
        }

        static class SizeWatcher implements Runnable {
            private final int size;

            SizeWatcher(int size) {
                this.size = size;
            }

            public void run() {
                System.out.println(size);
            }
        }
    }

    static List<Runnable> watchers = new ArrayList<>();

    static boolean clearedWithinGc(WeakReference<?> ref) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (ref.get() == null) {
                return true;
            }
            System.gc();
            Thread.sleep(100);
        }
        return ref.get() == null;
    }

    static WeakReference<BigOrder> registerInner() {
        BigOrder order = new BigOrder();
        watchers.add(order.new Watcher());
        return new WeakReference<>(order);
    }

    static WeakReference<BigOrder> registerStatic() {
        BigOrder order = new BigOrder();
        watchers.add(new BigOrder.SizeWatcher(order.data.length));
        return new WeakReference<>(order);
    }

    @Test
    @DisplayName("static 리스트에 넣은 내부 클래스 객체는 바깥 객체를 붙잡고, 값만 받은 static 중첩 클래스는 붙잡지 않는다")
    void innerKeepsOuter() throws Exception {
        watchers.clear();
        WeakReference<BigOrder> viaStatic = registerStatic();
        WeakReference<BigOrder> viaInner = registerInner();
        boolean staticCleared = clearedWithinGc(viaStatic);
        System.gc();
        boolean innerCleared = viaInner.get() == null;

        assertThat(staticCleared).isTrue();
        assertThat(innerCleared).isFalse();
        CiOutput.record(LESSON, "listener", """
                watchers에 넣고 order = null, GC를 요청한 뒤
                내부 클래스 Watcher를 넣은 Order       → %s (10MB 배열과 함께)
                static 중첩 클래스 Watcher를 넣은 Order → %s
                watchers.size() = %d""".formatted(innerCleared ? "치워짐" : "남음",
                staticCleared ? "치워짐" : "남음", watchers.size()));
        watchers.clear();
    }

    // 실무 구역: DTO 안의 중첩 타입
    static class InnerRequest {
        public List<Line> lines;

        class Line {
            public String name;
            public int qty;
        }
    }

    static class StaticRequest {
        public List<Line> lines;

        static class Line {
            public String name;
            public int qty;
        }
    }

    record RecordRequest(List<Line> lines) {
        record Line(String name, int qty) {
        }
    }

    static final String BODY = "{\"lines\":[{\"name\":\"펜\",\"qty\":2}]}";

    @Test
    @DisplayName("static이 아닌 내부 클래스가 든 DTO는 Jackson이 읽지 못하고, static이나 중첩 record면 읽는다")
    void jacksonNeedsStatic() throws Exception {
        ObjectMapper json = new ObjectMapper();
        Exception error = null;
        try {
            json.readValue(BODY, InnerRequest.class);
        } catch (Exception e) {
            error = e;
        }
        StaticRequest s = json.readValue(BODY, StaticRequest.class);
        RecordRequest r = json.readValue(BODY, RecordRequest.class);

        assertThat(error).isInstanceOf(com.fasterxml.jackson.databind.exc.InvalidDefinitionException.class);
        assertThat(s.lines.get(0).qty).isEqualTo(2);
        assertThat(r.lines().get(0)).isEqualTo(new RecordRequest.Line("펜", 2));
        String message = error.getMessage().lines().findFirst().orElse("")
                .replace("lab.devlessons.nested.NestedClassTest$InnerRequest", "OrderRequest");
        CiOutput.record(LESSON, "jackson", """
                class Line (static 없음)
                → %s
                  %s

                static class Line → 읽힘, 첫 줄 %s %d개
                record Line       → 읽힘, %s""".formatted(error.getClass().getSimpleName(), message,
                s.lines.get(0).name, s.lines.get(0).qty, r.lines().get(0)));
    }

    @RestController
    static class OrderController {
        @PostMapping("/orders/inner")
        String inner(@RequestBody InnerRequest request) {
            return "받음";
        }

        @PostMapping("/orders/static")
        String fixed(@RequestBody StaticRequest request) {
            return "받음: " + request.lines.get(0).name + " " + request.lines.get(0).qty + "개";
        }
    }

    /** 에러 응답 본문에서 error와 message만 꺼낸다 */
    static String errorLine(String body) {
        try {
            var node = new ObjectMapper().readTree(body);
            String msg = node.path("message").asText()
                    .replace("lab.devlessons.nested.NestedClassTest$InnerRequest", "OrderRequest");
            return "error: " + node.path("error").asText() + (msg.isEmpty() ? "" : "\nmessage: " + msg);
        } catch (Exception e) {
            return body;
        }
    }

    @Test
    @DisplayName("스프링 MVC는 내부 클래스가 든 요청 DTO를 읽지 못해 500으로 답한다. 요청이 아니라 DTO 정의의 잘못이다")
    void springAnswers500() {
        try (MiniWeb web = MiniWeb.start(OrderController.class)) {
            MiniWeb.Reply bad = web.postJson("/orders/inner", BODY);
            MiniWeb.Reply ok = web.postJson("/orders/static", BODY);
            assertThat(bad.status()).isEqualTo(500);
            assertThat(ok.status()).isEqualTo(200);
            CiOutput.record(LESSON, "spring", """
                    POST /orders (Line에 static 없음) → %d
                    %s

                    POST /orders (static class Line)  → %d %s""".formatted(bad.status(), errorLine(bad.body()),
                    ok.status(), ok.body()));
        }
    }

    // 실무 구역: 이중 중괄호 초기화
    Map<String, Integer> doubleBrace() {
        return new HashMap<String, Integer>() {{
                put("A", 1);
            }};
    }

    @Test
    @DisplayName("이중 중괄호로 만든 맵은 HashMap을 상속한 익명 클래스이고 바깥 객체를 붙잡는다")
    void doubleBraceIsAnonymous() {
        Map<String, Integer> m = doubleBrace();
        boolean holdsOuter = Arrays.stream(m.getClass().getDeclaredFields())
                .anyMatch(f -> f.getType() == NestedClassTest.class);

        assertThat(m.getClass()).isNotEqualTo(HashMap.class);
        assertThat(m.getClass().getSuperclass()).isEqualTo(HashMap.class);
        assertThat(holdsOuter).isTrue();
        assertThat(Map.of("A", 1)).isEqualTo(m);
        CiOutput.record(LESSON, "double-brace", """
                m.getClass() == HashMap.class → %s
                m.getClass().getName()        → %s
                m.getClass().getSuperclass()  → %s
                바깥 객체를 가리키는 필드      → %s
                Map.of("A", 1).equals(m)      → %s""".formatted(m.getClass() == HashMap.class,
                m.getClass().getName().replace("lab.devlessons.nested.NestedClassTest", "OrderService"),
                m.getClass().getSuperclass().getSimpleName(), holdsOuter ? "있음" : "없음",
                Map.of("A", 1).equals(m)));
    }
}
