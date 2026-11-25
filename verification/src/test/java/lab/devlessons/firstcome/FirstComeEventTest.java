package lab.devlessons.firstcome;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 레슨 "선착순 이벤트 설계"의 주장을 Redis 컨테이너로 검증한다.
 * 쿠폰 100장에 사용자 500명이 몰리고, 그 가운데 100명은 버튼을 두 번 누른다(요청 600건).
 */
class FirstComeEventTest {

    static final String LESSON = "first-come-event";
    static final int COUPONS = 100;
    static final int USERS = 500;

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    static RedisClient client;
    static StatefulRedisConnection<String, String> conn;
    static RedisCommands<String, String> redis;

    /** 이미 참여했으면 -2, 남은 수량이 없으면 -1, 아니면 참여 기록, 수량 차감, 발급 대기열에 넣고 남은 수량 */
    static final String ENTER = """
            if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then return -2 end
            local left = tonumber(redis.call('GET', KEYS[1]))
            if left <= 0 then return -1 end
            redis.call('SADD', KEYS[2], ARGV[1])
            redis.call('RPUSH', KEYS[3], ARGV[1])
            return redis.call('DECR', KEYS[1])
            """;

    @BeforeAll
    static void start() {
        REDIS.start();
        client = RedisClient.create(RedisURI.create(REDIS.getHost(), REDIS.getMappedPort(6379)));
        conn = client.connect();
        redis = conn.sync();
    }

    @AfterAll
    static void stop() {
        conn.close();
        client.shutdown();
        REDIS.stop();
    }

    @BeforeEach
    void reset() {
        redis.flushall();
        redis.set("event:coupon:left", String.valueOf(COUPONS));
    }

    /** 사용자 500명이 동시에, 앞의 100명은 두 번씩 요청한다. 요청마다 자기 Redis 연결을 쓰는 50개 스레드 */
    private Result rush(Entry entry) throws Exception {
        List<String> requests = new ArrayList<>();
        for (int u = 1; u <= USERS; u++) {
            requests.add("user:" + u);
            if (u <= 100) {
                requests.add("user:" + u);
            }
        }
        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger won = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        AtomicInteger dup = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        int per = requests.size() / 50;
        for (int t = 0; t < 50; t++) {
            List<String> mine = requests.subList(t * per, (t + 1) * per);
            fs.add(pool.submit(() -> {
                try (StatefulRedisConnection<String, String> c = client.connect()) {
                    RedisCommands<String, String> r = c.sync();
                    go.await();
                    for (String user : mine) {
                        long res = entry.enter(r, user);
                        if (res >= 0) {
                            won.incrementAndGet();
                        } else if (res == -1) {
                            soldOut.incrementAndGet();
                        } else {
                            dup.incrementAndGet();
                        }
                    }
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : fs) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
        long distinct = redis.smembers("event:coupon:users").size();
        long queued = redis.llen("event:coupon:queue");
        return new Result(won.get(), soldOut.get(), dup.get(), distinct, queued, Long.parseLong(redis.get("event:coupon:left")));
    }

    interface Entry {
        long enter(RedisCommands<String, String> r, String user);
    }

    record Result(int won, int soldOut, int dup, long distinctWinners, long queued, long left) {
        String text() {
            return "당첨 응답 " + won + ", 품절 " + soldOut + ", 중복 거절 " + dup
                    + " / 당첨된 서로 다른 사용자 " + distinctWinners + "명, 발급 대기열 " + queued + "건, 남은 수량 " + left;
        }
    }

    @Test
    @DisplayName("Lua 하나로 참여 확인, 수량 차감, 기록을 하면 요청 600건에도 100명이 한 번씩 당첨되고 수량은 0에서 멈춘다")
    void atomicEntry() throws Exception {
        Result atomic = rush((r, user) -> r.eval(ENTER, ScriptOutputType.INTEGER,
                new String[] {"event:coupon:left", "event:coupon:users", "event:coupon:queue"}, user));

        assertThat(atomic.won()).isEqualTo(COUPONS);
        assertThat(atomic.distinctWinners()).isEqualTo(COUPONS);
        assertThat(atomic.queued()).isEqualTo(COUPONS);
        assertThat(atomic.left()).isZero();
        CiOutput.record(LESSON, "rush", "[쿠폰 100장, 사용자 500명(100명은 두 번 누름), 요청 600건, Lua 하나로]\n" + atomic.text());
    }

    @Test
    @DisplayName("같은 사용자가 두 번 눌러 두 요청이 동시에 참여 확인을 지나면, 확인과 기록을 따로 하는 코드는 두 번 당첨시킨다. Lua는 한 번")
    void doubleClick() throws Exception {
        java.util.concurrent.CyclicBarrier bothChecked = new java.util.concurrent.CyclicBarrier(2);
        Entry separate = (r, user) -> {
            boolean already = r.sismember("event:coupon:users", user);
            try {
                bothChecked.await(10, TimeUnit.SECONDS); // 두 요청이 모두 "참여 안 함"을 본 뒤
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            if (already) {
                return -2;
            }
            long left = r.decr("event:coupon:left");
            if (left < 0) {
                return -1;
            }
            r.sadd("event:coupon:users", user);
            r.rpush("event:coupon:queue", user);
            return left;
        };
        Entry atomic = (r, user) -> r.eval(ENTER, ScriptOutputType.INTEGER,
                new String[] {"event:coupon:left", "event:coupon:users", "event:coupon:queue"}, user);

        List<String> log = new ArrayList<>();
        for (boolean useLua : new boolean[] {false, true}) {
            reset();
            Entry entry = useLua ? atomic : separate;
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<Future<Long>> fs = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                fs.add(pool.submit(() -> {
                    try (StatefulRedisConnection<String, String> c = client.connect()) {
                        return entry.enter(c.sync(), "user:7");
                    }
                }));
            }
            List<Long> results = new ArrayList<>();
            for (Future<Long> f : fs) {
                results.add(f.get(30, TimeUnit.SECONDS));
            }
            pool.shutdown();
            long queued = redis.llen("event:coupon:queue");
            log.add((useLua ? "Lua 하나로" : "확인과 기록을 따로") + ": 응답 " + results + ", 발급 대기열에 user:7 " + queued + "번");
            assertThat(queued).isEqualTo(useLua ? 1 : 2);
        }
        CiOutput.record(LESSON, "double-click", "[user:7이 두 번 눌러 두 요청이 동시에 도착, 남은 수량 100]\n"
                + "(응답: 0 이상은 당첨 뒤 남은 수량, -2는 이미 참여)\n" + String.join("\n", log));
    }

    @Test
    @DisplayName("발급 대기열에 쌓인 당첨자를 워커가 정해진 속도로 꺼내 DB에 저장한다. 대기열은 당첨 순서를 지킨다")
    void queueDrainsInOrder() throws Exception {
        rush((r, user) -> r.eval(ENTER, ScriptOutputType.INTEGER,
                new String[] {"event:coupon:left", "event:coupon:users", "event:coupon:queue"}, user));
        List<String> firstThree = redis.lrange("event:coupon:queue", 0, 2);

        int saved = 0;
        int batches = 0;
        while (true) {
            List<String> batch = redis.lpop("event:coupon:queue", 20); // 한 번에 20건씩 DB에 저장한다고 친다
            if (batch == null || batch.isEmpty()) {
                break;
            }
            saved += batch.size();
            batches++;
        }

        assertThat(saved).isEqualTo(COUPONS);
        assertThat(batches).isEqualTo(5);
        CiOutput.record(LESSON, "queue", "[발급 대기열 100건을 워커가 20건씩 꺼냄]\n"
                + "대기열 앞의 세 명: " + firstThree + "\n"
                + "꺼낸 횟수 " + batches + "번, DB에 저장 " + saved + "건");
    }
}
