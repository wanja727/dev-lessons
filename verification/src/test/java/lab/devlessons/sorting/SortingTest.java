package lab.devlessons.sorting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "정렬"(K6)의 주장을 검증한다. 정렬 방법마다 비교 횟수를 센다.
 * 걸린 시간은 환경마다 달라서 남기기만 하고, 비교 횟수와 결과만 단정한다.
 */
class SortingTest {

    private static final String LESSON = "sorting";

    record Order(long id, int amount) {
    }

    record TimedOrder(long id, int amount, LocalDateTime createdAt) {
    }

    @Test
    @DisplayName("List.sort는 안정 정렬이라 금액이 같은 주문은 원래 순서를 지킨다: id 2, 4, 1, 3")
    void stableListSort() {
        List<Order> orders = new ArrayList<>(List.of(
                new Order(1, 5000), new Order(2, 3000), new Order(3, 5000), new Order(4, 3000)));
        orders.sort(Comparator.comparingInt(Order::amount));
        List<Long> ids = orders.stream().map(Order::id).toList();
        assertThat(ids).containsExactly(2L, 4L, 1L, 3L);
        CiOutput.record(LESSON, "predict", "정렬한 뒤 id: " + ids + "\n" + orders.stream()
                .map(o -> "  id " + o.id() + ", " + o.amount() + "원").reduce((a, b) -> a + "\n" + b).orElseThrow());

        record Named(String name, int amount) {
        }
        List<Named> named = new ArrayList<>(List.of(
                new Named("A", 3000), new Named("B", 1000), new Named("C", 3000), new Named("D", 1000)));
        named.sort(Comparator.comparingInt(Named::amount));
        assertThat(named.stream().map(Named::name).toList()).containsExactly("B", "D", "A", "C");
    }

    static long comparisons;

    static void bubble(int[] a) {
        int n = a.length;
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - 1 - i; j++) {
                comparisons++;
                if (a[j] > a[j + 1]) {
                    int t = a[j];
                    a[j] = a[j + 1];
                    a[j + 1] = t;
                }
            }
        }
    }

    static void mergeSort(int[] a, int lo, int hi) {
        if (hi - lo < 2) {
            return;
        }
        int mid = (lo + hi) >>> 1;
        mergeSort(a, lo, mid);
        mergeSort(a, mid, hi);
        merge(a, lo, mid, hi);
    }

    static void merge(int[] a, int lo, int mid, int hi) {
        int[] tmp = new int[hi - lo];
        int i = lo;
        int j = mid;
        int k = 0;
        while (i < mid && j < hi) {
            comparisons++;
            if (a[i] <= a[j]) {
                tmp[k++] = a[i++];
            } else {
                tmp[k++] = a[j++];
            }
        }
        while (i < mid) {
            tmp[k++] = a[i++];
        }
        while (j < hi) {
            tmp[k++] = a[j++];
        }
        System.arraycopy(tmp, 0, a, lo, tmp.length);
    }

    /** 맨 앞 원소를 피벗으로 쓰는 퀵 정렬 (hi는 포함하지 않는다) */
    static void quickFirstPivot(int[] a, int lo, int hi) {
        if (hi - lo < 2) {
            return;
        }
        int pivot = a[lo];
        int store = lo + 1;
        for (int i = lo + 1; i < hi; i++) {
            comparisons++;
            if (a[i] < pivot) {
                int t = a[i];
                a[i] = a[store];
                a[store] = t;
                store++;
            }
        }
        int t = a[lo];
        a[lo] = a[store - 1];
        a[store - 1] = t;
        quickFirstPivot(a, lo, store - 1);
        quickFirstPivot(a, store, hi);
    }

    static int[] shuffled(int n, long seed) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = i;
        }
        Random r = new Random(seed);
        for (int i = n - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
        return a;
    }

    static boolean sorted(int[] a) {
        for (int i = 1; i < a.length; i++) {
            if (a[i - 1] > a[i]) {
                return false;
            }
        }
        return true;
    }

    @Test
    @DisplayName("버블 정렬의 비교는 n(n-1)/2번: 5개면 10, 10개면 45, 1천 개면 499500")
    void bubbleComparisons() {
        int[] small = {5, 1, 4, 2, 3};
        comparisons = 0;
        bubble(small);
        assertThat(small).containsExactly(1, 2, 3, 4, 5);
        assertThat(comparisons).isEqualTo(10);

        for (int[] nAndExpected : new int[][] {{6, 15}, {10, 45}, {1_000, 499_500}}) {
            comparisons = 0;
            int[] a = shuffled(nAndExpected[0], 1);
            bubble(a);
            assertThat(sorted(a)).isTrue();
            assertThat(comparisons).isEqualTo(nAndExpected[1]);
        }
        // 이미 정렬된 배열도 같은 수를 비교한다
        comparisons = 0;
        bubble(new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        assertThat(comparisons).isEqualTo(45);
    }

    @Test
    @DisplayName("원소 1만 개에서 병합 정렬의 비교는 버블 정렬보다 수백 배 적다")
    void bubbleVersusMerge() {
        int[] base = shuffled(10_000, 42);
        // JIT가 데우도록
        for (int i = 0; i < 3; i++) {
            bubble(shuffled(2_000, i));
            mergeSort(shuffled(2_000, i), 0, 2_000);
        }

        int[] a = base.clone();
        comparisons = 0;
        long start = System.nanoTime();
        bubble(a);
        long bubbleMs = (System.nanoTime() - start) / 1_000_000;
        long bubbleCmp = comparisons;

        int[] b = base.clone();
        comparisons = 0;
        start = System.nanoTime();
        mergeSort(b, 0, b.length);
        long mergeMs = (System.nanoTime() - start) / 1_000_000;
        long mergeCmp = comparisons;

        int[] c = base.clone();
        start = System.nanoTime();
        Arrays.sort(c);
        long arraysMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(sorted(a)).isTrue();
        assertThat(b).isEqualTo(a);
        assertThat(c).isEqualTo(a);
        assertThat(bubbleCmp).isEqualTo(49_995_000L);
        assertThat(mergeCmp).isLessThan(140_000L);
        assertThat(bubbleCmp / mergeCmp).isGreaterThan(300);

        CiOutput.record(LESSON, "compare", """
                무작위 원소 10,000개
                버블 정렬: 비교 %,d번, %,dms
                병합 정렬: 비교 %,d번, %,dms
                Arrays.sort(int[]): %,dms"""
                .formatted(bubbleCmp, bubbleMs, mergeCmp, mergeMs, arraysMs));
    }

    @Test
    @DisplayName("16개를 절반씩 나누면 4겹이다. 맨 앞 피벗의 퀵 정렬은 정렬된 배열에서 비교가 n(n-1)/2번이다")
    void depthsAndQuickWorstCase() {
        int depth = 0;
        for (int n = 16; n > 1; n /= 2) {
            depth++;
        }
        assertThat(depth).isEqualTo(4);

        int n = 2_000;
        int[] already = new int[n];
        for (int i = 0; i < n; i++) {
            already[i] = i;
        }
        comparisons = 0;
        quickFirstPivot(already, 0, n);
        long sortedCmp = comparisons;
        assertThat(sorted(already)).isTrue();
        assertThat(sortedCmp).isEqualTo((long) n * (n - 1) / 2);

        int[] mixed = shuffled(n, 7);
        comparisons = 0;
        quickFirstPivot(mixed, 0, n);
        long mixedCmp = comparisons;
        assertThat(sorted(mixed)).isTrue();
        assertThat(mixedCmp * 20).isLessThan(sortedCmp);

        CiOutput.record(LESSON, "quick", """
                원소 2,000개, 맨 앞을 피벗으로
                이미 정렬된 배열: 비교 %,d번
                섞인 배열:       비교 %,d번""".formatted(sortedCmp, mixedCmp));
    }

    /** 주문을 금액으로 정렬하는 맨 앞 피벗 퀵 정렬. 안정이 아닌 것을 보인다 */
    static void quickOrders(Order[] a, int lo, int hi) {
        if (hi - lo < 2) {
            return;
        }
        Order pivot = a[lo];
        int store = lo + 1;
        for (int i = lo + 1; i < hi; i++) {
            if (a[i].amount() < pivot.amount()) {
                Order t = a[i];
                a[i] = a[store];
                a[store] = t;
                store++;
            }
        }
        Order t = a[lo];
        a[lo] = a[store - 1];
        a[store - 1] = t;
        quickOrders(a, lo, store - 1);
        quickOrders(a, store, hi);
    }

    @Test
    @DisplayName("퀵 정렬은 같은 금액의 순서를 바꿀 수 있고, 이미 정렬된 리스트의 List.sort는 비교가 n-1번이다")
    void quickIsNotStableAndTimSortRuns() {
        Order[] orders = {new Order(1, 5000), new Order(2, 3000), new Order(3, 5000), new Order(4, 3000)};
        quickOrders(orders, 0, orders.length);
        List<Long> quickIds = Arrays.stream(orders).map(Order::id).toList();
        assertThat(quickIds).isNotEqualTo(List.of(2L, 4L, 1L, 3L));
        assertThat(Arrays.stream(orders).map(Order::amount).toList()).containsExactly(3000, 3000, 5000, 5000);

        List<Integer> already = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            already.add(i);
        }
        long[] calls = {0};
        already.sort((x, y) -> {
            calls[0]++;
            return Integer.compare(x, y);
        });
        assertThat(calls[0]).isEqualTo(999);

        CiOutput.record(LESSON, "stable", """
                같은 주문을 금액 순으로
                List.sort (TimSort): id [2, 4, 1, 3]
                맨 앞 피벗 퀵 정렬:  id %s

                이미 정렬된 1,000개를 List.sort
                Comparator 호출: %,d번""".formatted(quickIds, calls[0]));
    }

    static List<TimedOrder> timed() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 25, 9, 0);
        return List.of(
                new TimedOrder(1, 5000, t),
                new TimedOrder(2, 3000, t.plusMinutes(1)),
                new TimedOrder(3, 5000, t.plusMinutes(2)),
                new TimedOrder(4, 3000, t.plusMinutes(3)));
    }

    @Test
    @DisplayName("thenComparing(...).reversed()는 두 기준을 모두 뒤집고, thenComparing(b, reverseOrder())는 둘째만 뒤집는다")
    void reversedScope() {
        List<TimedOrder> chain = new ArrayList<>(timed());
        chain.sort(Comparator.comparingInt(TimedOrder::amount)
                .reversed()
                .thenComparing(TimedOrder::createdAt));
        List<TimedOrder> allReversed = new ArrayList<>(timed());
        allReversed.sort(Comparator.comparingInt(TimedOrder::amount)
                .thenComparing(TimedOrder::createdAt)
                .reversed());
        List<TimedOrder> secondOnly = new ArrayList<>(timed());
        secondOnly.sort(Comparator.comparingInt(TimedOrder::amount)
                .thenComparing(TimedOrder::createdAt, Comparator.reverseOrder()));

        assertThat(chain.stream().map(TimedOrder::id).toList()).containsExactly(1L, 3L, 2L, 4L);
        assertThat(allReversed.stream().map(TimedOrder::id).toList()).containsExactly(3L, 1L, 4L, 2L);
        assertThat(secondOnly.stream().map(TimedOrder::id).toList()).containsExactly(4L, 2L, 3L, 1L);

        CiOutput.record(LESSON, "chain", """
                주문: 1(5000원, 9:00) 2(3000원, 9:01)
                      3(5000원, 9:02) 4(3000원, 9:03)

                comparingInt(amount).reversed()
                  .thenComparing(createdAt)
                → id %s

                comparingInt(amount)
                  .thenComparing(createdAt).reversed()
                → id %s""".formatted(
                chain.stream().map(TimedOrder::id).toList(),
                allReversed.stream().map(TimedOrder::id).toList()));

        record Member(String name, int age) {
        }
        List<Member> members = new ArrayList<>(List.of(
                new Member("다은", 30), new Member("가람", 25), new Member("나래", 30)));
        members.sort(Comparator.comparingInt(Member::age).reversed().thenComparing(Member::name));
        assertThat(members.stream().map(Member::name).toList()).containsExactly("나래", "다은", "가람");
    }

    record Product(String name, Integer price) {
    }

    @Test
    @DisplayName("Comparator.comparing은 null 값에서 NullPointerException을 던지고, nullsLast는 null을 뒤로 보낸다")
    void nullsInSort() {
        List<Product> products = new ArrayList<>(List.of(
                new Product("가", 300), new Product("나", null), new Product("다", 100)));
        assertThatThrownBy(() -> products.sort(Comparator.comparing(Product::price)))
                .isInstanceOf(NullPointerException.class);
        products.sort(Comparator.comparing(Product::price, Comparator.nullsLast(Comparator.naturalOrder())));
        assertThat(products.stream().map(Product::name).toList()).containsExactly("다", "가", "나");
    }

    @Test
    @DisplayName("뺄셈 Comparator는 2,000,000,000과 -500,000,000을 거꾸로 놓고, comparingInt는 바르게 놓는다")
    void subtractionComparatorOverflows() {
        Comparator<Order> bySubtract = (a, b) -> a.amount() - b.amount();
        Comparator<Order> byCompare = Comparator.comparingInt(Order::amount);
        List<Order> orders = List.of(
                new Order(1, 2_000_000_000), new Order(2, -500_000_000), new Order(3, 1000));

        List<Order> wrong = new ArrayList<>(orders);
        wrong.sort(bySubtract);
        List<Order> right = new ArrayList<>(orders);
        right.sort(byCompare);

        assertThat(2_000_000_000 - (-500_000_000)).isNegative();
        assertThat(right.stream().map(Order::amount).toList()).containsExactly(-500_000_000, 1000, 2_000_000_000);
        assertThat(wrong.stream().map(Order::amount).toList()).isNotEqualTo(
                right.stream().map(Order::amount).toList());
        assertThat(wrong.indexOf(orders.get(0))).isLessThan(wrong.indexOf(orders.get(1)));

        CiOutput.record(LESSON, "subtract", """
                2000000000 - (-500000000) = %d

                뺄셈 Comparator:
                  %s
                comparingInt:
                  %s""".formatted(2_000_000_000 - (-500_000_000),
                wrong.stream().map(Order::amount).toList(),
                right.stream().map(Order::amount).toList()));
    }
}
