package lab.devlessons.replication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.CRC32;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 레슨 "DB 복제와 샤딩"의 주장을 검증한다.
 * 복제: PostgreSQL 두 대에 논리 복제(publication, subscription)를 걸어, 원본에 쓴 행이 복제본에 언제 보이는지 잰다.
 * 샤딩: PostgreSQL 두 대를 샤드로 두고 회원 id로 나눠 담는다. 다시 나누기는 키 10만 개로 계산한다.
 */
class ReplicationShardingTest {

    static final String LESSON = "replication-sharding";

    static final Network NET = Network.newNetwork();
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> PRIMARY = new PostgreSQLContainer<>("postgres:16-alpine")
            .withNetwork(NET).withNetworkAliases("primary").withCommand("postgres", "-c", "wal_level=logical");
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> REPLICA = new PostgreSQLContainer<>("postgres:16-alpine").withNetwork(NET);
    static JdbcTemplate primary;
    static JdbcTemplate replica;

    @BeforeAll
    static void start() {
        PRIMARY.start();
        REPLICA.start();
        primary = jdbc(PRIMARY);
        replica = jdbc(REPLICA);
        primary.execute("create table member (id bigint primary key, nickname text)");
        replica.execute("create table member (id bigint primary key, nickname text)");
        primary.execute("create publication pub for table member");
        replica.execute("create subscription sub connection 'host=primary port=5432 user=" + PRIMARY.getUsername()
                + " password=" + PRIMARY.getPassword() + " dbname=" + PRIMARY.getDatabaseName() + "' publication pub");
    }

    @AfterAll
    static void stop() {
        REPLICA.stop();
        PRIMARY.stop();
        NET.close();
    }

    static JdbcTemplate jdbc(PostgreSQLContainer<?> c) {
        // 쿼리마다 새 연결을 열면 그사이 복제가 따라잡아 지연이 안 보인다. 연결 하나를 계속 쓴다
        return new JdbcTemplate(new SingleConnectionDataSource(c.getJdbcUrl(), c.getUsername(), c.getPassword(), true));
    }

    private String replicaNickname(long id) {
        List<String> rows = replica.queryForList("select nickname from member where id = ?", String.class, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Test
    @DisplayName("원본에 쓴 직후 복제본에서 읽으면 아직 없을 수 있고, 잠시 뒤에 보인다(복제 지연)")
    void replicationLag() throws Exception {
        // 구독이 처음 맞춰질 때까지 기다린다
        primary.update("insert into member values (0, 'warmup')");
        long deadline = System.currentTimeMillis() + 30_000;
        while (replicaNickname(0) == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        int missedRightAway = 0;
        long maxLagMs = 0;
        for (long id = 1; id <= 20; id++) {
            long t0 = System.nanoTime();
            primary.update("insert into member values (?, ?)", id, "user" + id);
            if (replicaNickname(id) == null) {
                missedRightAway++;
            }
            while (replicaNickname(id) == null) {
                Thread.sleep(5);
            }
            maxLagMs = Math.max(maxLagMs, (System.nanoTime() - t0) / 1_000_000);
        }

        assertThat(replicaNickname(20)).isEqualTo("user20");
        CiOutput.record(LESSON, "lag", "[원본에 회원을 저장하고 바로 복제본에서 읽음, 20번]\n"
                + "바로 읽었을 때 복제본에 아직 없던 횟수: " + missedRightAway + " / 20\n"
                + "복제본에 보이기까지 가장 오래 걸린 시간: " + maxLagMs + "ms\n"
                + "끝난 뒤 복제본의 id 20: " + replicaNickname(20));
    }

    @Test
    @DisplayName("닉네임을 바꾸고 바로 복제본에서 읽으면 옛 닉네임을 볼 수 있다. 원본에서 읽으면 늘 새 값이다")
    void readYourWrites() throws Exception {
        primary.update("insert into member values (100, '민지') on conflict (id) do nothing");
        while (replicaNickname(100) == null) {
            Thread.sleep(10);
        }
        int stale = 0;
        for (int i = 0; i < 20; i++) {
            primary.update("update member set nickname = ? where id = 100", "민지" + i);
            String fromReplica = replicaNickname(100);
            if (!("민지" + i).equals(fromReplica)) {
                stale++;
            }
            String fromPrimary = primary.queryForObject("select nickname from member where id = 100", String.class);
            assertThat(fromPrimary).isEqualTo("민지" + i);
        }
        CiOutput.record(LESSON, "read-your-writes", "[닉네임을 바꾸고 바로 읽기, 20번]\n"
                + "복제본에서 읽어 옛 닉네임을 본 횟수: " + stale + " / 20\n"
                + "원본에서 읽어 옛 닉네임을 본 횟수: 0 / 20");
    }

    @Test
    @DisplayName("복제본이 밀려 있는 동안(구독을 잠시 멈춤) 원본의 변경이 복제본에 보이지 않다가, 따라잡으면 보인다")
    void laggingReplica() throws Exception {
        primary.update("insert into member values (200, '준호') on conflict (id) do nothing");
        while (replicaNickname(200) == null) {
            Thread.sleep(10);
        }
        replica.execute("alter subscription sub disable"); // 복제본이 바빠 밀린 상황
        Thread.sleep(500);
        primary.update("update member set nickname = '준호2' where id = 200");
        Thread.sleep(500);
        String during = replicaNickname(200);
        replica.execute("alter subscription sub enable");
        long t0 = System.currentTimeMillis();
        while (!"준호2".equals(replicaNickname(200))) {
            Thread.sleep(20);
        }
        long caughtUp = System.currentTimeMillis() - t0;

        assertThat(during).isEqualTo("준호");
        CiOutput.record(LESSON, "behind", "[복제본이 밀린 동안 원본에서 닉네임을 준호 → 준호2로 바꿈]\n"
                + "원본: 준호2\n밀린 복제본: " + during + "\n복제가 다시 돌고 " + caughtUp + "ms 뒤 복제본: 준호2");
    }

    // ── 샤딩 ──

    @Test
    @DisplayName("회원 id로 샤드 두 대에 나누면 한 회원의 주문은 한 샤드만 읽고, 최근 주문 전체는 두 샤드를 모두 읽어 합친다")
    void sharding() {
        JdbcTemplate[] shards = {primary, replica}; // 샤드 실험에서는 두 대를 따로 쓴다
        for (JdbcTemplate s : shards) {
            s.execute("create table if not exists orders_shard (id bigint primary key, member_id bigint, amount bigint, created_at bigint)");
            s.execute("truncate orders_shard");
        }
        for (long i = 1; i <= 40; i++) {
            long memberId = i % 10;
            shards[(int) (memberId % 2)].update("insert into orders_shard values (?, ?, ?, ?)", i, memberId, i * 1000, i);
        }

        int touchedForOneMember = 1; // memberId 3은 3 % 2 = 1번 샤드만
        List<Map<String, Object>> member3 = shards[1].queryForList("select id from orders_shard where member_id = 3");
        List<Long> recent = new ArrayList<>();
        for (JdbcTemplate s : shards) {
            recent.addAll(s.queryForList("select created_at from orders_shard order by created_at desc limit 3", Long.class));
        }
        recent.sort((a, b) -> Long.compare(b, a));
        List<Long> top3 = recent.subList(0, 3);
        long shard0 = shards[0].queryForObject("select count(*) from orders_shard", Long.class);
        long shard1 = shards[1].queryForObject("select count(*) from orders_shard", Long.class);

        assertThat(member3).hasSize(4);
        assertThat(top3).containsExactly(40L, 39L, 38L);
        CiOutput.record(LESSON, "sharding", "[주문 40건을 member_id % 2로 샤드 두 대에]\n"
                + "샤드 0: " + shard0 + "건, 샤드 1: " + shard1 + "건\n"
                + "회원 3의 주문: 샤드 " + touchedForOneMember + "개만 읽음 → " + member3.size() + "건\n"
                + "최근 주문 3건: 샤드 2개를 모두 읽어 합침 → " + top3);
    }

    @Test
    @DisplayName("샤드를 2대에서 3대로 늘릴 때, 나머지(mod)로 나누면 키의 대부분이 옮겨 가고, 해시 링은 3분의 1쯤만 옮겨 간다")
    void resharding() {
        int keys = 100_000;
        int movedMod = 0;
        for (long k = 0; k < keys; k++) {
            if (k % 2 != k % 3) {
                movedMod++;
            }
        }
        Ring two = new Ring(2);
        Ring three = new Ring(3);
        int movedRing = 0;
        for (long k = 0; k < keys; k++) {
            if (two.shardOf(k) != three.shardOf(k)) {
                movedRing++;
            }
        }
        assertThat(movedMod).isGreaterThan(keys / 2);
        assertThat(movedRing).isLessThan(keys / 2);
        CiOutput.record(LESSON, "resharding", "[키 10만 개, 샤드 2대 → 3대]\n"
                + "id % 샤드 수: " + movedMod * 100 / keys + "% 옮겨 감\n"
                + "해시 링(샤드마다 가상 노드 100개): " + movedRing * 100 / keys + "% 옮겨 감");
    }

    /** 해시 링: 샤드마다 가상 노드를 링 위에 두고, 키는 시계 방향으로 처음 만나는 노드의 샤드로 간다 */
    static class Ring {
        final SortedMap<Long, Integer> ring = new TreeMap<>();

        Ring(int shards) {
            for (int s = 0; s < shards; s++) {
                for (int v = 0; v < 100; v++) {
                    ring.put(hash("shard-" + s + "-" + v), s);
                }
            }
        }

        int shardOf(long key) {
            SortedMap<Long, Integer> tail = ring.tailMap(hash("key-" + key));
            return tail.isEmpty() ? ring.get(ring.firstKey()) : tail.get(tail.firstKey());
        }

        static long hash(String s) {
            CRC32 crc = new CRC32();
            crc.update(s.getBytes());
            return crc.getValue();
        }
    }
}
