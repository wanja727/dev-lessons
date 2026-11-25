package lab.devlessons.choosingcollections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "컬렉션 고르기"(K10)의 주장을 검증한다. 순수 JUnit 테스트.
 */
class ChoosingCollectionsTest {

    private static final String LESSON = "choosing-collections";

    /** equals 호출을 세는 쿠폰 */
    static final class Coupon {
        static final AtomicLong EQUALS = new AtomicLong();
        final String code;

        Coupon(String code) {
            this.code = code;
        }

        @Override
        public boolean equals(Object o) {
            EQUALS.incrementAndGet();
            return o instanceof Coupon c && c.code.equals(code);
        }

        @Override
        public int hashCode() {
            return code.hashCode();
        }
    }

    @Test
    @DisplayName("쿠폰 50만 개에서 없는 쿠폰을 확인하면 ArrayList는 equals 50만 번, HashSet은 몇 번")
    void containsCost() {
        List<Coupon> list = new ArrayList<>();
        for (int i = 0; i < 500_000; i++) list.add(new Coupon("CP" + i));
        Set<Coupon> set = new HashSet<>(list);
        Coupon missing = new Coupon("NONE-1");

        Coupon.EQUALS.set(0);
        boolean inList = list.contains(missing);
        long listEquals = Coupon.EQUALS.get();
        Coupon.EQUALS.set(0);
        boolean inSet = set.contains(missing);
        long setEquals = Coupon.EQUALS.get();

        assertThat(inList).isFalse();
        assertThat(inSet).isFalse();
        assertThat(listEquals).isEqualTo(500_000);
        assertThat(setEquals).isLessThan(10);

        // 1만 번 확인하는 시간 (List는 100번으로 재고 백 배)
        List<String> listCodes = new ArrayList<>();
        for (int i = 0; i < 500_000; i++) listCodes.add("CP" + i);
        Set<String> setCodes = new HashSet<>(listCodes);
        for (int i = 0; i < 20; i++) listCodes.contains("X");
        long t0 = System.nanoTime();
        int hits = 0;
        for (int i = 0; i < 100; i++) if (listCodes.contains("X" + i)) hits++;
        long listMs = (System.nanoTime() - t0) / 1_000_000 * 100;
        long t1 = System.nanoTime();
        for (int i = 0; i < 10_000; i++) if (setCodes.contains("X" + i)) hits++;
        long setMs = (System.nanoTime() - t1) / 1_000_000;
        assertThat(hits).isZero();

        CiOutput.record(LESSON, "predict", """
                없는 쿠폰 하나를 확인할 때의 equals 호출
                ArrayList.contains: %d번
                HashSet.contains:   %d번

                없는 쿠폰 1만 번 확인 (ArrayList는 100번을 재서 백 배)
                ArrayList: 약 %d ms
                HashSet:   %d ms""".formatted(listEquals, setEquals, listMs, setMs));
    }

    @Test
    @DisplayName("Set.add는 이미 있으면 false를 돌려준다")
    void setAddReturnsFalse() {
        Set<Long> visitors = new HashSet<>();
        assertThat(visitors.add(7L)).isTrue();
        assertThat(visitors.add(7L)).isFalse();
        assertThat(visitors.add(9L)).isTrue();
        assertThat(visitors).hasSize(2);
    }

    @Test
    @DisplayName("같은 키를 같은 순서로 넣어도 HashMap, LinkedHashMap, TreeMap의 순서가 다르다")
    void threeMapsOrder() {
        String[] keys = {"banana", "apple", "cherry", "kiwi", "date"};
        Map<String, Integer> hash = new HashMap<>();
        Map<String, Integer> linked = new LinkedHashMap<>();
        Map<String, Integer> tree = new TreeMap<>();
        for (String k : keys) {
            hash.put(k, k.length());
            linked.put(k, k.length());
            tree.put(k, k.length());
        }
        assertThat(linked.keySet()).containsExactly(keys);
        assertThat(tree.keySet()).containsExactly("apple", "banana", "cherry", "date", "kiwi");
        assertThat(List.copyOf(hash.keySet())).isNotEqualTo(List.of(keys));

        CiOutput.record(LESSON, "order", """
                넣은 순서:     [banana, apple, cherry, kiwi, date]
                HashMap:       %s
                LinkedHashMap: %s
                TreeMap:       %s""".formatted(hash.keySet(), linked.keySet(), tree.keySet()));
    }

    @Test
    @DisplayName("키 100만 개를 넣고 찾을 때 TreeMap이 HashMap보다 오래 걸린다")
    void mapTiming() {
        int n = 1_000_000;
        Integer[] keys = new Integer[n];
        java.util.Random r = new java.util.Random(5);
        for (int i = 0; i < n; i++) keys[i] = r.nextInt();
        long[] hashMs = new long[1];
        long[] linkedMs = new long[1];
        long[] treeMs = new long[1];
        for (int round = 0; round < 3; round++) {
            hashMs[0] = time(new HashMap<>(), keys);
            linkedMs[0] = time(new LinkedHashMap<>(), keys);
            treeMs[0] = time(new TreeMap<>(), keys);
        }
        assertThat(treeMs[0]).isGreaterThan(hashMs[0]);
        CiOutput.record(LESSON, "timing", """
                무작위 키 100만 개를 넣고 모두 한 번씩 찾기
                HashMap:       %d ms
                LinkedHashMap: %d ms
                TreeMap:       %d ms""".formatted(hashMs[0], linkedMs[0], treeMs[0]));
    }

    static long time(Map<Integer, Integer> map, Integer[] keys) {
        long t = System.nanoTime();
        for (Integer k : keys) map.put(k, 1);
        long sum = 0;
        for (Integer k : keys) sum += map.get(k);
        assertThat(sum).isEqualTo(keys.length);
        return (System.nanoTime() - t) / 1_000_000;
    }

    enum Status { ORDERED, PAID, SHIPPED }

    record Order(long id, Status status, int amount) {
    }

    static final List<Order> ORDERS = List.of(
            new Order(1, Status.SHIPPED, 12_000),
            new Order(2, Status.ORDERED, 35_000),
            new Order(3, Status.PAID, 8_000),
            new Order(4, Status.SHIPPED, 27_000),
            new Order(5, Status.ORDERED, 54_000),
            new Order(6, Status.SHIPPED, 3_000));

    @Test
    @DisplayName("EnumMap은 선언 순서로 돌고, HashMap으로 센 결과와 값이 같다")
    void enumMap() {
        Map<Status, Integer> count = new EnumMap<>(Status.class);
        Map<Status, Integer> hash = new HashMap<>();
        for (Order o : ORDERS) {
            count.merge(o.status(), 1, Integer::sum);
            hash.merge(o.status(), 1, Integer::sum);
        }
        assertThat(count.keySet()).containsExactly(Status.ORDERED, Status.PAID, Status.SHIPPED);
        assertThat(count).isEqualTo(hash);

        Map<java.time.DayOfWeek, String> hours = new EnumMap<>(java.time.DayOfWeek.class);
        hours.put(java.time.DayOfWeek.FRIDAY, "10-22");
        hours.put(java.time.DayOfWeek.MONDAY, "10-20");
        assertThat(hours.keySet()).containsExactly(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.FRIDAY);

        CiOutput.record(LESSON, "enummap", "EnumMap: " + count + "\nHashMap: " + hash);
    }

    static List<String> cart() {
        return new ArrayList<>(List.of("A", "B", "C", "D"));
    }

    /** 레슨의 코드 removeLoop. 본 원소를 seen에 모은다 */
    static List<String> removeLoop(List<String> cart, String target, List<String> seen) {
        for (String item : cart) {
            seen.add(item);
            if (item.equals(target)) {
                cart.remove(item);
            }
        }
        return cart;
    }

    @Test
    @DisplayName("돌면서 B나 A를 지우면 ConcurrentModificationException, C를 지우면 예외 없이 D를 보지 않는다")
    void removeWhileIterating() {
        assertThatThrownBy(() -> removeLoop(cart(), "B", new ArrayList<>()))
                .isInstanceOf(ConcurrentModificationException.class);
        assertThatThrownBy(() -> removeLoop(cart(), "A", new ArrayList<>()))
                .isInstanceOf(ConcurrentModificationException.class);
        List<String> seen = new ArrayList<>();
        List<String> left = removeLoop(cart(), "C", seen);
        assertThat(seen).containsExactly("A", "B", "C");
        assertThat(left).containsExactly("A", "B", "D");

        String message;
        try {
            removeLoop(cart(), "B", new ArrayList<>());
            message = "예외 없음";
        } catch (ConcurrentModificationException e) {
            message = e.getClass().getName();
        }
        CiOutput.record(LESSON, "cme", """
                "B"를 지우면 → %s
                "C"를 지우면 → 예외 없음. 본 원소 %s, 남은 리스트 %s""".formatted(message, seen, left));
    }

    @Test
    @DisplayName("removeIf, Iterator.remove, filter는 모두 [A, C, D]를 남긴다")
    void removeRight() {
        List<String> a = cart();
        a.removeIf(item -> item.equals("B"));

        List<String> b = cart();
        Iterator<String> it = b.iterator();
        while (it.hasNext()) {
            if (it.next().equals("B")) it.remove();
        }

        List<String> c = cart().stream().filter(item -> !item.equals("B")).toList();

        assertThat(a).containsExactly("A", "C", "D");
        assertThat(b).containsExactly("A", "C", "D");
        assertThat(c).containsExactly("A", "C", "D");

        Map<String, Integer> stock = new HashMap<>(Map.of("p1", 0, "p2", 3, "p3", 0));
        assertThatThrownBy(() -> {
            for (String k : stock.keySet()) if (stock.get(k) == 0) stock.remove(k);
        }).isInstanceOf(ConcurrentModificationException.class);
        Map<String, Integer> stock2 = new HashMap<>(Map.of("p1", 0, "p2", 3, "p3", 0));
        stock2.values().removeIf(v -> v == 0);
        assertThat(stock2).containsOnlyKeys("p2");

        CiOutput.record(LESSON, "removeRight", """
                removeIf        → %s
                Iterator.remove → %s
                stream filter   → %s""".formatted(a, b, c));
    }

    @Test
    @DisplayName("groupingBy의 기본 맵은 HashMap이고, TreeMap::new를 넘기면 키 순서로 돈다")
    void groupingBy() {
        List<Order> orders = new ArrayList<>();
        int[] amounts = {54_000, 12_000, 170_000, 8_000, 35_000, 27_000, 99_000, 160_000, 3_000, 140_000};
        for (int i = 0; i < amounts.length; i++) orders.add(new Order(i, Status.PAID, amounts[i]));

        Map<Integer, List<Order>> byBand = orders.stream().collect(
                Collectors.groupingBy(o -> o.amount() / 10_000));
        Map<Integer, List<Order>> sorted = orders.stream().collect(
                Collectors.groupingBy(o -> o.amount() / 10_000, TreeMap::new, Collectors.toList()));

        assertThat(byBand).isInstanceOf(HashMap.class);
        assertThat(sorted.keySet()).containsExactly(0, 1, 2, 3, 5, 9, 14, 16, 17);
        assertThat(List.copyOf(byBand.keySet())).isNotEqualTo(List.copyOf(sorted.keySet()));

        CiOutput.record(LESSON, "grouping", """
                groupingBy(구간)            → %s, 키 %s
                groupingBy(구간, TreeMap::new, ...) → 키 %s""".formatted(
                byBand.getClass().getSimpleName(), byBand.keySet(), sorted.keySet()));
    }

    @Test
    @DisplayName("접근 순서 LinkedHashMap에 removeEldestEntry를 두면 가장 오래 안 쓴 키가 지워진다")
    void lruCache() {
        Map<String, String> cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > 3;
            }
        };
        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");
        cache.get("a");
        cache.put("d", "4");
        assertThat(cache.keySet()).containsExactly("c", "a", "d");
    }
}
