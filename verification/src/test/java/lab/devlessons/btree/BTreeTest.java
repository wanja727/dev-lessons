package lab.devlessons.btree;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 레슨 "B-트리"(K13)의 주장을 검증한다.
 * 자바로 만든 작은 균형 이진 탐색 트리와 B+트리에서 거치는 노드 수를 세고,
 * PostgreSQL 16의 실제 인덱스는 pageinspect와 EXPLAIN (ANALYZE, BUFFERS)로 본다.
 * 스프링 컨텍스트 없이 컨테이너만 띄운다.
 */
class BTreeTest {

    private static final String LESSON = "b-tree";

    // ── 균형 이진 탐색 트리 ──

    static final class BstNode {
        final long key;
        BstNode left;
        BstNode right;

        BstNode(long key) {
            this.key = key;
        }
    }

    /** 정렬된 키로 균형 이진 탐색 트리를 만든다 */
    static BstNode balanced(long[] keys, int lo, int hi) {
        if (lo > hi) {
            return null;
        }
        int mid = (lo + hi) >>> 1;
        BstNode n = new BstNode(keys[mid]);
        n.left = balanced(keys, lo, mid - 1);
        n.right = balanced(keys, mid + 1, hi);
        return n;
    }

    static int bstVisits(BstNode root, long key) {
        int visits = 0;
        BstNode n = root;
        while (n != null) {
            visits++;
            if (n.key == key) {
                return visits;
            }
            n = key < n.key ? n.left : n.right;
        }
        return visits;
    }

    static int bstHeight(BstNode n) {
        return n == null ? 0 : 1 + Math.max(bstHeight(n.left), bstHeight(n.right));
    }

    // ── B+트리: 값은 잎에만, 잎끼리 next로 잇는다 ──

    static final class BPlusTree {
        final int maxKeys;
        Node root = new Node(true);
        int splits;

        BPlusTree(int maxKeys) {
            this.maxKeys = maxKeys;
        }

        static final class Node {
            final boolean leaf;
            final List<Long> keys = new ArrayList<>();
            final List<Node> children = new ArrayList<>();
            Node next;

            Node(boolean leaf) {
                this.leaf = leaf;
            }
        }

        /** 키보다 큰 첫 자리. 안쪽 노드에서 들어갈 자식의 번호다 */
        static int upperBound(List<Long> keys, long key) {
            int lo = 0;
            int hi = keys.size();
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (keys.get(mid) <= key) {
                    lo = mid + 1;
                } else {
                    hi = mid;
                }
            }
            return lo;
        }

        void insert(long key) {
            Object[] split = insert(root, key);
            if (split != null) {
                Node newRoot = new Node(false);
                newRoot.keys.add((Long) split[0]);
                newRoot.children.add(root);
                newRoot.children.add((Node) split[1]);
                root = newRoot;
            }
        }

        /** 나뉘면 {부모로 올릴 키, 오른쪽 노드}를 돌려준다 */
        private Object[] insert(Node n, long key) {
            if (n.leaf) {
                int pos = upperBound(n.keys, key);
                n.keys.add(pos, key);
            } else {
                int i = upperBound(n.keys, key);
                Object[] split = insert(n.children.get(i), key);
                if (split != null) {
                    n.keys.add(i, (Long) split[0]);
                    n.children.add(i + 1, (Node) split[1]);
                }
            }
            if (n.keys.size() <= maxKeys) {
                return null;
            }
            splits++;
            int mid = n.keys.size() / 2;
            Node right = new Node(n.leaf);
            long up;
            if (n.leaf) {
                right.keys.addAll(n.keys.subList(mid, n.keys.size()));
                n.keys.subList(mid, n.keys.size()).clear();
                right.next = n.next;
                n.next = right;
                up = right.keys.get(0);
            } else {
                up = n.keys.get(mid);
                right.keys.addAll(n.keys.subList(mid + 1, n.keys.size()));
                right.children.addAll(n.children.subList(mid + 1, n.children.size()));
                n.keys.subList(mid, n.keys.size()).clear();
                n.children.subList(mid + 1, n.children.size()).clear();
            }
            return new Object[] {up, right};
        }

        int height() {
            int h = 1;
            for (Node n = root; !n.leaf; n = n.children.get(0)) {
                h++;
            }
            return h;
        }

        /** 찾을 때 거친 노드 수. 못 찾으면 음수 */
        int findVisits(long key) {
            int visits = 1;
            Node n = root;
            while (!n.leaf) {
                n = n.children.get(upperBound(n.keys, key));
                visits++;
            }
            return n.keys.contains(key) ? visits : -visits;
        }

        /** [from, to] 범위를 읽을 때 거친 노드 수와 찾은 키의 수 */
        int[] rangeVisits(long from, long to) {
            int visits = 1;
            Node n = root;
            while (!n.leaf) {
                n = n.children.get(upperBound(n.keys, from - 1));
                visits++;
            }
            int found = 0;
            while (n != null) {
                boolean past = false;
                for (long k : n.keys) {
                    if (k > to) {
                        past = true;
                        break;
                    }
                    if (k >= from) {
                        found++;
                    }
                }
                if (past || n.next == null) {
                    break;
                }
                n = n.next;
                visits++;
            }
            return new int[] {visits, found};
        }

        /** 겹마다 노드의 키를 한 줄로 */
        String draw() {
            StringBuilder sb = new StringBuilder();
            List<Node> level = List.of(root);
            while (!level.isEmpty()) {
                List<Node> next = new ArrayList<>();
                List<String> parts = new ArrayList<>();
                for (Node n : level) {
                    parts.add(n.keys.toString().replace(",", ""));
                    next.addAll(n.children);
                }
                sb.append(String.join(" ", parts)).append('\n');
                level = next;
            }
            return sb.toString().strip();
        }
    }

    @Test
    @DisplayName("키 100만 개: 균형 이진 탐색 트리는 높이 20, B+트리(노드당 키 255개)는 높이 3이고 찾을 때 거치는 노드도 그만큼이다")
    void heightAndVisits() {
        int n = 1_000_000;
        long[] keys = new long[n];
        for (int i = 0; i < n; i++) {
            keys[i] = i + 1;
        }
        BstNode bst = balanced(keys, 0, n - 1);
        BPlusTree bp = new BPlusTree(255);
        for (long k : keys) {
            bp.insert(k);
        }

        int bstMax = 0;
        int bpMax = 0;
        for (long k = 1; k <= n; k += 997) {
            bstMax = Math.max(bstMax, bstVisits(bst, k));
            assertThat(bp.findVisits(k)).isPositive();
            bpMax = Math.max(bpMax, bp.findVisits(k));
        }
        assertThat(bstHeight(bst)).isEqualTo(20);
        assertThat(bstMax).isLessThanOrEqualTo(20);
        assertThat(bp.height()).isEqualTo(3);
        assertThat(bpMax).isEqualTo(3);
        assertThat(bstVisits(bst, 765_432)).isLessThanOrEqualTo(20);

        CiOutput.record(LESSON, "visits", """
                키 1,000,000개
                균형 이진 탐색 트리: 높이 %d, 찾을 때 거친 노드 최대 %d개
                B+트리(노드당 키 255개): 높이 %d, 찾을 때 거친 노드 %d개"""
                .formatted(bstHeight(bst), bstMax, bp.height(), bpMax));
    }

    @Test
    @DisplayName("노드당 키 3개인 B+트리에 1부터 넣으면 4에서 첫 분할로 높이 2, 6에서 뿌리에 키가 하나 더 는다. 정렬된 순서로 100만 개를 넣어도 낮다")
    void splitsKeepBalance() {
        BPlusTree t = new BPlusTree(3);
        List<String> steps = new ArrayList<>();
        for (long k = 1; k <= 7; k++) {
            int before = t.splits;
            t.insert(k);
            steps.add("insert %d (분할 %d번, 높이 %d)\n%s".formatted(k, t.splits - before, t.height(), t.draw()));
        }
        assertThat(steps.get(2)).endsWith("[1 2 3]");
        assertThat(steps.get(3)).contains("분할 1번, 높이 2").endsWith("[3]\n[1 2] [3 4]");
        assertThat(steps.get(5)).contains("분할 1번, 높이 2").endsWith("[3 5]\n[1 2] [3 4] [5 6]");
        assertThat(steps.get(6)).endsWith("[3 5]\n[1 2] [3 4] [5 6 7]");
        CiOutput.record(LESSON, "splits", String.join("\n\n", steps));

        // 한 줄로 길어지는 이진 탐색 트리와 달리, 정렬된 순서로 넣어도 높이가 log만큼만 자란다
        BPlusTree sorted = new BPlusTree(3);
        for (long k = 1; k <= 1_000_000; k++) {
            sorted.insert(k);
        }
        assertThat(sorted.height()).isBetween(13, 20);
        BPlusTree wide = new BPlusTree(255);
        for (long k = 1; k <= 1_000_000; k++) {
            wide.insert(k);
        }
        CiOutput.record(LESSON, "sorted", """
                1부터 1,000,000까지 차례로 넣기
                노드당 키 3개: 높이 %d
                노드당 키 255개: 높이 %d
                균형을 맞추지 않는 이진 탐색 트리: 높이 1,000,000"""
                .formatted(sorted.height(), wide.height()));
    }

    @Test
    @DisplayName("B+트리의 범위 조회는 뿌리에서 잎까지 겹 수와 옆 잎 몇 개만 거친다")
    void rangeScan() {
        BPlusTree bp = new BPlusTree(255);
        for (long k = 1; k <= 1_000_000; k++) {
            bp.insert(k);
        }
        int[] r = bp.rangeVisits(500_000, 500_099);
        assertThat(r[1]).isEqualTo(100);
        assertThat(r[0]).isBetween(3, 4);

        // 같은 범위를 균형 이진 탐색 트리의 중위 순회로 찾으면 지나는 노드가 훨씬 많다
        long[] keys = new long[1_000_000];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = i + 1;
        }
        BstNode bst = balanced(keys, 0, keys.length - 1);
        int[] counter = {0};
        bstRange(bst, 500_000, 500_099, counter);
        assertThat(counter[0]).isGreaterThan(100);

        CiOutput.record(LESSON, "range", """
                between 500000 and 500099 (100개)
                B+트리: 거친 노드 %d개 (뿌리에서 잎까지 %d, 옆 잎 %d)
                균형 이진 탐색 트리: 지난 노드 %d개"""
                .formatted(r[0], bp.height(), r[0] - bp.height(), counter[0]));
    }

    static void bstRange(BstNode n, long from, long to, int[] counter) {
        if (n == null) {
            return;
        }
        counter[0]++;
        if (from < n.key) {
            bstRange(n.left, from, to, counter);
        }
        if (to > n.key) {
            bstRange(n.right, from, to, counter);
        }
    }

    // ── PostgreSQL ──

    static PostgreSQLContainer<?> pg;
    static Connection conn;

    @BeforeAll
    static void startPostgres() throws SQLException {
        pg = new PostgreSQLContainer<>("postgres:16-alpine");
        pg.start();
        conn = pg.createConnection("");
        exec("create extension if not exists pageinspect",
                "create table bt_orders (id bigint primary key, amount int not null)",
                "insert into bt_orders select g, (g % 100) * 1000 from generate_series(1, 1000000) g",
                "vacuum analyze bt_orders",
                "create table bt_events (id bigint primary key, url text not null, url_hash bigint not null)",
                "insert into bt_events select g, u, ('x' || substr(md5(u), 1, 16))::bit(64)::bigint from ("
                        + "select g, 'https://shop.example.com/products/' || md5(g::text) || '/' || md5((g + 1)::text)"
                        + " || '/' || md5((g + 2)::text) || '/' || md5((g + 3)::text) || '?ref=' || g as u"
                        + " from generate_series(1, 300000) g) s",
                "create index bt_events_url on bt_events (url)",
                "create index bt_events_url_hash on bt_events (url_hash)",
                "vacuum analyze bt_events");
    }

    @AfterAll
    static void stopPostgres() throws SQLException {
        if (conn != null) {
            conn.close();
        }
        if (pg != null) {
            pg.stop();
        }
    }

    static void exec(String... sqls) throws SQLException {
        try (Statement st = conn.createStatement()) {
            for (String sql : sqls) {
                st.execute(sql);
            }
        }
    }

    static List<String> rows(String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            int cols = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                String[] vals = new String[cols];
                for (int i = 0; i < cols; i++) {
                    vals[i] = rs.getString(i + 1);
                }
                out.add(String.join(" | ", vals));
            }
        }
        return out;
    }

    static int indexBuffers(String plan) {
        for (String line : plan.lines().toList()) {
            if (line.contains("Buffers:")) {
                int total = 0;
                for (String part : line.replaceAll(".*Buffers: shared ", "").split(" ")) {
                    if (part.contains("=")) {
                        total += Integer.parseInt(part.substring(part.indexOf('=') + 1));
                    }
                }
                return total;
            }
        }
        throw new IllegalStateException("Buffers가 없다:\n" + plan);
    }

    @Test
    @DisplayName("PostgreSQL 16: 100만 행 기본 키 인덱스의 level은 2(세 겹)이고, 한 건 조회는 인덱스 세 겹과 한 페이지 더, 4페이지를 읽는다")
    void postgresHeightAndPointLookup() throws SQLException {
        String blockSize = rows("show block_size").get(0);
        String meta = rows("select level, root from bt_metap('bt_orders_pkey')").get(0);
        String plan = String.join("\n", rows("explain (analyze, buffers, costs off, timing off, summary off) "
                + "select id from bt_orders where id = 765432"));

        CiOutput.record(LESSON, "predict", plan);
        assertThat(blockSize).isEqualTo("8192");
        assertThat(meta).startsWith("2 |");
        assertThat(plan).contains("Index Only Scan using bt_orders_pkey").contains("Heap Fetches: 0");
        // 인덱스 세 겹에, 테이블을 볼 필요가 없는지 알려 주는 가시성 맵 한 페이지를 더 읽는다
        assertThat(indexBuffers(plan)).isEqualTo(4);
        String heapPlan = String.join("\n", rows("explain (analyze, buffers, costs off, timing off, summary off) "
                + "select * from bt_orders where id = 765432"));
        // select *는 가시성 맵 대신 테이블 페이지 하나를 읽는다
        assertThat(heapPlan).contains("Index Scan using bt_orders_pkey");
        assertThat(indexBuffers(heapPlan)).isEqualTo(4);
        CiOutput.record(LESSON, "pg-heap", heapPlan);

        CiOutput.record(LESSON, "pg-meta", "block_size: " + blockSize
                + "\nbt_metap('bt_orders_pkey'): level | root\n  " + meta);
        CiOutput.record(LESSON, "predict", plan);
    }

    @Test
    @DisplayName("PostgreSQL 16: 이어진 100행의 범위 조회는 한 자릿수 페이지를 읽고, 흩어진 100행을 하나씩 찾으면 열 배 넘게 읽는다")
    void postgresRange() throws SQLException {
        String plan = String.join("\n", rows("explain (analyze, buffers, costs off, timing off, summary off) "
                + "select id from bt_orders where id between 500000 and 500099"));
        CiOutput.record(LESSON, "pg-range", plan);
        assertThat(plan).contains("Index Only Scan using bt_orders_pkey").contains("rows=100");
        int range = indexBuffers(plan);
        assertThat(range).isBetween(4, 9);

        // 같은 100행을 흩어진 id로 하나씩 찾으면 행마다 뿌리에서 잎까지 내려간다
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            ids.append(i == 0 ? "" : ",").append(5_000 + i * 10_000);
        }
        String spread = String.join("\n", rows("explain (analyze, buffers, costs off, timing off, summary off) "
                + "select id from bt_orders where id in (" + ids + ")"));
        int spreadBuffers = indexBuffers(spread);
        assertThat(spread).contains("rows=100");
        assertThat(spreadBuffers).isGreaterThan(range * 10);
        CiOutput.record(LESSON, "pg-range", plan);
        CiOutput.record(LESSON, "pg-spread", """
                흩어진 id 100개 (5000, 15000, ..., 995000)
                %s

                이어진 100행: 페이지 %d개
                흩어진 100행: 페이지 %d개""".formatted(
                spread.replaceAll("\\{[0-9,]+\\}", "{5000,15000,...,995000}"), range, spreadBuffers));
    }

    @Test
    @DisplayName("PostgreSQL 16: 같은 30만 행에서 긴 문자열 키 인덱스는 bigint 키 인덱스보다 몇 배 크고, 해시 값 열의 인덱스는 bigint만큼 작다")
    void longKeyIndex() throws SQLException {
        List<String> sizes = rows("select c.relname, pg_relation_size(c.oid), "
                + "pg_size_pretty(pg_relation_size(c.oid)), (select level from bt_metap(c.relname)) "
                + "from pg_class c where c.relname in ('bt_events_pkey', 'bt_events_url', 'bt_events_url_hash') "
                + "order by pg_relation_size(c.oid)");
        String avgLen = rows("select round(avg(length(url))) from bt_events").get(0);
        long pkey = sizeOf(sizes, "bt_events_pkey");
        long url = sizeOf(sizes, "bt_events_url");
        long hash = sizeOf(sizes, "bt_events_url_hash");
        int pkeyLevel = levelOf(sizes, "bt_events_pkey");
        int urlLevel = levelOf(sizes, "bt_events_url");

        assertThat(url).isGreaterThan(pkey * 3);
        assertThat(hash).isLessThan(pkey * 2);
        assertThat(urlLevel).isGreaterThanOrEqualTo(pkeyLevel);

        StringBuilder out = new StringBuilder("bt_events 300,000행, url 평균 " + avgLen + "자\n");
        for (String row : sizes) {
            String[] p = row.split(" \\| ");
            out.append("%s: %s, level %s%n".formatted(p[0], p[2], p[3]).replace("\r", ""));
        }
        CiOutput.record(LESSON, "long-key", out.toString().strip());
    }

    static long sizeOf(List<String> rows, String name) {
        return rows.stream().filter(r -> r.startsWith(name + " |")).map(r -> Long.parseLong(r.split(" \\| ")[1]))
                .findFirst().orElseThrow();
    }

    static int levelOf(List<String> rows, String name) {
        return rows.stream().filter(r -> r.startsWith(name + " |")).map(r -> Integer.parseInt(r.split(" \\| ")[3]))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("노드 안에서는 정렬된 키를 이진 탐색으로 찾는다")
    void upperBoundInNode() {
        List<Long> keys = Arrays.asList(10L, 20L, 30L);
        assertThat(BPlusTree.upperBound(keys, 5)).isEqualTo(0);
        assertThat(BPlusTree.upperBound(keys, 20)).isEqualTo(2);
        assertThat(BPlusTree.upperBound(keys, 35)).isEqualTo(3);
    }
}
