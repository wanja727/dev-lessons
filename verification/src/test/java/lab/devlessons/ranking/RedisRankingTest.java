package lab.devlessons.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScoredValue;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/** 레슨 "Redis 자료구조와 랭킹"의 주장을 실제 Redis 7에서 검증한다. */
class RedisRankingTest {

    static final String LESSON = "redis-ranking";

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    static RedisClient client;
    static StatefulRedisConnection<String, String> connection;
    static RedisCommands<String, String> redis;

    @BeforeAll
    static void start() {
        REDIS.start();
        client = RedisClient.create(RedisURI.create(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connection = client.connect();
        redis = connection.sync();
    }

    @AfterAll
    static void stop() {
        connection.close();
        client.shutdown();
        REDIS.stop();
    }

    @BeforeEach
    void setUp() {
        redis.flushall();
    }

    private static String top(List<ScoredValue<String>> list) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            ScoredValue<String> v = list.get(i);
            out.add((i + 1) + "위 " + v.getValue() + " " + (long) v.getScore() + "점");
        }
        return String.join("\n", out);
    }

    @Test
    @DisplayName("자료구조 다섯 가지: String 카운터, Hash 필드, List 최근 본 상품(5개 유지), Set 중복 없는 방문자, Sorted Set 점수 순서")
    void fiveDataStructures() {
        redis.incr("views:product:1");
        redis.incr("views:product:1");
        redis.hset("user:1", Map.of("name", "민지", "level", "3"));
        for (int i = 1; i <= 7; i++) {
            redis.lpush("recent:user:1", "product:" + i);
            redis.ltrim("recent:user:1", 0, 4); // 최근 5개만
        }
        redis.sadd("visitors:2026-11-17", "user:1", "user:2", "user:1");
        redis.zadd("score", 30, "민지");
        redis.zadd("score", 50, "준호");

        assertThat(redis.get("views:product:1")).isEqualTo("2");
        assertThat(redis.hget("user:1", "level")).isEqualTo("3");
        assertThat(redis.lrange("recent:user:1", 0, -1)).containsExactly("product:7", "product:6", "product:5", "product:4", "product:3");
        assertThat(redis.scard("visitors:2026-11-17")).isEqualTo(2);
        assertThat(redis.zrevrange("score", 0, -1)).containsExactly("준호", "민지");
        CiOutput.record(LESSON, "structures", "INCR views:product:1 (두 번) → " + redis.get("views:product:1") + "\n"
                + "HGET user:1 level → " + redis.hget("user:1", "level") + "\n"
                + "LPUSH 7번 + LTRIM 0 4 → " + redis.lrange("recent:user:1", 0, -1) + "\n"
                + "SADD user:1, user:2, user:1 → SCARD " + redis.scard("visitors:2026-11-17") + "\n"
                + "ZREVRANGE score → " + redis.zrevrange("score", 0, -1));
    }

    @Test
    @DisplayName("Sorted Set 랭킹: ZINCRBY로 점수를 더하고, ZREVRANGE로 상위 N명, ZREVRANK로 내 순위(0부터)를 구한다")
    void ranking() {
        redis.zincrby("ranking:2026-11-17", 120, "민지");
        redis.zincrby("ranking:2026-11-17", 80, "준호");
        redis.zincrby("ranking:2026-11-17", 150, "서연");
        redis.zincrby("ranking:2026-11-17", 50, "준호"); // 준호 130

        List<ScoredValue<String>> top3 = redis.zrevrangeWithScores("ranking:2026-11-17", 0, 2);
        Long myRank = redis.zrevrank("ranking:2026-11-17", "준호");

        assertThat(top3).extracting(ScoredValue::getValue).containsExactly("서연", "준호", "민지");
        assertThat(myRank).isEqualTo(1L);
        CiOutput.record(LESSON, "ranking", "ZREVRANGE ranking 0 2 WITHSCORES\n" + top(top3)
                + "\n\nZREVRANK ranking 준호 → " + myRank + " (0부터 세니 2위)");
    }

    @Test
    @DisplayName("동점: ZREVRANGE는 점수가 같으면 멤버 이름의 사전 역순으로 놓는다. 먼저 달성한 사람을 앞에 두려면 시간을 점수에 섞는다")
    void tieBreaking() {
        redis.zadd("plain", 100, "alice");
        redis.zadd("plain", 100, "bob");
        List<String> plain = redis.zrevrange("plain", 0, -1);

        // bob이 먼저(10시), alice가 나중(11시) 100점 달성: 점수 = 포인트 x 10^10 + (기준 시각 - 달성 시각)
        long base = 9_999_999_999L;
        long bobAt = 1_700_000_000L; // 초 단위 시각
        long aliceAt = 1_700_003_600L;
        redis.zadd("tiebreak", 100 * 10_000_000_000d + (base - bobAt), "bob");
        redis.zadd("tiebreak", 100 * 10_000_000_000d + (base - aliceAt), "alice");
        redis.zadd("tiebreak", 99 * 10_000_000_000d + (base - 1_600_000_000L), "carol");
        List<String> withTime = redis.zrevrange("tiebreak", 0, -1);

        assertThat(plain).containsExactly("bob", "alice");
        assertThat(withTime).containsExactly("bob", "alice", "carol");
        CiOutput.record(LESSON, "tie", "[둘 다 100점, bob이 1시간 먼저 달성]\n"
                + "점수만: ZREVRANGE → " + plain + " (이름 사전 역순)\n"
                + "점수 + 시간: ZREVRANGE → " + withTime + " (먼저 달성한 bob 앞, 99점 carol은 뒤)");
    }

    @Test
    @DisplayName("동시에 ZINCRBY 1,000번 (스레드 50개)을 해도 점수를 잃지 않는다: Redis는 명령을 하나씩 실행한다")
    void concurrentIncrementsAreAtomic() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(50);
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            fs.add(pool.submit(() -> redis.zincrby("ranking", 1, "민지")));
        }
        for (Future<?> f : fs) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(redis.zscore("ranking", "민지")).isEqualTo(1000.0);
    }

    @Test
    @DisplayName("주간 랭킹: 날짜별 키를 ZUNIONSTORE로 합치면 점수가 더해진다. 날짜별 키에는 EXPIRE로 수명을 준다")
    void weeklyUnion() {
        redis.zincrby("ranking:day1", 100, "민지");
        redis.zincrby("ranking:day1", 70, "준호");
        redis.zincrby("ranking:day2", 50, "민지");
        redis.zincrby("ranking:day2", 120, "준호");
        redis.expire("ranking:day1", 8 * 24 * 3600);

        redis.zunionstore("ranking:week", "ranking:day1", "ranking:day2");

        assertThat(redis.zscore("ranking:week", "준호")).isEqualTo(190.0);
        assertThat(redis.zscore("ranking:week", "민지")).isEqualTo(150.0);
        assertThat(redis.ttl("ranking:day1")).isGreaterThan(0);
        CiOutput.record(LESSON, "weekly", "day1: 민지 100, 준호 70\nday2: 민지 50, 준호 120\n"
                + "ZUNIONSTORE ranking:week 2 ranking:day1 ranking:day2\n" + top(redis.zrevrangeWithScores("ranking:week", 0, -1)));
    }

    @Test
    @DisplayName("10만 명 랭킹에서도 상위 10명과 내 순위를 바로 구한다")
    void largeRanking() {
        for (int start = 0; start < 100_000; start += 1_000) {
            List<ScoredValue<String>> batch = new ArrayList<>();
            for (int i = start; i < start + 1_000; i++) {
                batch.add(ScoredValue.just(i, "user:" + i));
            }
            @SuppressWarnings("unchecked")
            ScoredValue<String>[] values = batch.toArray(new ScoredValue[0]);
            redis.zadd("big", values);
        }
        long t0 = System.nanoTime();
        List<String> top10 = redis.zrevrange("big", 0, 9);
        Long rank = redis.zrevrank("big", "user:50000");
        long micros = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - t0);

        assertThat(top10.get(0)).isEqualTo("user:99999");
        assertThat(rank).isEqualTo(49_999L);
        CiOutput.record(LESSON, "large", "[멤버 10만 명]\nZREVRANGE big 0 9 + ZREVRANK big user:50000 → 두 명령 합계 " + micros + "μs\n"
                + "1위 " + top10.get(0) + ", user:50000의 순위 " + (rank + 1) + "위");
    }
}
