package lab.devlessons.cache;

import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 레슨 "캐시 전략과 무효화": 상품 가격을 Redis에 캐시한다. DB 조회 횟수를 센다.
 * DB 조회에는 pg_sleep으로 50ms를 더해 "느린 원본"을 흉내 낸다.
 */
class PriceCache {

    final JdbcTemplate jdbc;
    final RedisCommands<String, String> redis;
    final long ttlMillis;
    final AtomicInteger dbReads = new AtomicInteger();
    /** 테스트에서 "DB를 읽은 뒤, 캐시에 넣기 전" 틈을 재현하려고 끼워 넣는 지점 */
    volatile Runnable afterDbRead = () -> { };
    private final ConcurrentHashMap<Long, CompletableFuture<Long>> inFlight = new ConcurrentHashMap<>();

    PriceCache(JdbcTemplate jdbc, RedisCommands<String, String> redis, long ttlMillis) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.ttlMillis = ttlMillis;
    }

    static String key(long id) {
        return "price:" + id;
    }

    long loadFromDb(long id) {
        dbReads.incrementAndGet();
        return jdbc.queryForObject("SELECT price FROM (SELECT pg_sleep(0.05)) s, cache_product WHERE id = ?", Long.class, id);
    }

    /** cache-aside 읽기: 캐시에 있으면 그대로, 없으면 DB에서 읽어 TTL과 함께 넣는다 */
    long get(long id) {
        String cached = redis.get(key(id));
        if (cached != null) {
            return Long.parseLong(cached);
        }
        long price = loadFromDb(id);
        afterDbRead.run();
        redis.set(key(id), String.valueOf(price), SetArgs.Builder.px(ttlMillis));
        return price;
    }

    /** 스탬피드 방지: 같은 키를 동시에 놓친 요청들은 첫 요청의 DB 조회 결과를 함께 기다린다 (한 서버 안에서) */
    long getSingleFlight(long id) {
        String cached = redis.get(key(id));
        if (cached != null) {
            return Long.parseLong(cached);
        }
        CompletableFuture<Long> mine = new CompletableFuture<>();
        CompletableFuture<Long> existing = inFlight.putIfAbsent(id, mine);
        if (existing != null) {
            return existing.join();
        }
        try {
            String again = redis.get(key(id)); // 앞선 요청이 방금 채웠을 수 있다
            if (again != null) {
                mine.complete(Long.parseLong(again));
                return Long.parseLong(again);
            }
            long price = loadFromDb(id);
            redis.set(key(id), String.valueOf(price), SetArgs.Builder.px(ttlMillis));
            mine.complete(price);
            return price;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(id);
        }
    }

    void updateDbOnly(long id, long price) {
        jdbc.update("UPDATE cache_product SET price = ? WHERE id = ?", price, id);
    }

    /** 무효화 순서 A: 캐시를 먼저 지우고 DB를 바꾼다 */
    void deleteCacheThenUpdateDb(long id, long price) {
        redis.del(key(id));
        updateDbOnly(id, price);
    }

    /** 무효화 순서 B: DB를 바꾸고 캐시를 지운다 */
    void updateDbThenDeleteCache(long id, long price) {
        updateDbOnly(id, price);
        redis.del(key(id));
    }

    /** write-through: DB와 캐시를 함께 쓴다 */
    void writeThrough(long id, long price) {
        updateDbOnly(id, price);
        redis.set(key(id), String.valueOf(price), SetArgs.Builder.px(ttlMillis));
    }
}
