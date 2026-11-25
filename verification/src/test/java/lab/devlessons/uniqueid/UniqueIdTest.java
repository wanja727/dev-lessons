package lab.devlessons.uniqueid;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** 레슨 "유일한 ID 만들기"의 주장을 PostgreSQL 컨테이너와 자바 코드로 검증한다. */
class UniqueIdTest {

    static final String LESSON = "unique-id";

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;

    @BeforeAll
    static void start() {
        PG.start();
        DriverManagerDataSource ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
    }

    @AfterAll
    static void stop() {
        PG.stop();
    }

    @Test
    @DisplayName("자동 증가: 1, 2, 3으로 늘고, 롤백된 INSERT가 쓴 번호는 돌아오지 않아 빈 번호가 생긴다. DB 두 개는 각자 1부터 센다")
    void autoIncrement() {
        jdbc.execute("create table orders_a (id bigint generated always as identity primary key, item text)");
        jdbc.execute("create table orders_b (id bigint generated always as identity primary key, item text)");
        jdbc.update("insert into orders_a(item) values ('a')");
        try {
            tx.executeWithoutResult(s -> {
                jdbc.update("insert into orders_a(item) values ('b')");
                throw new IllegalStateException("롤백");
            });
        } catch (IllegalStateException ignored) {
            // 롤백
        }
        jdbc.update("insert into orders_a(item) values ('c')");
        jdbc.update("insert into orders_b(item) values ('x')");

        List<Long> a = jdbc.queryForList("select id from orders_a order by id", Long.class);
        Long b = jdbc.queryForObject("select id from orders_b", Long.class);
        assertThat(a).containsExactly(1L, 3L);
        assertThat(b).isEqualTo(1L);
        CiOutput.record(LESSON, "auto", "[identity 열]\n"
                + "a 저장 → 롤백된 b 저장 → c 저장\n"
                + "orders_a의 id: " + a + " (2는 롤백된 b가 쓰고 돌아오지 않음)\n"
                + "다른 DB(orders_b)의 첫 id: " + b + " (orders_a의 1과 겹침)");
    }

    /**
     * 시간 순서 UUID(v7과 같은 모양): 앞 48비트에 밀리초 시각, 그 뒤 12비트는 같은 밀리초 안의 카운터(RFC 9562의 방법 1),
     * 나머지는 무작위. 처음에 카운터 자리를 무작위로 두었더니 같은 밀리초 안에서 순서가 섞여 무작위 UUID보다 인덱스가 커졌다
     */
    static final SecureRandom RANDOM = new SecureRandom();
    static long lastMs = -1;
    static long counter = 0;

    static synchronized UUID timeOrderedUuid() {
        long ms = System.currentTimeMillis();
        if (ms == lastMs) {
            counter++;
            if (counter > 0x0FFF) { // 이 밀리초의 카운터를 다 씀
                while (ms <= lastMs) {
                    ms = System.currentTimeMillis();
                }
                counter = 0;
            }
        } else {
            counter = 0;
        }
        lastMs = ms;
        long msb = (ms << 16) | (0x7L << 12) | counter;
        long lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }

    private long indexBytes(String table, List<Object[]> rows) {
        jdbc.execute("create table " + table + " (id uuid primary key)");
        jdbc.batchUpdate("insert into " + table + " values (?)", rows);
        return jdbc.queryForObject("select pg_relation_size('" + table + "_pkey')", Long.class);
    }

    @Test
    @DisplayName("무작위 UUID(v4)를 기본 키로 넣으면 시간 순서 UUID보다 인덱스가 커진다. 10만 개 사이에 겹치는 값은 없다")
    void randomUuidIndex() {
        int n = 100_000;
        List<Object[]> random = new ArrayList<>(n);
        List<Object[]> ordered = new ArrayList<>(n);
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < n; i++) {
            UUID r = UUID.randomUUID();
            seen.add(r);
            random.add(new Object[] {r});
            ordered.add(new Object[] {timeOrderedUuid()});
        }
        long randomBytes = indexBytes("ids_v4", random);
        long orderedBytes = indexBytes("ids_v7", ordered);

        assertThat(seen).hasSize(n);
        assertThat(randomBytes).isGreaterThan(orderedBytes);
        CiOutput.record(LESSON, "uuid-index", "[uuid 기본 키 인덱스에 10만 개씩 넣음]\n"
                + "무작위 UUID(v4): " + randomBytes / 1024 + "KB\n"
                + "시간 순서 UUID(v7 모양): " + orderedBytes / 1024 + "KB\n"
                + "v4 10만 개 가운데 겹친 값: " + (n - seen.size()) + "개");
    }

    /** 스노플레이크 모양: 41비트 밀리초 + 10비트 서버 번호 + 12비트 순번 */
    static class Snowflake {
        static final long EPOCH = 1_735_689_600_000L; // 2025-01-01
        final long node;
        long lastMs = -1;
        long seq = 0;

        Snowflake(long node) {
            this.node = node;
        }

        synchronized long next() {
            long ms = System.currentTimeMillis();
            if (ms == lastMs) {
                seq = (seq + 1) & 0xFFF;
                if (seq == 0) { // 이 밀리초의 4096개를 다 씀
                    while (ms <= lastMs) {
                        ms = System.currentTimeMillis();
                    }
                }
            } else {
                seq = 0;
            }
            lastMs = ms;
            return ((ms - EPOCH) << 22) | (node << 12) | seq;
        }
    }

    @Test
    @DisplayName("스노플레이크 모양 ID: 서버 2대가 동시에 10만 개씩 만들어도 겹치지 않고, 서버마다 만든 순서대로 커진다")
    void snowflake() throws Exception {
        Snowflake s1 = new Snowflake(1);
        Snowflake s2 = new Snowflake(2);
        Set<Long> all = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Boolean>> fs = new ArrayList<>();
        for (Snowflake s : List.of(s1, s2)) {
            fs.add(pool.submit(() -> {
                long prev = -1;
                boolean increasing = true;
                for (int i = 0; i < 100_000; i++) {
                    long id = s.next();
                    increasing &= id > prev;
                    prev = id;
                    all.add(id);
                }
                return increasing;
            }));
        }
        boolean inc1 = fs.get(0).get(30, TimeUnit.SECONDS);
        boolean inc2 = fs.get(1).get(30, TimeUnit.SECONDS);
        pool.shutdown();
        long sample = s1.next();

        assertThat(all).hasSize(200_000);
        assertThat(inc1).isTrue();
        assertThat(inc2).isTrue();
        CiOutput.record(LESSON, "snowflake", "[서버 1, 2가 동시에 10만 개씩]\n"
                + "만든 ID 20만 개 가운데 겹친 값: " + (200_000 - all.size()) + "개\n"
                + "서버마다 만든 순서대로 커짐: " + (inc1 && inc2) + "\n"
                + "예: " + sample + " → 시각 " + ((sample >> 22) + Snowflake.EPOCH) + "ms, 서버 " + ((sample >> 12) & 0x3FF)
                + ", 순번 " + (sample & 0xFFF));
    }
}
