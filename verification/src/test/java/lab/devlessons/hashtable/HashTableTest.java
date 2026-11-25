package lab.devlessons.hashtable;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lab.devlessons.support.ChildJvm;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "해시 테이블"(K4)의 주장을 검증한다.
 * HashMap의 안쪽 배열(table)과 노드는 java.base 모듈 안의 private 필드라서,
 * --add-opens를 준 자식 JVM(TableProbe)에서 리플렉션으로 읽는다.
 */
class HashTableTest {

    private static final String LESSON = "hash-table";
    private static final List<String> OPENS = List.of("--add-opens", "java.base/java.util=ALL-UNNAMED");

    static int spread(Object key) {
        int h = key.hashCode();
        return h ^ (h >>> 16);
    }

    /** hashCode가 늘 1인 키. Comparable이 아니다 */
    static final class BadKey {
        final int id;

        BadKey(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            HashTableTest.equalsCalls++;
            return o instanceof BadKey b && b.id == id;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    /** hashCode를 제대로 만든 키. equals를 센다 */
    static final class GoodKey {
        final int id;

        GoodKey(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            HashTableTest.equalsCalls++;
            return o instanceof GoodKey g && g.id == id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    static long equalsCalls;

    /** 자식 JVM에서 돈다. HashMap의 table을 리플렉션으로 읽어 출력한다 */
    public static final class TableProbe {

        static Object[] table(HashMap<?, ?> m) throws Exception {
            Field f = HashMap.class.getDeclaredField("table");
            f.setAccessible(true);
            return (Object[]) f.get(m);
        }

        static int length(HashMap<?, ?> m) throws Exception {
            Object[] t = table(m);
            return t == null ? 0 : t.length;
        }

        /** key가 든 칸의 번호. 칸의 노드를 next로 따라가며 찾는다 */
        static int bucketOf(HashMap<?, ?> m, Object key) throws Exception {
            Object[] t = table(m);
            for (int i = 0; i < t.length; i++) {
                for (Object n = t[i]; n != null; n = next(n)) {
                    if (key(n).equals(key)) {
                        return i;
                    }
                }
            }
            return -1;
        }

        static Object key(Object node) throws Exception {
            Field f = Class.forName("java.util.HashMap$Node").getDeclaredField("key");
            f.setAccessible(true);
            return f.get(node);
        }

        static Object next(Object node) throws Exception {
            Field f = Class.forName("java.util.HashMap$Node").getDeclaredField("next");
            f.setAccessible(true);
            return f.get(node);
        }

        /** 칸 하나에 든 키들을 이어진 순서대로 */
        static List<Object> chain(HashMap<?, ?> m, int index) throws Exception {
            List<Object> keys = new ArrayList<>();
            for (Object n = table(m)[index]; n != null; n = next(n)) {
                keys.add(key(n));
            }
            return keys;
        }

        public static void main(String[] args) throws Exception {
            // 1. 칸 번호: 계산한 값과 실제 칸
            HashMap<String, String> grade = new HashMap<>();
            for (String k : List.of("kim@a.com", "lee@a.com", "park@a.com")) {
                grade.put(k, "BASIC");
            }
            for (String k : List.of("kim@a.com", "lee@a.com", "park@a.com")) {
                System.out.println("index " + k + " " + bucketOf(grade, k));
            }

            // 2. 칸 8개짜리 배열에서 충돌 (위젯)
            HashMap<String, String> small = new HashMap<>(8);
            small.put("kim@a.com", "VIP");
            small.put("lee@a.com", "BASIC");
            small.put("choi@a.com", "BASIC");
            System.out.println("small length " + length(small)
                    + " kim " + bucketOf(small, "kim@a.com")
                    + " lee " + bucketOf(small, "lee@a.com")
                    + " choi " + bucketOf(small, "choi@a.com")
                    + " chain3 " + chain(small, 3));

            // 3. 배열이 늘어나는 순간
            HashMap<String, String> growing = new HashMap<>();
            System.out.println("grow 0 " + length(growing));
            int last = 0;
            for (int i = 1; i <= 25; i++) {
                growing.put("user" + i + "@a.com", "BASIC");
                int len = length(growing);
                if (len != last) {
                    System.out.println("grow " + i + " " + len);
                    last = len;
                }
            }

            // 4. 한 칸이 트리로 바뀌는 순간
            HashMap<BadKey, Integer> big = new HashMap<>(64);
            for (int i = 1; i <= 9; i++) {
                big.put(new BadKey(i), i);
                if (i >= 8) {
                    System.out.println("tree64 " + i + " " + table(big)[1].getClass().getName()
                            + " length " + length(big));
                }
            }
            HashMap<BadKey, Integer> sixteen = new HashMap<>();
            for (int i = 1; i <= 9; i++) {
                sixteen.put(new BadKey(i), i);
            }
            System.out.println("tree16 9 " + table(sixteen)[1].getClass().getName()
                    + " length " + length(sixteen));

            // 5. newHashMap과 new HashMap<>(n)
            System.out.println("default10000 " + resizes(new HashMap<>(), 10_000));
            System.out.println("newHashMap10000 " + resizes(HashMap.newHashMap(10_000), 10_000));
            System.out.println("ctor10000 " + resizes(new HashMap<>(10_000), 10_000));
            System.out.println("ctor10000with13000 " + resizes(new HashMap<>(10_000), 13_000));
        }

        static String resizes(HashMap<Integer, Integer> m, int n) throws Exception {
            int count = 0;
            int last = length(m);
            int first = -1;
            for (int i = 0; i < n; i++) {
                m.put(i, i);
                int len = length(m);
                if (first < 0) {
                    first = len;
                    last = len;
                } else if (len != last) {
                    count++;
                    last = len;
                }
            }
            return "first " + first + " resizes " + count + " final " + last;
        }
    }

    @Test
    @DisplayName("HashMap의 칸 번호는 (h ^ (h >>> 16)) & (length - 1)이고, 처음 16칸은 13번째에 32, 25번째에 64가 된다")
    void tableProbe() {
        ChildJvm.Run r = ChildJvm.run(OPENS, TableProbe.class);
        assertThat(r.exitCode()).as(r.stderr()).isZero();
        List<String> lines = r.stdout().lines().toList();

        // 1. 계산한 칸과 실제 칸
        StringBuilder index = new StringBuilder("배열 길이 16\n");
        for (String k : List.of("kim@a.com", "lee@a.com", "park@a.com")) {
            int expected = spread(k) & 15;
            assertThat(lines).contains("index " + k + " " + expected);
            index.append("%s\n  hashCode %d\n  칸 번호 %d (계산) = %d (실제)\n"
                    .formatted(k, k.hashCode(), expected, expected));
        }
        assertThat(spread("kim@a.com") & 15).isEqualTo(3);
        assertThat(spread("lee@a.com") & 15).isEqualTo(14);
        assertThat(spread("park@a.com") & 15).isEqualTo(13);
        CiOutput.record(LESSON, "index", index.toString().strip());

        // 2. 칸 8개짜리 배열의 충돌
        assertThat(lines).contains("small length 8 kim 3 lee 6 choi 3 chain3 [kim@a.com, choi@a.com]");
        CiOutput.record(LESSON, "collide", """
                배열 길이 8
                kim@a.com → 칸 3
                lee@a.com → 칸 6
                choi@a.com → 칸 3
                칸 3에 이어진 키: [kim@a.com, choi@a.com]""");

        // 3. 배열이 늘어나는 순간
        assertThat(lines).contains("grow 0 0", "grow 1 16", "grow 13 32", "grow 25 64");
        assertThat(lines.stream().filter(l -> l.startsWith("grow ")).count()).isEqualTo(4);
        CiOutput.record(LESSON, "predict", """
                넣기 전: 배열 없음 (길이 0)
                1번째 put 뒤: 길이 16
                13번째 put 뒤: 길이 32
                25번째 put 뒤: 길이 64""");

        // 4. 트리로 바뀌는 순간
        assertThat(lines).contains(
                "tree64 8 java.util.HashMap$Node length 64",
                "tree64 9 java.util.HashMap$TreeNode length 64");
        String tree16 = lines.stream().filter(l -> l.startsWith("tree16")).findFirst().orElseThrow();
        assertThat(tree16).contains("HashMap$Node");
        CiOutput.record(LESSON, "treeify", """
                hashCode가 같은 키를 한 칸에 넣기
                배열 64칸, 8개째: 칸의 노드 %s
                배열 64칸, 9개째: 칸의 노드 %s
                배열 16칸에서 9개째: %s, 배열 길이 %s"""
                .formatted("HashMap$Node", "HashMap$TreeNode",
                        tree16.contains("TreeNode") ? "HashMap$TreeNode" : "HashMap$Node",
                        tree16.substring(tree16.lastIndexOf(' ') + 1)));

        // 5. 처음 크기
        assertThat(lines).contains(
                "default10000 first 16 resizes 10 final 16384",
                "newHashMap10000 first 16384 resizes 0 final 16384",
                "ctor10000 first 16384 resizes 0 final 16384",
                "ctor10000with13000 first 16384 resizes 1 final 32768");
        CiOutput.record(LESSON, "presize", """
                키 10,000개를 넣는 동안
                new HashMap<>(): 처음 16, 늘어난 횟수 10, 마지막 16384
                HashMap.newHashMap(10000): 처음 16384, 늘어난 횟수 0
                new HashMap<>(10000)에 13,000개: 처음 16384, 늘어난 횟수 1, 마지막 32768""");
    }

    @Test
    @DisplayName("bit & (length - 1)은 아래 비트만 남긴다: 길이 16에서 ...0110_1011은 11번 칸")
    void maskKeepsLowBits() {
        int h = 0b0110_1011;
        assertThat(h & (16 - 1)).isEqualTo(11);
        assertThat(h & (16 - 1)).isEqualTo(h % 16);
        assertThat(0b1_0110_1011 & 15).isEqualTo(11);
    }

    @Test
    @DisplayName("hashCode가 늘 1인 키 1만 개를 넣은 맵에서 없는 키를 찾으면 equals가 수천 번 넘게 불린다")
    void badHashCodeMakesItLinear() {
        Map<BadKey, Integer> bad = new HashMap<>();
        Map<GoodKey, Integer> good = new HashMap<>();
        for (int i = 0; i < 10_000; i++) {
            bad.put(new BadKey(i), i);
            good.put(new GoodKey(i), i);
        }
        equalsCalls = 0;
        assertThat(bad.get(new BadKey(-1))).isNull();
        long badCalls = equalsCalls;

        equalsCalls = 0;
        assertThat(good.get(new GoodKey(-1))).isNull();
        long goodCalls = equalsCalls;

        assertThat(badCalls).isGreaterThan(5_000);
        assertThat(goodCalls).isLessThan(5);

        // 카테고리 3가지로 만든 hashCode: 9만 개가 칸 3개에 몰린다
        Map<Integer, Integer> perBucket = new HashMap<>();
        HashMap<String, Integer> probe = new HashMap<>();
        for (String category : List.of("FOOD", "BOOK", "TOY")) {
            perBucket.merge(spread(category) & (131072 - 1), 30_000, Integer::sum);
        }
        assertThat(perBucket).hasSize(3);
        assertThat(probe).isEmpty();

        CiOutput.record(LESSON, "badkey", """
                키 10,000개, 없는 키를 get
                hashCode가 늘 1인 키: equals %,d번
                hashCode를 제대로 만든 키: equals %,d번""".formatted(badCalls, goodCalls));
    }

    @Test
    @DisplayName("HashMap은 칸 순서로 돌아서 넣은 순서와 다르고, 배열이 커지면 순서가 바뀐다. LinkedHashMap은 넣은 순서다")
    void iterationOrder() {
        Map<String, Integer> m = new HashMap<>();
        m.put("banana", 1);
        m.put("apple", 2);
        m.put("orange", 3);
        String before = m.keySet().toString();

        for (int i = 0; i < 40; i++) {
            m.put("k" + i, i);
        }
        List<String> after = m.keySet().stream()
                .filter(k -> List.of("banana", "apple", "orange").contains(k)).toList();

        Map<String, Integer> linked = new LinkedHashMap<>();
        linked.put("banana", 1);
        linked.put("apple", 2);
        linked.put("orange", 3);

        assertThat(before).isEqualTo("[banana, orange, apple]");
        assertThat(after).containsExactly("apple", "banana", "orange");
        assertThat(linked.keySet()).containsExactly("banana", "apple", "orange");

        CiOutput.record(LESSON, "order", """
                HashMap (배열 16칸): %s
                키 40개를 더 넣은 뒤 (64칸): %s
                LinkedHashMap: %s""".formatted(before, after, linked.keySet()));
    }

    record Summary(int total, int paid, int canceled) {
    }

    @Test
    @DisplayName("잭슨은 맵을 도는 순서대로 쓴다. LinkedHashMap은 넣은 순서, record는 선언한 순서다")
    void jsonKeyOrder() throws Exception {
        ObjectMapper om = new ObjectMapper();
        Map<String, Object> hash = new HashMap<>();
        hash.put("total", 3);
        hash.put("paid", 2);
        hash.put("canceled", 1);
        Map<String, Object> linked = new LinkedHashMap<>();
        linked.put("total", 3);
        linked.put("paid", 2);
        linked.put("canceled", 1);

        String hashJson = om.writeValueAsString(hash);
        String linkedJson = om.writeValueAsString(linked);
        String recordJson = om.writeValueAsString(new Summary(3, 2, 1));

        assertThat(hashJson).isEqualTo(om.writeValueAsString(new ArrayList<>(hash.entrySet()).stream()
                .collect(LinkedHashMap::new, (acc, e) -> acc.put(e.getKey(), e.getValue()), Map::putAll)));
        assertThat(linkedJson).isEqualTo("{\"total\":3,\"paid\":2,\"canceled\":1}");
        assertThat(recordJson).isEqualTo("{\"total\":3,\"paid\":2,\"canceled\":1}");
        CiOutput.record(LESSON, "json", """
                HashMap:       %s
                LinkedHashMap: %s
                record:        %s""".formatted(hashJson, linkedJson, recordJson));
    }
}
