package lab.devlessons.distlock;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

/** 레슨 "분산 락"의 주장을 Redis 컨테이너로 검증한다. 서버 여러 대 대신 Redis 연결을 따로 가진 스레드를 쓴다. */
class DistributedLockTest {

    static final String LESSON = "distributed-lock";

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    static RedisClient client;
    static StatefulRedisConnection<String, String> conn;
    static RedisCommands<String, String> redis;

    /** 값이 내 토큰일 때만 지운다 */
    static final String UNLOCK = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
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
    void flush() {
        redis.flushall();
    }

    /** 서버 n대가 동시에 task를 한 번씩 실행한다. 서버마다 자기 Redis 연결을 쓴다 */
    private void servers(int n, ServerTask task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int id = i + 1;
            fs.add(pool.submit(() -> {
                try (StatefulRedisConnection<String, String> c = client.connect()) {
                    go.await();
                    task.run(id, c.sync());
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : fs) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
    }

    interface ServerTask {
        void run(int server, RedisCommands<String, String> r) throws Exception;
    }

    static boolean tryLock(RedisCommands<String, String> r, String key, String token, long ttlMillis) {
        return "OK".equals(r.set(key, token, SetArgs.Builder.nx().px(ttlMillis)));
    }

    @Test
    @DisplayName("락 없이 서버 3대가 정산을 돌리면 3번, SET NX PX 락을 잡은 서버만 돌리면 1번 실행된다")
    void lockRunsOnce() throws Exception {
        AtomicInteger noLock = new AtomicInteger();
        servers(3, (id, r) -> noLock.incrementAndGet());

        AtomicInteger withLock = new AtomicInteger();
        List<String> log = new ArrayList<>();
        servers(3, (id, r) -> {
            String token = UUID.randomUUID().toString();
            if (tryLock(r, "lock:settle", token, 10_000)) {
                withLock.incrementAndGet();
                synchronized (log) {
                    log.add("서버 " + id + ": 락 얻음 → 정산 실행");
                }
            } else {
                synchronized (log) {
                    log.add("서버 " + id + ": 락 못 얻음 → 건너뜀");
                }
            }
        });

        assertThat(noLock.get()).isEqualTo(3);
        assertThat(withLock.get()).isEqualTo(1);
        log.sort(null);
        CiOutput.record(LESSON, "once", "[서버 3대가 같은 순간 정산 시작]\n"
                + "락 없음: 정산 " + noLock.get() + "번\n"
                + "SET lock:settle <토큰> NX PX 10000:\n" + String.join("\n", log)
                + "\n→ 정산 " + withLock.get() + "번");
    }

    @Test
    @DisplayName("만료 시간이 없으면 락을 쥔 서버가 죽었을 때 락이 영원히 남고, PX를 주면 만료 뒤 다른 서버가 얻는다")
    void expiryReleasesDeadHolder() throws Exception {
        redis.set("lock:a", "server-1"); // 만료 없이 잡고 서버 1이 죽음
        tryLock(redis, "lock:b", "server-1", 300); // 300ms 만료로 잡고 서버 1이 죽음
        Thread.sleep(500);

        boolean a = tryLock(redis, "lock:a", "server-2", 10_000);
        boolean b = tryLock(redis, "lock:b", "server-2", 10_000);

        assertThat(a).isFalse();
        assertThat(b).isTrue();
        CiOutput.record(LESSON, "expiry", "[서버 1이 락을 잡은 채 죽음, 0.5초 뒤 서버 2가 시도]\n"
                + "만료 없는 락: 서버 2 " + (a ? "얻음" : "못 얻음 (TTL " + redis.ttl("lock:a") + ", 영원히 남음)") + "\n"
                + "PX 300 락: 서버 2 " + (b ? "얻음" : "못 얻음"));
    }

    @Test
    @DisplayName("주인 확인 없이 DEL로 풀면 만료 뒤 다른 서버가 잡은 락을 지운다. 토큰을 확인하는 Lua로 풀면 지우지 않는다")
    void unlockMustCheckOwner() throws Exception {
        List<String> log = new ArrayList<>();
        for (boolean safe : new boolean[] {false, true}) {
            redis.flushall();
            tryLock(redis, "lock:settle", "token-A", 200); // A가 잡음
            Thread.sleep(300); // A가 오래 걸려 락이 만료됨
            tryLock(redis, "lock:settle", "token-B", 10_000); // B가 잡음
            if (safe) {
                redis.eval(UNLOCK, ScriptOutputType.INTEGER, new String[] {"lock:settle"}, "token-A");
            } else {
                redis.del("lock:settle"); // A가 일을 마치고 그냥 지움
            }
            boolean cGot = tryLock(redis, "lock:settle", "token-C", 10_000);
            log.add((safe ? "토큰 확인 Lua로 풀기" : "그냥 DEL로 풀기") + ": 남은 락 주인 "
                    + redis.get("lock:settle") + ", C가 " + (cGot ? "얻음 (B와 동시에 실행)" : "못 얻음"));
            assertThat(cGot).isEqualTo(!safe);
        }
        CiOutput.record(LESSON, "unlock", "[A의 락이 만료된 뒤 B가 잡음, 그다음 A가 일을 마치고 락을 풂, 이어서 C가 시도]\n"
                + String.join("\n", log));
    }

    @Test
    @DisplayName("일이 만료 시간보다 오래 걸리면 두 서버가 동시에 임계 구역에 있다. 펜싱 토큰으로 늦은 쓰기를 거절할 수 있다")
    void expiryDuringWorkAndFencing() throws Exception {
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxInside = new AtomicInteger();
        List<String> writes = new ArrayList<>();
        long[] lastToken = {0};
        servers(2, (id, r) -> {
            if (id == 2) {
                Thread.sleep(300); // 서버 1의 락이 만료된 뒤
            }
            String token = UUID.randomUUID().toString();
            if (!tryLock(r, "lock:job", token, 200)) {
                return;
            }
            long fence = r.incr("fence:job"); // 락을 얻을 때마다 커지는 번호
            int now = inside.incrementAndGet();
            maxInside.accumulateAndGet(now, Math::max);
            Thread.sleep(id == 1 ? 600 : 100); // 서버 1은 GC 멈춤처럼 오래 걸림
            inside.decrementAndGet();
            synchronized (writes) { // 저장소: 더 큰 펜싱 번호만 받는다
                if (fence > lastToken[0]) {
                    lastToken[0] = fence;
                    writes.add("서버 " + id + " (번호 " + fence + ") 쓰기 → 받음");
                } else {
                    writes.add("서버 " + id + " (번호 " + fence + ") 쓰기 → 거절 (이미 " + lastToken[0] + "번이 씀)");
                }
            }
        });

        assertThat(maxInside.get()).isEqualTo(2);
        assertThat(writes).anyMatch(w -> w.contains("거절"));
        CiOutput.record(LESSON, "fencing", "[락 만료 0.2초, 서버 1의 일은 0.6초, 서버 2는 0.3초 뒤 시도]\n"
                + "동시에 임계 구역에 있던 서버 수: 최대 " + maxInside.get() + "\n"
                + String.join("\n", writes));
    }
}
