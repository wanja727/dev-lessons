package lab.devlessons.polymorphism;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.JavaSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 레슨 "상속과 다형성"(J7)의 주장을 검증한다. 출력 대신 문자열을 돌려주게 해서 결과를 비교한다.
 */
class PolymorphismTest {

    private static final String LESSON = "inheritance-polymorphism";

    // ── 코드 predict ──
    static class Notifier {
        String send(Object msg) {
            return "Notifier, Object";
        }
    }

    static class SmsNotifier extends Notifier {
        @Override
        String send(Object msg) {
            return "Sms, Object";
        }

        String send(String msg) {
            return "Sms, String";
        }

        int remaining() {
            return 10;
        }
    }

    static class EmailNotifier extends Notifier {
    }

    @Test
    @DisplayName("코드 predict: Notifier n = new SmsNotifier(); n.send(\"주문 완료\")는 \"Sms, Object\"")
    void predict() {
        Notifier n = new SmsNotifier();
        String result = n.send("주문 완료");

        assertThat(result).isEqualTo("Sms, Object");
        CiOutput.record(LESSON, "predict", result);
    }

    @Test
    @DisplayName("받는 변수의 타입과 인자의 변수 타입 네 조합")
    void fourCombinations() {
        Notifier n = new SmsNotifier();
        SmsNotifier s = new SmsNotifier();
        String str = "주문 완료";
        Object obj = "주문 완료";

        String a = n.send(str);
        String b = n.send(obj);
        String c = s.send(str);
        String d = s.send(obj);

        assertThat(a).isEqualTo("Sms, Object");
        assertThat(b).isEqualTo("Sms, Object");
        assertThat(c).isEqualTo("Sms, String");
        assertThat(d).isEqualTo("Sms, Object");
        CiOutput.record(LESSON, "combinations", """
                Notifier n,    String 변수 → %s
                Notifier n,    Object 변수 → %s
                SmsNotifier s, String 변수 → %s
                SmsNotifier s, Object 변수 → %s""".formatted(a, b, c, d));
    }

    // ── 필드와 static 메서드 ──
    static class Base {
        String name = "기본";

        static String type() {
            return "Notifier";
        }
    }

    static class Child extends Base {
        String name = "문자";

        static String type() {
            return "Sms";
        }
    }

    @Test
    @DisplayName("필드와 static 메서드는 오버라이딩되지 않고 변수의 타입으로 정해진다")
    void fieldsAreNotOverridden() {
        Base n = new Child();

        assertThat(n.name).isEqualTo("기본");
        assertThat(((Child) n).name).isEqualTo("문자");
        CiOutput.record(LESSON, "fields", "n.name → " + n.name);
    }

    static class Animal {
        String sound() {
            return "...";
        }
    }

    static class Dog extends Animal {
        @Override
        String sound() {
            return "멍";
        }
    }

    static class Puppy extends Dog {
    }

    @Test
    @DisplayName("다시 정의하지 않은 자식은 가장 가까운 부모의 구현을 쓴다")
    void nearestOverride() {
        Animal a = new Puppy();
        assertThat(a.sound()).isEqualTo("멍");
    }

    @Test
    @DisplayName("상관없는 자식 타입으로 내려받으면 ClassCastException, instanceof 패턴은 false")
    void downcast() {
        Notifier n = new EmailNotifier();

        assertThat(n instanceof SmsNotifier).isFalse();
        assertThatThrownBy(() -> {
            SmsNotifier s = (SmsNotifier) n;
        }).isInstanceOf(ClassCastException.class);
        String message = null;
        try {
            SmsNotifier s = (SmsNotifier) n;
        } catch (ClassCastException e) {
            message = e.getMessage().replace("lab.devlessons.polymorphism.PolymorphismTest$", "");
        }
        CiOutput.record(LESSON, "downcast", "ClassCastException: " + message);
    }

    @Test
    @DisplayName("변수 타입에 없는 메서드는 컴파일되지 않는다")
    void methodNotInVariableType() {
        JavaSource.Result r = JavaSource.compile("Main", """
                class Notifier { void send(Object m) {} }
                class SmsNotifier extends Notifier { int remaining() { return 10; } }
                public class Main {
                  void run() {
                    Notifier n = new SmsNotifier();
                    n.remaining();
                  }
                }
                """);
        assertThat(r.ok()).isFalse();
        assertThat(r.errors()).anyMatch(e -> e.contains("cannot find symbol"));
    }

    // ── 오버로딩하려다: 실무 구역의 흔한 실수 ──
    static class Event {
    }

    static class OrderEvent extends Event {
    }

    static class Handler {
        String handle(Event e) {
            return "기본";
        }
    }

    static class OrderHandler extends Handler {
        String handle(OrderEvent e) {
            return "주문";
        }
    }

    @Test
    @DisplayName("매개변수 타입이 다른 메서드는 오버로딩이라 부모 타입으로 부르면 부모의 구현이 실행된다")
    void overloadNotOverride() {
        Handler h = new OrderHandler();
        Event e = new OrderEvent();

        assertThat(h.handle(e)).isEqualTo("기본");
        assertThat(h.handle(new OrderEvent())).isEqualTo("기본");
        CiOutput.record(LESSON, "handler", """
                Handler h = new OrderHandler();
                h.handle(new OrderEvent()) → %s""".formatted(h.handle(new OrderEvent())));
    }

    @Test
    @DisplayName("오버라이딩이 아닌 메서드에 @Override를 붙이면 컴파일되지 않는다")
    void overrideAnnotationCatchesIt() {
        JavaSource.Result r = JavaSource.compile("OrderHandler", """
                class Event {}
                class OrderEvent extends Event {}
                class Handler { String handle(Event e) { return "기본"; } }
                public class OrderHandler extends Handler {
                  @Override
                  String handle(OrderEvent e) { return "주문"; }
                }
                """);

        assertThat(r.ok()).isFalse();
        assertThat(r.errors()).anyMatch(e -> e.contains("does not override or implement a method from a supertype"));
        CiOutput.record(LESSON, "override-error", "error: " + r.errors().get(0));
    }

    // ── 생성자에서 오버라이딩된 메서드 ──
    static final List<String> SEEN = new ArrayList<>();

    static class Parent {
        Parent() {
            describe();
        }

        void describe() {
            SEEN.add("부모");
        }
    }

    static class Kid extends Parent {
        String label = "자식";

        @Override
        void describe() {
            SEEN.add("label = " + label);
        }
    }

    @Test
    @DisplayName("부모 생성자가 부른 오버라이딩된 메서드에서 자식의 필드는 아직 기본값이다")
    void overriddenMethodInConstructor() {
        SEEN.clear();
        Kid k = new Kid();

        assertThat(SEEN).containsExactly("label = null");
        assertThat(k.label).isEqualTo("자식");
    }

    @Test
    @DisplayName("클래스는 하나만 상속하고 인터페이스는 여러 개 구현한다")
    void singleInheritance() {
        JavaSource.Result two = JavaSource.compile("Report", """
                class Document {}
                interface Printable {}
                public class Report extends Document, Printable {}
                """);
        JavaSource.Result ok = JavaSource.compile("Report", """
                class Document {}
                interface Printable {}
                interface Exportable {}
                public class Report extends Document implements Printable, Exportable {}
                """);

        assertThat(two.ok()).isFalse();
        assertThat(ok.ok()).isTrue();
    }

    // ── 실무 구역: List<Notifier> 주입 ──
    interface AlarmNotifier {
        String send(String msg);
    }

    static class SmsAlarm implements AlarmNotifier {
        public String send(String msg) {
            return "문자: " + msg;
        }
    }

    static class EmailAlarm implements AlarmNotifier {
        public String send(String msg) {
            return "메일: " + msg;
        }
    }

    static class PushAlarm implements AlarmNotifier {
        public String send(String msg) {
            return "푸시: " + msg;
        }
    }

    static class OrderService {
        private final List<AlarmNotifier> notifiers;

        OrderService(List<AlarmNotifier> notifiers) {
            this.notifiers = notifiers;
        }

        List<String> complete() {
            return notifiers.stream().map(n -> n.send("주문 완료")).toList();
        }
    }

    @Test
    @DisplayName("생성자로 List<Notifier>를 받으면 구현 빈이 모두 들어오고, 구현을 더해도 OrderService는 그대로다")
    void listInjection() {
        List<String> two;
        try (var ctx = new AnnotationConfigApplicationContext(SmsAlarm.class, EmailAlarm.class, OrderService.class)) {
            two = ctx.getBean(OrderService.class).complete();
        }
        List<String> three;
        try (var ctx = new AnnotationConfigApplicationContext(
                SmsAlarm.class, EmailAlarm.class, PushAlarm.class, OrderService.class)) {
            three = ctx.getBean(OrderService.class).complete();
        }

        assertThat(two).containsExactlyInAnyOrder("문자: 주문 완료", "메일: 주문 완료");
        assertThat(three).hasSize(3).contains("푸시: 주문 완료");
        CiOutput.record(LESSON, "list-injection", "구현 둘\n" + String.join("\n", two)
                + "\n\nPushNotifier를 더한 뒤\n" + String.join("\n", three));
    }
}
