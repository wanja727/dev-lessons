package lab.devlessons.singleton;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 레슨 "싱글톤 패턴"(singleton)의 주장을 확인한다.
 * 생성자는 설정 파일을 읽는 것처럼 0.1초 쉬고, 불린 횟수를 센다.
 */
class SingletonTest {

    private static final String LESSON = "singleton";

    static void slow() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── 코드 lazy ──
    static class LazyConfig {
        static final AtomicInteger created = new AtomicInteger();
        private static LazyConfig instance;

        private LazyConfig() {
            created.incrementAndGet();
            slow();
        }

        static LazyConfig getInstance() {
            if (instance == null) {
                instance = new LazyConfig();
            }
            return instance;
        }

        static void reset() {
            instance = null;
            created.set(0);
        }
    }

    // ── 코드 holder ──
    static class HolderConfig {
        static final AtomicInteger created = new AtomicInteger();

        private HolderConfig() {
            created.incrementAndGet();
            slow();
        }

        private static class Holder {
            static final HolderConfig INSTANCE = new HolderConfig();
        }

        static HolderConfig getInstance() {
            return Holder.INSTANCE;
        }

        static String version() {
            return "1.0";
        }
    }

    // ── 코드 eager ──
    static class EagerConfig {
        static final AtomicInteger created = new AtomicInteger();
        static final EagerConfig INSTANCE = new EagerConfig();

        private EagerConfig() {
            created.incrementAndGet();
            slow();
        }
    }

    // ── 코드 enumSingleton ──
    enum EnumConfig {
        INSTANCE;

        EnumConfig() {
            counter().incrementAndGet();
            slow();
        }

        // enum 생성자에서는 static 필드를 쓸 수 없어서 바깥에 센다
        private static AtomicInteger counter() {
            return EnumCounter.COUNT;
        }
    }

    static class EnumCounter {
        static final AtomicInteger COUNT = new AtomicInteger();
    }

    /** 두 스레드를 같은 순간에 출발시켜 get을 부르고, 받은 객체를 모은다 */
    static List<Object> race(Supplier<Object> get) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        List<Object> got = java.util.Collections.synchronizedList(new ArrayList<>());
        Thread a = new Thread(() -> {
            await(start);
            got.add(get.get());
        });
        Thread b = new Thread(() -> {
            await(start);
            got.add(get.get());
        });
        a.start();
        b.start();
        start.countDown();
        a.join();
        b.join();
        return got;
    }

    static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("두 스레드가 동시에 처음 부르면 코드 lazy는 생성자가 두 번 불리는 경우가 생기고, 홀더와 미리 만들기와 enum은 한 번이다")
    void raceOnFirstAccess() throws Exception {
        int runs = 10;
        int twice = 0;
        for (int i = 0; i < runs; i++) {
            LazyConfig.reset();
            race(LazyConfig::getInstance);
            if (LazyConfig.created.get() == 2) {
                twice++;
            }
        }
        List<Object> holder = race(HolderConfig::getInstance);
        List<Object> eager = race(() -> EagerConfig.INSTANCE);
        List<Object> enums = race(() -> EnumConfig.INSTANCE);

        assertThat(twice).isGreaterThan(0);
        assertThat(HolderConfig.created.get()).isEqualTo(1);
        assertThat(holder.get(0)).isSameAs(holder.get(1));
        assertThat(EagerConfig.created.get()).isEqualTo(1);
        assertThat(eager.get(0)).isSameAs(eager.get(1));
        assertThat(EnumCounter.COUNT.get()).isEqualTo(1);
        assertThat(enums.get(0)).isSameAs(enums.get(1));
        CiOutput.record(LESSON, "race", "두 스레드가 동시에 처음 꺼내기\n\n코드 lazy   → " + runs + "번 돌려 생성자가 두 번 불린 때 "
                + twice + "번\n코드 holder → 생성자 " + HolderConfig.created.get() + "번\n코드 eager  → 생성자 "
                + EagerConfig.created.get() + "번\n코드 enum   → 생성자 " + EnumCounter.COUNT.get() + "번");
    }

    static class HolderOnly {
        static final AtomicInteger created = new AtomicInteger();

        private HolderOnly() {
            created.incrementAndGet();
        }

        private static class Holder {
            static final HolderOnly INSTANCE = new HolderOnly();
        }

        static HolderOnly getInstance() {
            return Holder.INSTANCE;
        }

        static String version() {
            return "1.0";
        }
    }

    @Test
    @DisplayName("홀더 방식은 바깥 클래스의 다른 static 메서드를 불러도 객체를 만들지 않고, getInstance에서 만든다")
    void holderIsLazy() {
        HolderOnly.version();
        int afterVersion = HolderOnly.created.get();
        HolderOnly.getInstance();
        int afterGet = HolderOnly.created.get();

        assertThat(afterVersion).isZero();
        assertThat(afterGet).isEqualTo(1);
        CiOutput.record(LESSON, "holder", "AppConfig.version() 뒤 만든 객체   → " + afterVersion + "개\nAppConfig.getInstance() 뒤 → "
                + afterGet + "개");
    }

    static class PlainSingleton {
        static final PlainSingleton INSTANCE = new PlainSingleton();

        private PlainSingleton() {
        }
    }

    enum EnumSingleton {
        INSTANCE
    }

    @Test
    @DisplayName("private 생성자 싱글톤은 리플렉션으로 하나 더 만들어지고, enum은 IllegalArgumentException")
    void reflectionAttack() throws Exception {
        Constructor<PlainSingleton> c = PlainSingleton.class.getDeclaredConstructor();
        c.setAccessible(true);
        PlainSingleton another = c.newInstance();

        Constructor<EnumSingleton> e = EnumSingleton.class.getDeclaredConstructor(String.class, int.class);
        e.setAccessible(true);

        assertThat(another).isNotSameAs(PlainSingleton.INSTANCE);
        assertThatThrownBy(() -> e.newInstance("SECOND", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot reflectively create enum objects");
        CiOutput.record(LESSON, "reflection", "private 생성자를 setAccessible(true)로 부르기\n→ INSTANCE와 다른 객체가 생김\n\n"
                + "enum 생성자를 리플렉션으로 부르기\n→ IllegalArgumentException: Cannot reflectively create enum objects");
    }

    // ── 코드 hidden: 상태가 테스트 사이에 남는다 ──
    static class SharedConfig {
        static final SharedConfig INSTANCE = new SharedConfig();
        private int shippingFee = 3000;

        private SharedConfig() {
        }

        static SharedConfig getInstance() {
            return INSTANCE;
        }
    }

    static int testA() {
        SharedConfig.getInstance().shippingFee = 0; // 무료 배송 경우를 보려고 바꾼다
        return SharedConfig.getInstance().shippingFee;
    }

    static boolean testB() {
        return SharedConfig.getInstance().shippingFee == 3000; // 기본 배송비라고 믿는다
    }

    @Test
    @DisplayName("싱글톤의 상태가 남아서, A 다음 B를 돌리면 B가 실패하고 B만 돌리면 통과한다")
    void stateLeaksBetweenTests() {
        boolean bAlone = testB();
        testA();
        boolean bAfterA = testB();

        assertThat(bAlone).isTrue();
        assertThat(bAfterA).isFalse();
        SharedConfig.getInstance().shippingFee = 3000;
        CiOutput.record(LESSON, "state", "테스트 B만 돌리기     → " + (bAlone ? "통과" : "실패")
                + "\n테스트 A 다음 B 돌리기 → " + (bAfterA ? "통과" : "실패") + " (A가 바꾼 배송비 0이 남아 있다)");
    }

    // ── 스프링 ──
    @Component
    static class AppConfig {
    }

    @Test
    @DisplayName("같은 컨테이너에서 두 번 꺼낸 빈은 같은 객체, new는 다른 객체, 컨테이너 둘은 빈 둘")
    void springSingleton() {
        try (var ctx1 = new AnnotationConfigApplicationContext(AppConfig.class);
                var ctx2 = new AnnotationConfigApplicationContext(AppConfig.class)) {
            AppConfig a = ctx1.getBean(AppConfig.class);
            AppConfig b = ctx1.getBean(AppConfig.class);
            AppConfig other = ctx2.getBean(AppConfig.class);
            AppConfig made = new AppConfig();

            assertThat(a).isSameAs(b);
            assertThat(made).isNotSameAs(a);
            assertThat(other).isNotSameAs(a);
            CiOutput.record(LESSON, "spring", "같은 컨테이너에서 두 번 꺼낸 빈 a == b → " + (a == b)
                    + "\nnew AppConfig() == a                → " + (made == a)
                    + "\n다른 컨테이너의 빈 == a              → " + (other == a));
        }
    }

    // ── 실무: 싱글톤 빈의 필드에 요청 값을 둔다 ──
    @Component
    static class OrderService {
        private int lastTotal;

        int order(int total, CountDownLatch afterWrite, CountDownLatch beforeRead) {
            lastTotal = total;
            afterWrite.countDown();
            await(beforeRead);
            return lastTotal;
        }
    }

    @Test
    @DisplayName("싱글톤 빈의 필드에 요청 값을 두면 먼저 쓴 요청이 다른 요청의 값을 읽는다")
    void statefulBean() throws Exception {
        try (var ctx = new AnnotationConfigApplicationContext(OrderService.class)) {
            OrderService service = ctx.getBean(OrderService.class);
            CountDownLatch aWrote = new CountDownLatch(1);
            CountDownLatch bWrote = new CountDownLatch(1);
            CountDownLatch none = new CountDownLatch(0);
            int[] aRead = new int[1];

            Thread a = new Thread(() -> aRead[0] = service.order(10000, aWrote, bWrote));
            a.start();
            aWrote.await();
            Thread b = new Thread(() -> service.order(20000, bWrote, none));
            b.start();
            b.join();
            a.join();

            assertThat(aRead[0]).isEqualTo(20000);
            CiOutput.record(LESSON, "stateful", "요청 A: lastTotal = 10000을 넣고 잠시 뒤 읽음\n요청 B: 그 사이 lastTotal = 20000을 넣음\n"
                    + "→ 요청 A가 읽은 금액: " + aRead[0]);
        }
    }
}
