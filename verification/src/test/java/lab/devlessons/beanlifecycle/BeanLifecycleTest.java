package lab.devlessons.beanlifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.MapPropertySource;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.annotation.RequestScope;

/** 레슨 "빈의 생명주기와 스코프"(S3)의 주장을 작은 스프링 컨테이너로 확인한다. DB는 쓰지 않는다. */
class BeanLifecycleTest {

    private static final String LESSON = "bean-lifecycle-scope";
    static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());

    // ── 생명주기 ──
    @Repository("couponRepository")
    static class CouponRepository {
        CouponRepository() {
            LOG.add("CouponRepository 생성자");
        }

        @PreDestroy
        void close() {
            LOG.add("CouponRepository @PreDestroy");
        }

        @Override
        public String toString() {
            return "CouponRepository";
        }
    }

    @Component("couponPolicyCache")
    static class CouponPolicyCache {
        @Autowired
        private CouponRepository repository;

        CouponPolicyCache() {
            LOG.add("CouponPolicyCache 생성자 repository=" + repository);
        }

        @PostConstruct
        void load() {
            LOG.add("CouponPolicyCache @PostConstruct repository=" + repository);
        }

        @PreDestroy
        void close() {
            LOG.add("CouponPolicyCache @PreDestroy");
        }
    }

    @Component("failingCache")
    static class FailingCache {
        @PostConstruct
        void load() {
            throw new IllegalStateException("금칙어 파일이 없어요");
        }
    }

    @Component("valueInConstructor")
    static class ValueInConstructor {
        @Value("${coupon.max}")
        int max;
        int seenInConstructor;
        int seenInPostConstruct;

        ValueInConstructor() {
            seenInConstructor = max;
        }

        @PostConstruct
        void init() {
            seenInPostConstruct = max;
        }
    }

    // ── 싱글톤 상태 ──
    @Service("orderCounter")
    static class OrderCounter {
        private int count = 0;

        void increase() {
            count++;
        }

        int count() {
            return count;
        }
    }

    @Service("atomicOrderCounter")
    static class AtomicOrderCounter {
        private final AtomicLong count = new AtomicLong();

        void increase() {
            count.incrementAndGet();
        }

        long count() {
            return count.get();
        }
    }

    /** 레슨의 코드 stateful과 같다. audit()에서 잠시 멈출 수 있게 걸쇠를 둔다 */
    @Service("greetingService")
    static class GreetingService {
        private String userName;
        CountDownLatch written;
        CountDownLatch overwritten;

        String greet(String name) throws InterruptedException {
            this.userName = name;
            audit(name);
            return "안녕하세요 " + userName;
        }

        private void audit(String name) throws InterruptedException {
            if (name.equals("kim") && written != null) {
                written.countDown();
                overwritten.await(5, TimeUnit.SECONDS);
            }
        }
    }

    @Service("statelessGreetingService")
    static class StatelessGreetingService {
        String greet(String name) {
            String userName = name;
            return "안녕하세요 " + userName;
        }
    }

    // ── 스코프 ──
    @Component("cart")
    @Scope("prototype")
    static class Cart {
        final List<String> items = new ArrayList<>();
    }

    @Service("cartService")
    static class CartService {
        private final Cart cart;

        CartService(Cart cart) {
            this.cart = cart;
        }

        Cart cart() {
            return cart;
        }
    }

    @Service("providerCartService")
    static class ProviderCartService {
        private final ObjectProvider<Cart> carts;

        ProviderCartService(ObjectProvider<Cart> carts) {
            this.carts = carts;
        }

        Cart newCart() {
            return carts.getObject();
        }
    }

    @Component
    @RequestScope
    static class TraceContext {
        private final String id = Integer.toHexString(System.identityHashCode(this));

        // 주입된 것은 프록시라서 필드를 바로 읽지 않고 메서드를 불러야 그 요청의 객체로 간다
        String id() {
            return id;
        }
    }

    @RestController
    static class TraceController {
        private final TraceContext trace;

        TraceController(TraceContext trace) {
            this.trace = trace;
        }

        @GetMapping("/trace")
        String trace() {
            return trace.id();
        }
    }

    // ── ApplicationRunner ──
    @Configuration(proxyBeanMethods = false)
    static class RunnerApp {
        @Component("warmCache")
        static class WarmCache {
            @PostConstruct
            void load() {
                LOG.add("WarmCache @PostConstruct");
            }
        }

        @Component("warmUp")
        static class WarmUp implements ApplicationRunner {
            WarmUp() {
                LOG.add("WarmUp 생성자");
            }

            @Override
            public void run(ApplicationArguments args) {
                LOG.add("WarmUp ApplicationRunner.run");
            }
        }
    }

    private static Throwable rootCause(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t;
    }

    @Test
    @DisplayName("생성자에서는 주입 필드가 null, @PostConstruct에서는 채워져 있다. 소멸은 만든 순서의 반대다")
    void lifecycleOrder() {
        LOG.clear();
        var ctx = new AnnotationConfigApplicationContext(CouponRepository.class, CouponPolicyCache.class);
        LOG.add("(사용 중)");
        ctx.close();

        assertThat(LOG).containsExactly(
                "CouponRepository 생성자",
                "CouponPolicyCache 생성자 repository=null",
                "CouponPolicyCache @PostConstruct repository=CouponRepository",
                "(사용 중)",
                "CouponPolicyCache @PreDestroy",
                "CouponRepository @PreDestroy");
        CiOutput.record(LESSON, "lifecycle", String.join("\n", LOG));
    }

    @Test
    @DisplayName("@PostConstruct에서 예외가 나면 컨텍스트가 BeanCreationException으로 뜨지 않는다")
    void postConstructFailureStopsStartup() {
        assertThatThrownBy(() -> new AnnotationConfigApplicationContext(FailingCache.class))
                .isInstanceOf(BeanCreationException.class)
                .satisfies(e -> {
                    assertThat(rootCause(e)).isInstanceOf(IllegalStateException.class).hasMessage("금칙어 파일이 없어요");
                    CiOutput.record(LESSON, "post-construct-fail", e.getClass().getSimpleName()
                            + ": Error creating bean with name 'failingCache'\n원인: "
                            + rootCause(e).getClass().getSimpleName() + ": " + rootCause(e).getMessage());
                });
    }

    @Test
    @DisplayName("@Value 필드는 생성자에서 아직 기본값 0이고 @PostConstruct에서는 채워져 있다")
    void valueFieldIsLaterThanConstructor() {
        var ctx = new AnnotationConfigApplicationContext();
        ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of("coupon.max", "50")));
        ctx.register(ValueInConstructor.class);
        ctx.refresh();
        ValueInConstructor bean = ctx.getBean(ValueInConstructor.class);

        assertThat(bean.seenInConstructor).isZero();
        assertThat(bean.seenInPostConstruct).isEqualTo(50);
        ctx.close();
    }

    @Test
    @DisplayName("싱글톤 빈의 int 필드를 두 스레드가 1만 번씩 ++하면 20000보다 작게 나오곤 하고, AtomicLong은 늘 20000이다")
    void singletonStateLosesUpdates() throws Exception {
        // 경쟁 상태는 늘 나타나지 않는다. 다섯 판을 한 묶음으로 돌리고, 20000보다 작은 판이 나올 때까지 묶음을 다시 돌린다(최대 20묶음).
        // 두 스레드는 CountDownLatch로 함께 출발시킨다. 늦게 뜬 스레드가 앞 스레드가 끝난 뒤에 돌아 겹치지 않는 일을 줄인다
        List<Integer> plain = new ArrayList<>();
        List<Long> atomic = new ArrayList<>();
        int batches = 0;
        while (batches < 20 && (plain.isEmpty() || plain.stream().allMatch(n -> n == 20_000))) {
            batches++;
            plain.clear();
            atomic.clear();
            for (int round = 0; round < 5; round++) {
                try (var ctx = new AnnotationConfigApplicationContext(OrderCounter.class, AtomicOrderCounter.class)) {
                    OrderCounter counter = ctx.getBean(OrderCounter.class);
                    AtomicOrderCounter atomicCounter = ctx.getBean(AtomicOrderCounter.class);
                    runTwice(() -> {
                        for (int i = 0; i < 10_000; i++) {
                            counter.increase();
                            atomicCounter.increase();
                        }
                    });
                    plain.add(counter.count());
                    atomic.add(atomicCounter.count());
                }
            }
            assertThat(atomic).containsOnly(20_000L);
        }

        assertThat(plain).anyMatch(n -> n < 20_000);
        CiOutput.record(LESSON, "counter", "int count++ 다섯 번: " + plain + "\nAtomicLong 다섯 번: " + atomic);
    }

    @Test
    @DisplayName("필드에 이름을 두는 싱글톤 서비스는 두 요청이 엇갈리면 다른 사용자의 이름을 돌려준다. 지역 변수면 섞이지 않는다")
    void statefulSingletonMixesUsers() throws Exception {
        try (var ctx = new AnnotationConfigApplicationContext(GreetingService.class, StatelessGreetingService.class)) {
            GreetingService service = ctx.getBean(GreetingService.class);
            service.written = new CountDownLatch(1);
            service.overwritten = new CountDownLatch(1);

            String[] kimResult = new String[1];
            Thread kim = new Thread(() -> {
                try {
                    kimResult[0] = service.greet("kim");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            kim.start();
            service.written.await(5, TimeUnit.SECONDS);
            String leeResult = service.greet("lee");
            service.overwritten.countDown();
            kim.join();

            assertThat(kimResult[0]).isEqualTo("안녕하세요 lee");
            StatelessGreetingService stateless = ctx.getBean(StatelessGreetingService.class);
            assertThat(stateless.greet("kim")).isEqualTo("안녕하세요 kim");
            CiOutput.record(LESSON, "stateful", "kim의 요청 → " + kimResult[0] + "\nlee의 요청 → " + leeResult);
        }
    }

    @Test
    @DisplayName("프로토타입 빈은 꺼낼 때마다 다르지만, 싱글톤에 생성자로 넣으면 늘 같다. ObjectProvider는 매번 다르다")
    void prototypeInSingleton() {
        try (var ctx = new AnnotationConfigApplicationContext(Cart.class, CartService.class, ProviderCartService.class)) {
            assertThat(ctx.getBean(Cart.class)).isNotSameAs(ctx.getBean(Cart.class));

            CartService service = ctx.getBean(CartService.class);
            service.cart().items.add("latte");
            service.cart().items.add("mocha");
            assertThat(service.cart().items).containsExactly("latte", "mocha");

            ProviderCartService provider = ctx.getBean(ProviderCartService.class);
            Cart a = provider.newCart();
            Cart b = provider.newCart();
            assertThat(a).isNotSameAs(b);
            CiOutput.record(LESSON, "prototype", "getBean(Cart) 두 번 → 같은 객체? "
                    + (ctx.getBean(Cart.class) == ctx.getBean(Cart.class))
                    + "\n생성자로 받은 Cart를 두 번 씀 → 같은 객체? " + (service.cart() == service.cart())
                    + ", 담긴 상품 " + service.cart().items
                    + "\nObjectProvider.getObject() 두 번 → 같은 객체? " + (a == b));
        }
    }

    @Test
    @DisplayName("request 스코프 빈은 요청마다 다른 객체다")
    void requestScopePerRequest() {
        try (MiniWeb web = MiniWeb.start(TraceController.class, TraceContext.class)) {
            String first = web.get("/trace").body();
            String second = web.get("/trace").body();

            assertThat(first).isNotEqualTo(second);
            CiOutput.record(LESSON, "request-scope", "첫 요청의 TraceContext → " + first + "\n둘째 요청의 TraceContext → " + second);
        }
    }

    @Test
    @DisplayName("모든 @PostConstruct가 끝난 뒤에 ApplicationRunner가 돈다")
    void runnerAfterPostConstruct() {
        LOG.clear();
        try (var app = new SpringApplicationBuilder(RunnerApp.class, RunnerApp.WarmCache.class, RunnerApp.WarmUp.class)
                .web(org.springframework.boot.WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off", "spring.main.log-startup-info=false")
                .run()) {
            assertThat(LOG.indexOf("WarmUp ApplicationRunner.run")).isGreaterThan(LOG.indexOf("WarmCache @PostConstruct"));
            assertThat(LOG.get(LOG.size() - 1)).isEqualTo("WarmUp ApplicationRunner.run");
            CiOutput.record(LESSON, "runner", String.join("\n", LOG));
        }
    }

    private static void runTwice(Runnable work) throws InterruptedException {
        CountDownLatch go = new CountDownLatch(1);
        Runnable together = () -> {
            try {
                go.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            work.run();
        };
        Thread a = new Thread(together);
        Thread b = new Thread(together);
        a.start();
        b.start();
        go.countDown();
        a.join();
        b.join();
    }
}
