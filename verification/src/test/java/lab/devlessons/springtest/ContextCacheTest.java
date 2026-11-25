package lab.devlessons.springtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 레슨 "스프링 테스트"(T3) 질문 4: 설정이 같은 테스트 클래스는 컨텍스트를 다시 쓰고, @MockitoBean이 다르면 새로 만든다.
 * 안쪽 테스트 클래스 셋을 JUnit의 EngineTestKit으로 이 테스트 안에서 돌리고, 컨텍스트가 만들어진 횟수를 센다.
 * 안쪽 클래스는 시스템 프로퍼티가 있을 때만 돌아서 평소 빌드에서는 건너뛴다.
 */
class ContextCacheTest {

    static final AtomicInteger CREATED = new AtomicInteger();
    static final List<String> LOG = new ArrayList<>();

    interface PaymentClient {
        String pay();
    }

    interface MailSender {
        void send();
    }

    /** 컨텍스트가 만들어질 때 한 번 생기는 빈 */
    static class Counter {
        Counter() {
            CREATED.incrementAndGet();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CacheApp {
        @Bean
        Counter counter() {
            return new Counter();
        }

        @Bean
        PaymentClient paymentClient() {
            return () -> "paid";
        }

        @Bean
        MailSender mailSender() {
            return () -> {
            };
        }
    }

    @Test
    @DisplayName("설정이 같은 두 클래스는 컨텍스트를 한 번 만들고, @MockitoBean이 다른 클래스는 새로 만든다")
    void contextIsCachedBySameConfiguration() {
        CREATED.set(0);
        LOG.clear();
        System.setProperty("springtest.inner", "true");
        try {
            var result = EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(OrderTest.class), selectClass(OrderTest2.class),
                            selectClass(CouponTest.class))
                    .execute();
            result.testEvents().assertStatistics(s -> s.succeeded(3).failed(0));
        } finally {
            System.clearProperty("springtest.inner");
        }

        assertThat(CREATED.get()).isEqualTo(2);
        CiOutput.record(FullContextTest.LESSON, "cache", String.join("\n", LOG)
                + "\n\n컨텍스트를 만든 횟수 → " + CREATED.get());
    }

    static void log(String testClass) {
        LOG.add("%s 실행 → 지금까지 만든 컨텍스트 %d개".formatted(testClass, CREATED.get()));
    }

    @SpringBootTest(classes = CacheApp.class)
    @EnabledIfSystemProperty(named = "springtest.inner", matches = "true")
    static class OrderTest {
        @MockitoBean PaymentClient pay;

        @Test
        void run() {
            log("OrderTest   (@MockitoBean PaymentClient)");
        }
    }

    @SpringBootTest(classes = CacheApp.class)
    @EnabledIfSystemProperty(named = "springtest.inner", matches = "true")
    static class OrderTest2 {
        @MockitoBean PaymentClient pay;

        @Test
        void run() {
            log("OrderTest2  (@MockitoBean PaymentClient)");
        }
    }

    @SpringBootTest(classes = CacheApp.class)
    @EnabledIfSystemProperty(named = "springtest.inner", matches = "true")
    static class CouponTest {
        @MockitoBean MailSender mail;

        @Test
        void run() {
            log("CouponTest  (@MockitoBean MailSender)");
        }
    }
}
