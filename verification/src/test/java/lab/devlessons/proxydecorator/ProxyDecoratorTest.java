package lab.devlessons.proxydecorator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.SourceChange;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;

/**
 * 레슨 "프록시와 데코레이터 패턴"(proxy-decorator)의 주장을 확인한다.
 */
class ProxyDecoratorTest {

    private static final String LESSON = "proxy-decorator";

    public interface PriceService {
        int price(long productId);

        int total(List<Long> ids);
    }

    // 진짜 구현. 외부 API 대신 호출 횟수를 센다
    static class ApiPriceService implements PriceService {
        int calls = 0;

        public int price(long productId) {
            calls++;
            return (int) productId * 1000;
        }

        public int total(List<Long> ids) {
            int sum = 0;
            for (long id : ids) {
                sum += this.price(id);
            }
            return sum;
        }
    }

    // ── 코드 cache ──
    static class CachingPriceService implements PriceService {
        private final PriceService target;
        private final Map<Long, Integer> cache = new HashMap<>();

        CachingPriceService(PriceService t) {
            this.target = t;
        }

        public int price(long productId) {
            return cache.computeIfAbsent(productId, target::price);
        }

        public int total(List<Long> ids) {
            return target.total(ids);
        }
    }

    // ── 코드 timing ──
    static class TimingPriceService implements PriceService {
        private final PriceService target;
        final List<String> log;

        TimingPriceService(PriceService target, List<String> log) {
            this.target = target;
            this.log = log;
        }

        public int price(long productId) {
            long start = System.nanoTime();
            int p = target.price(productId);
            log.add("price(" + productId + ") " + (System.nanoTime() - start) + "ns");
            return p;
        }

        public int total(List<Long> ids) {
            return target.total(ids);
        }
    }

    @Test
    @DisplayName("코드 cache: id 1을 세 번, 2를 한 번 물으면 진짜는 2번. 1, 2, 1, 3, 2면 3번. 가격은 감싸기 전과 같다")
    void cachingProxy() {
        ApiPriceService api = new ApiPriceService();
        PriceService service = new CachingPriceService(api);
        List<Integer> prices = new ArrayList<>();
        for (long id : new long[] {1, 1, 1, 2}) {
            prices.add(service.price(id));
        }
        ApiPriceService api2 = new ApiPriceService();
        PriceService service2 = new CachingPriceService(api2);
        for (long id : new long[] {1, 2, 1, 3, 2}) {
            service2.price(id);
        }

        assertThat(api.calls).isEqualTo(2);
        assertThat(prices).containsExactly(1000, 1000, 1000, 2000);
        assertThat(api2.calls).isEqualTo(3);
        CiOutput.record(LESSON, "cache", "캐시로 감싸고 price(1) 세 번, price(2) 한 번\n→ 받은 가격 " + prices
                + "\n→ 진짜 API 호출: " + api.calls + "번");
    }

    @Test
    @DisplayName("감싼 것을 넘겨도 컨트롤러와 ApiPriceService 파일은 그대로이고, 조립 코드만 바뀐다")
    void wrappingDoesNotTouchCallers() {
        Map<String, String> before = new HashMap<>(Map.of(
                "PriceService", "interface PriceService { int price(long id); }",
                "ApiPriceService", "class ApiPriceService implements PriceService { public int price(long id) { return 0; } }",
                "ProductController", "class ProductController { PriceService prices; }",
                "Wiring", "class Wiring { PriceService p = new ApiPriceService(); }"));
        Map<String, String> after = new HashMap<>(before);
        after.put("CachingPriceService", "class CachingPriceService implements PriceService { ... }");
        after.put("Wiring", "class Wiring { PriceService p = new CachingPriceService(new ApiPriceService()); }");

        SourceChange c = SourceChange.between(before, after);

        assertThat(c.changed()).containsExactly("Wiring");
        assertThat(c.added()).containsExactly("CachingPriceService");
        assertThat(c.same()).contains("ApiPriceService", "ProductController");
        CiOutput.record(LESSON, "files", "[캐시 붙이기]  (Wiring이 조립 코드예요)\n" + c.describe());
    }

    @Test
    @DisplayName("코드 stack: 같은 상품 세 번. a(시간 재기가 바깥)는 로그 3번, b(캐시가 바깥)는 1번, 진짜는 둘 다 1번")
    void stackingOrder() {
        ApiPriceService apiA = new ApiPriceService();
        List<String> logA = new ArrayList<>();
        PriceService a = new TimingPriceService(new CachingPriceService(apiA), logA);
        ApiPriceService apiB = new ApiPriceService();
        List<String> logB = new ArrayList<>();
        PriceService b = new CachingPriceService(new TimingPriceService(apiB, logB));
        for (int i = 0; i < 3; i++) {
            a.price(1);
            b.price(1);
        }
        ApiPriceService apiT = new ApiPriceService();
        List<String> logT = new ArrayList<>();
        PriceService timingOnly = new TimingPriceService(apiT, logT);
        for (int i = 0; i < 3; i++) {
            timingOnly.price(1);
        }

        assertThat(logA).hasSize(3);
        assertThat(logB).hasSize(1);
        assertThat(apiA.calls).isEqualTo(1);
        assertThat(apiB.calls).isEqualTo(1);
        assertThat(apiT.calls).isEqualTo(3);
        CiOutput.record(LESSON, "stack", "같은 상품 price(1)을 세 번\n\n"
                + "a = Timing(Caching(api)) → 시간 로그 " + logA.size() + "번, 진짜 API " + apiA.calls + "번\n"
                + "b = Caching(Timing(api)) → 시간 로그 " + logB.size() + "번, 진짜 API " + apiB.calls + "번\n"
                + "Timing(api)만            → 시간 로그 " + logT.size() + "번, 진짜 API " + apiT.calls + "번");
    }

    @Test
    @DisplayName("동적 프록시: 모든 호출이 핸들러로 가고, 클래스 이름에 $Proxy가 있고, 클래스를 주면 IllegalArgumentException")
    void dynamicProxy() {
        ApiPriceService api = new ApiPriceService();
        List<String> handled = new ArrayList<>();
        PriceService proxy = (PriceService) Proxy.newProxyInstance(
                PriceService.class.getClassLoader(),
                new Class<?>[] {PriceService.class},
                (p, method, args) -> {
                    handled.add(method.getName());
                    return method.invoke(api, args);
                });

        proxy.price(1);
        proxy.price(1);
        String name = proxy.getClass().getName();

        assertThat(handled).containsExactly("price", "price");
        assertThat(api.calls).isEqualTo(2);
        assertThat(name).contains("$Proxy");
        assertThatThrownBy(() -> Proxy.newProxyInstance(ApiPriceService.class.getClassLoader(),
                new Class<?>[] {ApiPriceService.class}, (p, m, a) -> null))
                .isInstanceOf(IllegalArgumentException.class);
        CiOutput.record(LESSON, "dynamic", "proxy.price(1); proxy.price(1);\n→ 핸들러가 받은 호출: " + handled
                + "\n→ 프록시의 클래스 이름: " + name
                + "\n\n인터페이스가 아닌 ApiPriceService.class를 주면\n→ IllegalArgumentException");
    }

    @Test
    @DisplayName("자기 호출: 프록시로 total([1, 2])를 부르면 핸들러는 total만 받는다")
    void selfInvocationSkipsProxy() {
        ApiPriceService api = new ApiPriceService();
        List<String> handled = new ArrayList<>();
        PriceService proxy = (PriceService) Proxy.newProxyInstance(
                PriceService.class.getClassLoader(),
                new Class<?>[] {PriceService.class},
                (p, method, args) -> {
                    handled.add(method.getName());
                    return method.invoke(api, args);
                });

        int sum = proxy.total(List.of(1L, 2L));

        assertThat(sum).isEqualTo(3000);
        assertThat(handled).containsExactly("total");
        assertThat(api.calls).isEqualTo(2);
        CiOutput.record(LESSON, "self", "proxy.total([1, 2])\n→ 핸들러가 받은 호출: " + handled
                + "\n→ 진짜 price 실행: " + api.calls + "번 (this.price는 프록시를 지나지 않음)");
    }

    @Test
    @DisplayName("스프링 ProxyFactory와 MethodInterceptor로도 자기 호출은 인터셉터를 지나지 않는다")
    void springProxyFactorySelfInvocation() {
        ApiPriceService api = new ApiPriceService();
        List<String> intercepted = new ArrayList<>();
        ProxyFactory factory = new ProxyFactory(api);
        factory.addInterface(PriceService.class);
        factory.addAdvice((MethodInterceptor) invocation -> {
            intercepted.add(invocation.getMethod().getName());
            return invocation.proceed();
        });
        PriceService proxy = (PriceService) factory.getProxy();

        proxy.price(1);
        proxy.total(List.of(1L, 2L));

        assertThat(intercepted).containsExactly("price", "total");
        CiOutput.record(LESSON, "spring", "proxy.price(1); proxy.total([1, 2]);\n→ 인터셉터가 받은 호출: " + intercepted
                + "\n→ total 안의 this.price 두 번은 인터셉터를 지나지 않았다");
    }
}
