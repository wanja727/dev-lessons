package lab.devlessons.cache;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.LongUnaryOperator;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/** 레슨 "캐시 전략과 무효화"의 주장을 실제 PostgreSQL과 Redis에서 검증한다. */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class CacheStrategyTest {

    static final String LESSON = "cache-strategy";

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    static RedisClient redisClient;
    static StatefulRedisConnection<String, String> redisConnection;
    static RedisCommands<String, String> redis;

    @Autowired JdbcTemplate jdbc;
    PriceCache cache;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        redisClient = RedisClient.create(RedisURI.create(REDIS.getHost(), REDIS.getMappedPort(6379)));
        redisConnection = redisClient.connect();
        redis = redisConnection.sync();
    }

    @AfterAll
    static void stopRedis() {
        redisConnection.close();
        redisClient.shutdown();
        REDIS.stop();
    }

    @BeforeEach
    void setUp() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS cache_product (id BIGINT PRIMARY KEY, price BIGINT NOT NULL)");
        jdbc.execute("TRUNCATE cache_product");
        jdbc.update("INSERT INTO cache_product VALUES (1, 10000)");
        redis.flushall();
        cache = new PriceCache(jdbc, redis, 60_000);
    }

    @Test
    @DisplayName("cache-aside: 첫 조회만 DB를 읽고, 두 번째부터는 캐시에서 돌려준다")
    void cacheAsideHitsCacheAfterFirstRead() {
        long t0 = System.nanoTime();
        cache.get(1);
        long firstMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        long t1 = System.nanoTime();
        long second = cache.get(1);
        long secondMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t1);

        assertThat(second).isEqualTo(10_000);
        assertThat(cache.dbReads.get()).isEqualTo(1);
        CiOutput.record(LESSON, "hit", "[cache-aside 두 번 조회]\n"
                + "1번째: 캐시 없음 → DB 조회 → 캐시에 저장 (" + firstMs + "ms)\n"
                + "2번째: 캐시에서 바로 (" + secondMs + "ms)\n"
                + "DB 조회 횟수: " + cache.dbReads.get());
    }

    @Test
    @DisplayName("DB만 바꾸고 캐시를 그대로 두면, 캐시가 살아 있는 동안 옛 가격을 돌려준다")
    void updateWithoutInvalidationServesStaleValue() {
        cache.get(1);

        cache.updateDbOnly(1, 8_000);

        assertThat(cache.get(1)).isEqualTo(10_000);
    }

    @Test
    @DisplayName("DB를 바꾼 뒤 캐시를 지우면 다음 조회는 새 가격을 읽는다")
    void updateThenDeleteServesFreshValue() {
        cache.get(1);

        cache.updateDbThenDeleteCache(1, 8_000);

        assertThat(cache.get(1)).isEqualTo(8_000);
        assertThat(cache.dbReads.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("캐시를 먼저 지우고 DB를 바꾸면, 그 사이 조회가 옛 가격을 다시 캐시에 넣어 오래 남는다")
    void deleteThenUpdateRaceLeavesStaleCache() throws Exception {
        CountDownLatch readerReadDb = new CountDownLatch(1);
        CountDownLatch writerDone = new CountDownLatch(1);
        // 조회 요청: DB에서 옛 가격을 읽은 뒤, 쓰기가 끝날 때까지 캐시에 넣기를 미룬다
        cache.afterDbRead = () -> {
            readerReadDb.countDown();
            await(writerDone);
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Long> reader = pool.submit(() -> cache.get(1));

        await(readerReadDb);
        cache.afterDbRead = () -> { };
        cache.deleteCacheThenUpdateDb(1, 8_000); // 쓰기: 캐시 삭제 → DB 갱신
        writerDone.countDown();
        reader.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        long dbPrice = jdbc.queryForObject("SELECT price FROM cache_product WHERE id = 1", Long.class);
        assertThat(dbPrice).isEqualTo(8_000);
        assertThat(redis.get(PriceCache.key(1))).isEqualTo("10000");
        assertThat(cache.get(1)).isEqualTo(10_000); // TTL이 끝날 때까지 옛 가격
        CiOutput.record(LESSON, "stale-race", "[캐시 먼저 삭제 → DB 갱신, 그 사이 조회 1건]\n"
                + "DB 가격: " + dbPrice + "\n"
                + "캐시 가격: " + redis.get(PriceCache.key(1)) + "\n"
                + "남은 TTL: 약 " + redis.pttl(PriceCache.key(1)) / 1000 + "초");
    }

    @Test
    @DisplayName("TTL이 지나면 캐시가 사라지고 다음 조회는 DB를 다시 읽는다")
    void ttlExpires() throws Exception {
        cache = new PriceCache(jdbc, redis, 500);
        cache.get(1);
        cache.updateDbOnly(1, 8_000);
        assertThat(cache.get(1)).isEqualTo(10_000);

        Thread.sleep(700);

        assertThat(redis.exists(PriceCache.key(1))).isZero();
        assertThat(cache.get(1)).isEqualTo(8_000);
    }

    @Test
    @DisplayName("write-through: 쓸 때 캐시도 함께 바꾸면 바로 다음 조회도 DB를 거치지 않고 새 값을 준다")
    void writeThroughKeepsCacheFresh() {
        cache.get(1);

        cache.writeThrough(1, 8_000);

        assertThat(cache.get(1)).isEqualTo(8_000);
        assertThat(cache.dbReads.get()).isEqualTo(1);
    }

    private int concurrentMisses(int requests, LongUnaryOperator read) throws Exception {
        cache.dbReads.set(0);
        redis.del(PriceCache.key(1)); // 방금 만료됨
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> results = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return read.applyAsLong(1);
            }));
        }
        start.countDown();
        for (Future<Long> f : results) {
            assertThat(f.get(30, TimeUnit.SECONDS)).isEqualTo(10_000);
        }
        pool.shutdown();
        return cache.dbReads.get();
    }

    @Test
    @DisplayName("캐시 스탬피드: 만료 순간 동시 조회 100건이 모두 DB로 가고, 단일 로드로 묶으면 DB 조회는 1번")
    void stampedeAndSingleFlight() throws Exception {
        int naive = concurrentMisses(100, id -> cache.get(id));
        int singleFlight = concurrentMisses(100, id -> cache.getSingleFlight(id));

        assertThat(naive).isGreaterThan(10);
        assertThat(singleFlight).isEqualTo(1);
        CiOutput.record(LESSON, "stampede", "[캐시가 막 만료된 순간 동시 조회 100건, DB 조회 50ms]\n"
                + "그냥 cache-aside: DB 조회 " + naive + "번\n"
                + "단일 로드(같은 키는 한 요청만 DB로): DB 조회 " + singleFlight + "번");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("대기 시간 초과");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
