package lab.devlessons.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lab.devlessons.inventory.InventoryServices.ConditionalUpdateInventoryService;
import lab.devlessons.inventory.InventoryServices.OptimisticInventoryService;
import lab.devlessons.inventory.InventoryServices.PessimisticInventoryService;
import lab.devlessons.inventory.InventoryServices.ReadModifyWriteInventoryService;
import lab.devlessons.inventory.InventoryServices.SoldOutException;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 레슨 "재고 차감 동시성"의 주장을 실제 PostgreSQL과 Redis에서 검증한다.
 * 재고 50개인 상품에 주문 100건이 동시에 들어온다. 테스트에는 트랜잭션이 없다.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class InventoryConcurrencyTest {

    static final String LESSON = "stock-concurrency";
    static final long ITEM = 1L;
    static final int STOCK = 50;
    static final int ORDERS = 100;

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    static RedisClient redisClient;
    static StatefulRedisConnection<String, String> redisConnection;
    static RedisCommands<String, String> redis;
    /** 실행 결과를 모아 마지막에 표로 남긴다 */
    static final Map<String, String> summary = new LinkedHashMap<>();

    /** 재고가 요청 수량 이상일 때만 줄인다. 모자라면 -1. 스크립트 전체가 한 번에 실행된다 */
    static final String LUA_DECREASE = """
            local stock = tonumber(redis.call('GET', KEYS[1]))
            if stock == nil or stock < tonumber(ARGV[1]) then
              return -1
            end
            return redis.call('DECRBY', KEYS[1], ARGV[1])
            """;

    @Autowired ReadModifyWriteInventoryService readModifyWrite;
    @Autowired ConditionalUpdateInventoryService conditionalUpdate;
    @Autowired PessimisticInventoryService pessimistic;
    @Autowired OptimisticInventoryService optimistic;
    @Autowired InventoryItemRepository items;
    @Autowired VersionedInventoryItemRepository versionedItems;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        redisClient = RedisClient.create(RedisURI.create(REDIS.getHost(), REDIS.getMappedPort(6379)));
        redisConnection = redisClient.connect();
        redis = redisConnection.sync();
    }

    @AfterAll
    static void stopRedis() {
        StringBuilder table = new StringBuilder("[재고 50개, 동시 주문 100건]\n");
        summary.forEach((k, v) -> table.append(k).append(": ").append(v).append('\n'));
        CiOutput.record(LESSON, "summary", table.toString().trim());
        redisConnection.close();
        redisClient.shutdown();
        REDIS.stop();
    }

    @BeforeEach
    void setUp() {
        items.deleteAll();
        versionedItems.deleteAll();
        items.save(new InventoryItem(ITEM, STOCK));
        versionedItems.save(new VersionedInventoryItem(ITEM, STOCK));
        redis.set("stock:1", String.valueOf(STOCK));
        readModifyWrite.setAfterRead(() -> { });
    }

    @AfterEach
    void tearDown() {
        readModifyWrite.setAfterRead(() -> { });
    }

    record Result(int success, int soldOut, int conflict, long millis) {
        String text(int finalStock) {
            return "성공 " + success + ", 품절 " + soldOut + (conflict > 0 ? ", 충돌 실패 " + conflict : "")
                    + ", 남은 재고 " + finalStock + ", " + millis + "ms";
        }
    }

    /** 주문 100건을 동시에 시작한다. 예외 종류로 결과를 센다 */
    private Result orderConcurrently(Runnable order) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(ORDERS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < ORDERS; i++) {
            futures.add(pool.submit((Callable<Void>) () -> {
                start.await();
                try {
                    order.run();
                    success.incrementAndGet();
                } catch (SoldOutException e) {
                    soldOut.incrementAndGet();
                } catch (OptimisticLockingFailureException e) {
                    conflict.incrementAndGet();
                }
                return null;
            }));
        }
        long t0 = System.nanoTime();
        start.countDown();
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        pool.shutdown();
        return new Result(success.get(), soldOut.get(), conflict.get(), millis);
    }

    private int dbStock() {
        return items.findById(ITEM).orElseThrow().getQuantity();
    }

    @Test
    @DisplayName("읽고-계산하고-쓰기: 동시 주문 100건에서 재고 50개보다 많이 팔린다 (초과 판매)")
    void readModifyWriteOversells() throws Exception {
        readModifyWrite.setAfterRead(() -> sleep(10)); // 읽기와 쓰기 사이 틈을 넓힌다 (실서버의 다른 처리 시간)

        Result r = orderConcurrently(() -> readModifyWrite.order(ITEM));

        assertThat(r.success()).isGreaterThan(STOCK);
        summary.put("1. 읽고-계산하고-쓰기", r.text(dbStock()));
    }

    @Test
    @DisplayName("조건부 UPDATE: 정확히 50건 성공, 50건 품절, 남은 재고 0")
    void conditionalUpdateIsExact() throws Exception {
        Result r = orderConcurrently(() -> conditionalUpdate.order(ITEM));

        assertThat(r.success()).isEqualTo(STOCK);
        assertThat(r.soldOut()).isEqualTo(ORDERS - STOCK);
        assertThat(dbStock()).isZero();
        summary.put("2. 조건부 UPDATE", r.text(dbStock()));
    }

    @Test
    @DisplayName("비관적 락(SELECT ... FOR UPDATE): 정확히 50건 성공, 남은 재고 0")
    void pessimisticLockIsExact() throws Exception {
        Result r = orderConcurrently(() -> pessimistic.order(ITEM));

        assertThat(r.success()).isEqualTo(STOCK);
        assertThat(r.soldOut()).isEqualTo(ORDERS - STOCK);
        assertThat(dbStock()).isZero();
        summary.put("3. 비관적 락", r.text(dbStock()));
    }

    @Test
    @DisplayName("낙관적 락(재시도 없음): 초과 판매는 없지만, 재고가 있는데도 충돌로 실패하는 주문이 생긴다")
    void optimisticLockFailsUnderContention() throws Exception {
        Result r = orderConcurrently(() -> optimistic.order(ITEM));
        int left = versionedItems.findById(ITEM).orElseThrow().getQuantity();

        assertThat(r.success() + left).isEqualTo(STOCK); // 판 만큼만 줄었다
        assertThat(r.conflict()).isPositive();
        summary.put("4. 낙관적 락 (재시도 없음)", r.text(left));
    }

    @Test
    @DisplayName("Redis DECR만 쓰면: 재고 값이 음수(-50)까지 내려간다. 결과가 0 이상인 50건만 성공으로 친다")
    void redisDecrGoesNegative() throws Exception {
        Result r = orderConcurrently(() -> {
            long left = redis.decr("stock:1");
            if (left < 0) {
                throw new SoldOutException();
            }
        });

        assertThat(r.success()).isEqualTo(STOCK);
        assertThat(redis.get("stock:1")).isEqualTo("-50");
        summary.put("5. Redis DECR", r.text(Integer.parseInt(redis.get("stock:1"))) + " (음수까지 내려감)");
    }

    @Test
    @DisplayName("Redis Lua 스크립트: 확인과 차감이 한 번에 실행돼 정확히 50건 성공, 재고는 0에서 멈춘다")
    void redisLuaIsExact() throws Exception {
        Result r = orderConcurrently(() -> {
            Long left = redis.eval(LUA_DECREASE, ScriptOutputType.INTEGER, new String[] {"stock:1"}, "1");
            if (left < 0) {
                throw new SoldOutException();
            }
        });

        assertThat(r.success()).isEqualTo(STOCK);
        assertThat(r.soldOut()).isEqualTo(ORDERS - STOCK);
        assertThat(redis.get("stock:1")).isEqualTo("0");
        summary.put("6. Redis Lua 스크립트", r.text(Integer.parseInt(redis.get("stock:1"))));
    }

    @Test
    @DisplayName("Redis는 명령을 하나씩 실행한다: 동시 INCR 1,000번은 정확히 1,000")
    void redisCommandsAreAtomic() throws Exception {
        redis.set("counter", "0");
        ExecutorService pool = Executors.newFixedThreadPool(50);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            futures.add(pool.submit(() -> redis.incr("counter")));
        }
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(redis.get("counter")).isEqualTo("1000");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
