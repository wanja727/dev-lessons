package lab.devlessons.binarysearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "이진 탐색"(K7)의 주장을 검증한다. 순수 JUnit 테스트.
 */
class BinarySearchTest {

    private static final String LESSON = "binary-search";

    /** 레슨의 코드 search. 반복 횟수를 세려고 counter를 받는다. */
    static int find(int[] a, int key, AtomicInteger counter) {
        int low = 0;
        int high = a.length - 1;
        while (low <= high) {
            counter.incrementAndGet();
            int mid = (low + high) >>> 1;
            if (a[mid] < key) low = mid + 1;
            else if (a[mid] > key) high = mid - 1;
            else return mid;
        }
        return -1;
    }

    /** 레슨의 코드 buggy. 닫힌 범위로 시작했지만 low < high로 돈다. */
    static int buggy(int[] a, int key) {
        int low = 0;
        int high = a.length - 1;
        while (low < high) {
            int mid = (low + high) / 2;
            if (a[mid] < key) low = mid + 1;
            else if (a[mid] > key) high = mid - 1;
            else return mid;
        }
        return -1;
    }

    static int find(int[] a, int key) {
        return find(a, key, new AtomicInteger());
    }

    @Test
    @DisplayName("짝수 100만 개에서 없는 값을 찾으면 반복은 20번 안팎, 처음부터 보면 100만 번")
    void comparisons() {
        int[] ids = new int[1_000_000];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = i * 2;
        }
        AtomicInteger absent = new AtomicInteger();
        int r = find(ids, 1_234_567, absent);
        AtomicInteger present = new AtomicInteger();
        int found = find(ids, 1_234_568, present);

        int linear = 0;
        for (int id : ids) {
            linear++;
            if (id == 1_234_567) break;
        }

        assertThat(r).isEqualTo(-1);
        assertThat(absent.get()).isBetween(19, 20);
        assertThat(found).isEqualTo(617_284);
        assertThat(linear).isEqualTo(1_000_000);

        int[] big = new int[4_000_000];
        for (int i = 0; i < big.length; i++) big[i] = i * 2;
        AtomicInteger four = new AtomicInteger();
        find(big, 1, four);
        int[] thousand = new int[1000];
        for (int i = 0; i < thousand.length; i++) thousand[i] = i * 2;
        AtomicInteger th = new AtomicInteger();
        find(thousand, 1, th);
        assertThat(four.get()).isBetween(21, 22);
        assertThat(th.get()).isBetween(9, 10);

        CiOutput.record(LESSON, "predict", """
                찾은 값: 1_234_567 (배열에 없음)
                이진 탐색의 반복: %d번
                처음부터 하나씩 본 비교: %d번

                원소 1천 개에서 없는 값: %d번
                원소 400만 개에서 없는 값: %d번""".formatted(absent.get(), linear, th.get(), four.get()));
    }

    @Test
    @DisplayName("16칸 배열에서 37은 비교 4번에 찾고 38은 없다")
    void widgetArray() {
        int[] a = {3, 7, 12, 18, 21, 25, 30, 34, 37, 41, 46, 52, 58, 63, 70, 77};
        AtomicInteger c = new AtomicInteger();
        assertThat(find(a, 37, c)).isEqualTo(8);
        assertThat(c.get()).isEqualTo(4);
        AtomicInteger c2 = new AtomicInteger();
        assertThat(find(a, 38, c2)).isEqualTo(-1);
        assertThat(c2.get()).isEqualTo(4);
    }

    @Test
    @DisplayName("정렬되지 않은 배열과 리스트에서는 있는 값을 찾아도 음수가 나오고 예외는 없다")
    void unsorted() {
        int[] a = {9, 2, 7, 4, 5};
        int r = Arrays.binarySearch(a, 9);
        assertThat(r).isNegative();

        List<String> codes = new ArrayList<>(List.of("C10", "A20", "B30"));
        int before = Collections.binarySearch(codes, "C10");
        assertThat(before).isNegative();
        Collections.sort(codes);
        int after = Collections.binarySearch(codes, "C10");
        assertThat(after).isEqualTo(2);

        CiOutput.record(LESSON, "unsorted", """
                Arrays.binarySearch({9, 2, 7, 4, 5}, 9) → %d

                정렬 전 [C10, A20, B30]에서 "C10" → %d
                정렬 후 %s에서 "C10" → %d""".formatted(r, before, codes, after));
    }

    record Order(long id, int amount) {
    }

    @Test
    @DisplayName("금액 순으로 정렬한 주문에서 id로 찾으면 틀리고, 같은 Comparator로 찾으면 맞다")
    void sameComparator() {
        List<Order> orders = new ArrayList<>(List.of(
                new Order(1, 5000), new Order(2, 1000), new Order(3, 9000),
                new Order(4, 3000), new Order(5, 7000)));
        Comparator<Order> byAmount = Comparator.comparingInt(Order::amount);
        Comparator<Order> byId = Comparator.comparingLong(Order::id);
        orders.sort(byAmount);
        // 금액 순: 2(1000), 4(3000), 1(5000), 5(7000), 3(9000)
        int wrong = Collections.binarySearch(orders, new Order(4, 0), byId);
        int right = Collections.binarySearch(orders, new Order(0, 3000), byAmount);
        assertThat(wrong).isNegative();
        assertThat(right).isEqualTo(1);
    }

    @Test
    @DisplayName("LinkedList에도 비교 횟수는 log n번이다")
    void linkedListComparisons() {
        List<Integer> linked = new LinkedList<>();
        for (int i = 0; i < 100_000; i++) linked.add(i * 2);
        AtomicInteger count = new AtomicInteger();
        Comparator<Integer> counting = (x, y) -> {
            count.incrementAndGet();
            return Integer.compare(x, y);
        };
        Collections.binarySearch(linked, 1, counting);
        assertThat(count.get()).isBetween(16, 17);
    }

    @Test
    @DisplayName("배송비 경계값에서 27000은 -3, 30000은 2, 60000은 -5")
    void insertionPoint() {
        int[] bounds = {0, 10_000, 30_000, 50_000};
        int[] fees = {3000, 2500, 1000, 0};

        int i27 = Arrays.binarySearch(bounds, 27_000);
        int i30 = Arrays.binarySearch(bounds, 30_000);
        int i60 = Arrays.binarySearch(bounds, 60_000);
        assertThat(i27).isEqualTo(-3);
        assertThat(i30).isEqualTo(2);
        assertThat(i60).isEqualTo(-5);

        assertThat(fees[section(bounds, 27_000)]).isEqualTo(2500);
        assertThat(fees[section(bounds, 30_000)]).isEqualTo(1000);
        assertThat(fees[section(bounds, 60_000)]).isEqualTo(0);
        assertThat(fees[section(bounds, 0)]).isEqualTo(3000);

        assertThat(Arrays.binarySearch(new int[] {10, 20, 30}, 5)).isEqualTo(-1);

        CiOutput.record(LESSON, "fee", """
                binarySearch(bounds, 27_000) → %d
                binarySearch(bounds, 30_000) → %d
                binarySearch(bounds, 60_000) → %d

                27,000원의 배송비 → %d원""".formatted(i27, i30, i60, fees[section(bounds, 27_000)]));
    }

    /** 레슨의 코드 section */
    static int section(int[] bounds, int amount) {
        int i = Arrays.binarySearch(bounds, amount);
        if (i >= 0) return i;
        int p = -(i + 1);
        return p - 1;
    }

    @Test
    @DisplayName("같은 값이 다섯 개면 첫 번째가 아닌 위치가 나올 수 있다")
    void duplicates() {
        int[] a = {1, 2, 2, 2, 2, 2, 3};
        int r = Arrays.binarySearch(a, 2);
        assertThat(r).isNotEqualTo(1);
        CiOutput.record(LESSON, "dup", "binarySearch({1, 2, 2, 2, 2, 2, 3}, 2) → " + r);
    }

    @Test
    @DisplayName("buggy는 원소 하나짜리와 맨 끝 원소를 못 찾고 search는 찾는다")
    void boundary() {
        int[] one = {5};
        int[] four = {1, 3, 5, 7};
        assertThat(buggy(one, 5)).isEqualTo(-1);
        assertThat(buggy(four, 7)).isEqualTo(-1);
        assertThat(find(one, 5)).isEqualTo(0);
        assertThat(find(four, 7)).isEqualTo(3);
        CiOutput.record(LESSON, "buggy", """
                {5}에서 5:          buggy → %d, search → %d
                {1, 3, 5, 7}에서 7: buggy → %d, search → %d""".formatted(
                buggy(one, 5), find(one, 5), buggy(four, 7), find(four, 7)));
    }

    @Test
    @DisplayName("(low + high) / 2는 합이 int를 넘으면 음수, >>> 1과 low + (high - low) / 2는 바르다")
    void midOverflow() {
        int low = 1_500_000_000;
        int high = 1_600_000_000;
        int bad = (low + high) / 2;
        int shift = (low + high) >>> 1;
        int safe = low + (high - low) / 2;
        assertThat(bad).isNegative();
        assertThat(shift).isEqualTo(1_550_000_000);
        assertThat(safe).isEqualTo(1_550_000_000);
        CiOutput.record(LESSON, "mid", """
                (low + high) / 2         → %d
                (low + high) >>> 1       → %d
                low + (high - low) / 2   → %d""".formatted(bad, shift, safe));
    }

    @Test
    @DisplayName("TreeMap.floorEntry(27000)은 10000 구간이라 배송비 2500원")
    void floorEntry() {
        TreeMap<Integer, Integer> fee = new TreeMap<>(Map.of(
                0, 3000, 10_000, 2500, 30_000, 1000, 50_000, 0));
        int f = fee.floorEntry(27_000).getValue();
        assertThat(f).isEqualTo(2500);
        assertThat(fee.floorEntry(30_000).getValue()).isEqualTo(1000);
        CiOutput.record(LESSON, "floor", """
                floorEntry(27_000) → %s
                floorEntry(30_000) → %s""".formatted(fee.floorEntry(27_000), fee.floorEntry(30_000)));
    }

    @Test
    @DisplayName("넣을 위치에 add하면 정렬이 유지되어 다음 찾기도 맞다")
    void insertKeepsSorted() {
        List<String> codes = new ArrayList<>(List.of("A20", "B30", "C10"));
        String x = "B05";
        int i = Collections.binarySearch(codes, x);
        codes.add(-(i + 1), x);
        assertThat(codes).containsExactly("A20", "B05", "B30", "C10");
        assertThat(Collections.binarySearch(codes, "C10")).isEqualTo(3);
    }
}
