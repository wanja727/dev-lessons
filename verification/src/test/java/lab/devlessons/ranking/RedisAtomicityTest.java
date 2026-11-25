package lab.devlessons.ranking;

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
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/** 레슨 "Redis"의 질문 3: 명령 하나는 섞이지 않지만, 명령 둘로 나눈 읽고-바꾸기는 값을 잃는다. 서버 50대 대신 연결 50개를 쓴다. */
class RedisAtomicityTest {

    static final String LESSON = "redis-ranking";

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    static RedisClient client;

    @BeforeAll
    static void start() {
        REDIS.start();
        client = RedisClient.create(RedisURI.create(REDIS.getHost(), REDIS.getMappedPort(6379)));
    }

    @AfterAll
    static void stop() {
        client.shutdown();
        REDIS.stop();
    }

    /** 연결 50개가 각자 task를 20번씩 한다 (모두 1,000번) */
    private void run(Task task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> fs = new ArrayList<>();
        for (int t = 0; t < 50; t++) {
            fs.add(pool.submit(() -> {
                try (StatefulRedisConnection<String, String> c = client.connect()) {
                    RedisCommands<String, String> r = c.sync();
                    start.await();
                    for (int i = 0; i < 20; i++) {
                        task.run(r);
                    }
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : fs) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
    }

    interface Task {
        void run(RedisCommands<String, String> r);
    }

    @Test
    @DisplayName("연결 50개가 1,000번 올릴 때 INCR와 ZINCRBY는 정확히 1,000이고, GET 뒤 SET은 1,000보다 작다")
    void singleCommandIsAtomicButGetSetIsNot() throws Exception {
        try (StatefulRedisConnection<String, String> c = client.connect()) {
            RedisCommands<String, String> redis = c.sync();
            redis.flushall();
            redis.set("views:getset", "0");

            run(r -> r.incr("views:incr"));
            run(r -> r.zincrby("ranking", 1, "minji"));
            run(r -> {
                long n = Long.parseLong(r.get("views:getset")) + 1;
                r.set("views:getset", String.valueOf(n));
            });

            long incr = Long.parseLong(redis.get("views:incr"));
            double z = redis.zscore("ranking", "minji");
            long getSet = Long.parseLong(redis.get("views:getset"));
            assertThat(incr).isEqualTo(1_000);
            assertThat(z).isEqualTo(1_000.0);
            assertThat(getSet).isLessThan(1_000);
            CiOutput.record(LESSON, "atomic", "[연결 50개가 20번씩, 모두 1,000번 올림]\n"
                    + "INCR views → " + incr + "\n"
                    + "ZINCRBY ranking 1 minji → " + (long) z + "\n"
                    + "GET 뒤 SET (n + 1) → " + getSet + "  (" + (1_000 - getSet) + "번을 잃음)");
        }
    }
}
