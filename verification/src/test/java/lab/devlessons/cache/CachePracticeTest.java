package lab.devlessons.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 레슨 "캐시 전략과 무효화"의 크기 한도와 스프링 캐시 추상화(@Cacheable)를 검증한다. DB 대신 호출 횟수를 센다. */
class CachePracticeTest {

    static final String LESSON = "cache-strategy";

    @Test
    @DisplayName("크기 한도가 2인 Caffeine 캐시에 셋째를 넣으면 하나를 내보내 2개만 남는다")
    void sizeLimitEvicts() {
        Cache<String, Long> cache = Caffeine.newBuilder().maximumSize(2).executor(Runnable::run).build();
        cache.put("A", 1L);
        cache.put("B", 2L);
        cache.getIfPresent("A");
        cache.put("C", 3L);
        cache.cleanUp();

        TreeSet<String> left = new TreeSet<>(cache.asMap().keySet());
        assertThat(left).hasSize(2).contains("C");
        CiOutput.record(LESSON, "evict", "[최대 크기 2인 Caffeine 캐시]\n"
                + "A, B를 넣고 A를 읽은 뒤 C를 넣음\n"
                + "남은 키: " + left);
    }

    static class PriceService {
        // 프록시(CGLIB)의 필드가 아니라 진짜 객체의 호출을 세려고 static으로 둔다
        static final AtomicInteger loads = new AtomicInteger();

        @Cacheable(cacheNames = "price", key = "#id")
        public long price(long id) {
            loads.incrementAndGet();
            return 10_000;
        }

        @CacheEvict(cacheNames = "price", key = "#id")
        public void change(long id, long p) {
        }

        /** 같은 클래스 안에서 캐시 메서드를 부른다 */
        public long priceInside(long id) {
            return this.price(id);
        }
    }

    @Configuration
    @EnableCaching
    static class CacheConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("price");
        }

        @Bean
        PriceService priceService() {
            return new PriceService();
        }
    }

    @Test
    @DisplayName("@Cacheable 메서드를 두 번 부르면 한 번만 실행되고, @CacheEvict 뒤에는 다시 실행된다. 같은 클래스 안의 호출에는 캐시가 걸리지 않는다")
    void cacheableAndSelfInvocation() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(CacheConfig.class)) {
            PriceService service = ctx.getBean(PriceService.class);
            List<String> log = new ArrayList<>();

            service.price(1);
            service.price(1);
            log.add("price(1) 두 번 → 메서드 실행 " + PriceService.loads.get() + "번");
            assertThat(PriceService.loads.get()).isEqualTo(1);

            service.change(1, 8_000);
            service.price(1);
            log.add("change(1) 뒤 price(1) → 메서드 실행 누적 " + PriceService.loads.get() + "번");
            assertThat(PriceService.loads.get()).isEqualTo(2);

            PriceService.loads.set(0);
            service.priceInside(2);
            service.priceInside(2);
            log.add("priceInside(2) 두 번 (안에서 this.price) → 메서드 실행 " + PriceService.loads.get() + "번");
            assertThat(PriceService.loads.get()).isEqualTo(2);

            CiOutput.record(LESSON, "cacheable", String.join("\n", log));
        }
    }
}
