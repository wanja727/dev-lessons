package lab.devlessons.staticinit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.JavaSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 레슨 "static과 초기화 순서"(J5)의 주장을 검증한다.
 * 클래스 초기화는 JVM에서 한 번만 일어나므로, 테스트마다 다른 클래스를 쓴다.
 */
@ExtendWith(OutputCaptureExtension.class)
class StaticInitializationTest {

    private static final String LESSON = "static-initialization";

    // ── 코드 predict ──
    static final List<String> ORDER = new ArrayList<>();

    static int init(String step) {
        ORDER.add(step);
        return 0;
    }

    static class Coupon {
        static {
            ORDER.add("1 Coupon static");
        }

        int rate = init("2 Coupon 필드");

        Coupon() {
            ORDER.add("3 Coupon 생성자");
        }
    }

    static class VipCoupon extends Coupon {
        static {
            ORDER.add("4 Vip static");
        }

        int bonus = init("5 Vip 필드");

        VipCoupon() {
            ORDER.add("6 Vip 생성자");
        }
    }

    @Test
    @DisplayName("코드 predict: 1 4 2 3 5 6 다음에 2 3 5 6")
    void predict() {
        ORDER.clear();
        new VipCoupon();
        List<String> first = List.copyOf(ORDER);
        ORDER.clear();
        new VipCoupon();
        List<String> second = List.copyOf(ORDER);
        ORDER.clear();
        new Coupon();
        List<String> parentOnly = List.copyOf(ORDER);

        assertThat(first).extracting(s -> s.substring(0, 1)).containsExactly("1", "4", "2", "3", "5", "6");
        assertThat(second).extracting(s -> s.substring(0, 1)).containsExactly("2", "3", "5", "6");
        assertThat(parentOnly).extracting(s -> s.substring(0, 1)).containsExactly("2", "3");
        CiOutput.record(LESSON, "predict", "첫 new VipCoupon()\n" + String.join("\n", first)
                + "\n\n둘째 new VipCoupon()\n" + String.join("\n", second));
    }

    // ── 코드 counter ──
    static class CounterCoupon {
        static int issued = 0;
        int number;

        CounterCoupon() {
            issued++;
            number = issued;
        }
    }

    @Test
    @DisplayName("코드 counter: number는 객체마다, issued는 클래스에 하나")
    void counter() {
        CounterCoupon a = new CounterCoupon();
        CounterCoupon b = new CounterCoupon();
        CounterCoupon c = new CounterCoupon();

        assertThat(a.number).isEqualTo(1);
        assertThat(b.number).isEqualTo(2);
        assertThat(c.number).isEqualTo(3);
        assertThat(CounterCoupon.issued).isEqualTo(3);
        CiOutput.record(LESSON, "counter", """
                a.number → %d
                b.number → %d
                c.number → %d
                CounterCoupon.issued → %d""".formatted(a.number, b.number, c.number, CounterCoupon.issued));
    }

    @Test
    @DisplayName("static 메서드에서 인스턴스 필드를 쓰면 컴파일되지 않는다")
    void staticMethodCannotUseInstanceField() {
        JavaSource.Result r = JavaSource.compile("Cart", """
                class Cart {
                  int total;
                  static int tax() { return total / 10; }
                }
                """);

        assertThat(r.ok()).isFalse();
        assertThat(r.errors()).anyMatch(e -> e.contains("non-static variable total cannot be referenced"));
        CiOutput.record(LESSON, "static-method", "error: " + r.errors().get(0));
    }

    // ── 클래스 초기화가 일어나는 때 ──
    static final List<String> TRIGGER = new ArrayList<>();

    static class Rates {
        static final int MAX = 50;

        static {
            TRIGGER.add("Rates 초기화");
        }

        static int max() {
            return MAX;
        }
    }

    @Test
    @DisplayName("컴파일 시점 상수 읽기와 변수 선언은 초기화를 일으키지 않고, static 메서드 호출은 일으킨다")
    void whatTriggersInitialization() {
        TRIGGER.clear();
        TRIGGER.add("상수 읽기: " + Rates.MAX);
        Rates declared = null;
        TRIGGER.add("변수 선언");
        TRIGGER.add("static 메서드: " + Rates.max());

        assertThat(declared).isNull();
        assertThat(TRIGGER).containsExactly("상수 읽기: 50", "변수 선언", "Rates 초기화", "static 메서드: 50");
        CiOutput.record(LESSON, "trigger", String.join("\n", TRIGGER));
    }

    // ── 코드 forward ──
    static class Config {
        static Config INSTANCE = new Config();
        static int timeout = 30;
        int copied;

        Config() {
            copied = timeout;
        }
    }

    static class SwappedConfig {
        static int timeout = 30;
        static SwappedConfig INSTANCE = new SwappedConfig();
        int copied;

        SwappedConfig() {
            copied = timeout;
        }
    }

    @Test
    @DisplayName("코드 forward: static 필드는 위에서 아래로 실행되어 0, 순서를 바꾸면 30")
    void forwardReference() {
        assertThat(Config.INSTANCE.copied).isZero();
        assertThat(SwappedConfig.INSTANCE.copied).isEqualTo(30);
        CiOutput.record(LESSON, "forward", """
                INSTANCE가 위에 있을 때 → %d
                timeout이 위에 있을 때  → %d""".formatted(Config.INSTANCE.copied, SwappedConfig.INSTANCE.copied));
    }

    static class Timer {
        long start = 1000;
        long limit;
        long seenInConstructor;

        Timer() {
            seenInConstructor = start;
            limit = start + 1000;
        }
    }

    @Test
    @DisplayName("필드 초기화식의 값은 생성자 본문에서 이미 보인다")
    void fieldInitializerBeforeConstructor() {
        Timer t = new Timer();
        assertThat(t.seenInConstructor).isEqualTo(1000);
        assertThat(t.limit).isEqualTo(2000);
    }

    // ── 실무 구역: 스프링 빈 ──
    static class OrderRepository {
        long count() {
            return 3;
        }
    }

    static class FieldInjectedService {
        @Autowired
        private OrderRepository repo;
        long countAtStart;

        FieldInjectedService() {
            countAtStart = repo.count();
        }
    }

    static class ConstructorInjectedService {
        private final OrderRepository repo;
        long countAtStart;

        ConstructorInjectedService(OrderRepository repo) {
            this.repo = repo;
            countAtStart = repo.count();
        }
    }

    @Test
    @DisplayName("필드 주입을 생성자에서 쓰면 빈 생성이 NPE로 실패하고, 생성자 주입은 성공한다")
    void fieldInjectionIsAfterConstructor() {
        Throwable failure = null;
        try (var ctx = new AnnotationConfigApplicationContext(OrderRepository.class, FieldInjectedService.class)) {
            ctx.getBean(FieldInjectedService.class);
        } catch (BeanCreationException e) {
            failure = e;
        }
        assertThat(failure).isInstanceOf(BeanCreationException.class);
        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertThat(root).isInstanceOf(NullPointerException.class);

        long count;
        try (var ctx = new AnnotationConfigApplicationContext(OrderRepository.class, ConstructorInjectedService.class)) {
            count = ctx.getBean(ConstructorInjectedService.class).countAtStart;
        }
        assertThat(count).isEqualTo(3);

        CiOutput.record(LESSON, "field-injection", """
                필드 주입
                → %s
                  원인: %s: %s

                생성자 주입
                → 빈 생성 성공, countAtStart = %d""".formatted(failure.getClass().getSimpleName(),
                root.getClass().getSimpleName(), root.getMessage(), count)
                .replace("lab.devlessons.staticinit.StaticInitializationTest$", ""));
    }

    static class StaticInjectedService {
        @Autowired
        static OrderRepository repo;
    }

    @Test
    @DisplayName("static 필드에 @Autowired를 붙이면 컨텍스트는 뜨고 그 필드는 null이다")
    void staticFieldIsNotInjected(CapturedOutput output) {
        try (var ctx = new AnnotationConfigApplicationContext(OrderRepository.class, StaticInjectedService.class)) {
            assertThat(ctx.getBean(StaticInjectedService.class)).isNotNull();
            assertThat(StaticInjectedService.repo).isNull();
        }
        String log = output.getAll().lines()
                .filter(l -> l.contains("not supported on static fields"))
                .findFirst().orElse("(로그 없음)");
        CiOutput.record(LESSON, "static-autowired", """
                컨텍스트 시작 → 성공
                StaticInjectedService.repo → %s

                스프링이 남긴 로그
                %s""".formatted(StaticInjectedService.repo, log.replaceAll("^.*?-- ", ""))
                .replace("lab.devlessons.staticinit.StaticInitializationTest$", ""));
    }
}
