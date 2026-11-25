package lab.devlessons.greedydp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "그리디와 동적 계획법"(K12)의 주장을 검증한다. 순수 JUnit 테스트.
 */
class GreedyAndDpTest {

    private static final String LESSON = "greedy-and-dp";
    static final int NONE = Integer.MAX_VALUE;

    /** 레슨의 코드 predict. units는 큰 것부터 */
    static int greedy(int[] units, int amount) {
        int count = 0;
        for (int u : units) {
            count += amount / u;
            amount %= u;
        }
        return count;
    }

    static final AtomicLong CALLS = new AtomicLong();

    /** 레슨의 코드 bruteForce */
    static int best(int[] units, int amount) {
        CALLS.incrementAndGet();
        if (amount == 0) return 0;
        int min = Integer.MAX_VALUE;
        for (int u : units) {
            if (u <= amount) {
                int r = best(units, amount - u);
                if (r != Integer.MAX_VALUE) {
                    min = Math.min(min, r + 1);
                }
            }
        }
        return min;
    }

    static long cells;

    /** 레슨의 코드 table */
    static int[] table(int[] units, int amount) {
        int[] dp = new int[amount + 1];
        Arrays.fill(dp, NONE);
        dp[0] = 0;
        cells = 0;
        for (int m = 1; m <= amount; m++) {
            for (int u : units) {
                cells++;
                if (u <= m && dp[m - u] != NONE) {
                    dp[m] = Math.min(dp[m], dp[m - u] + 1);
                }
            }
        }
        return dp;
    }

    static int minCoins(int[] units, int amount) {
        return table(units, amount)[amount];
    }

    static final int[] WON = {500, 100, 50, 10};
    static final int[] ODD = {4, 3, 1};

    @Test
    @DisplayName("그리디는 6에 3, 우리 동전으로 3760원에 11, 1280원에 8")
    void greedyCounts() {
        assertThat(greedy(ODD, 6)).isEqualTo(3);
        assertThat(greedy(WON, 3760)).isEqualTo(11);
        assertThat(greedy(WON, 1280)).isEqualTo(8);
        assertThat(greedy(ODD, 8)).isEqualTo(2);
        assertThat(minCoins(ODD, 8)).isEqualTo(2);
        CiOutput.record(LESSON, "predict", "greedy({4, 3, 1}, 6) → " + greedy(ODD, 6));
        CiOutput.record(LESSON, "won", """
                greedy({500, 100, 50, 10}, 3760) → %d
                greedy({500, 100, 50, 10}, 1280) → %d""".formatted(greedy(WON, 3760), greedy(WON, 1280)));
    }

    record Meeting(int start, int end) {
        @Override
        public String toString() {
            return start + "~" + end + "시";
        }
    }

    /** 레슨의 코드 meetings */
    static List<Meeting> pick(List<Meeting> input) {
        List<Meeting> meetings = new ArrayList<>(input);
        meetings.sort(Comparator.comparingInt(Meeting::end));
        int lastEnd = 0;
        List<Meeting> chosen = new ArrayList<>();
        for (Meeting m : meetings) {
            if (m.start() >= lastEnd) {
                chosen.add(m);
                lastEnd = m.end();
            }
        }
        return chosen;
    }

    @Test
    @DisplayName("끝나는 시각이 이른 회의부터 고른다")
    void meetings() {
        List<Meeting> example = List.of(
                new Meeting(9, 12), new Meeting(9, 10), new Meeting(10, 11),
                new Meeting(11, 13), new Meeting(12, 14), new Meeting(13, 15));
        List<Meeting> chosen = pick(example);
        assertThat(chosen).containsExactly(
                new Meeting(9, 10), new Meeting(10, 11), new Meeting(11, 13), new Meeting(13, 15));

        List<Meeting> moved = List.of(
                new Meeting(1, 4), new Meeting(3, 5), new Meeting(0, 6),
                new Meeting(5, 7), new Meeting(8, 9), new Meeting(5, 9));
        assertThat(pick(moved)).containsExactly(new Meeting(1, 4), new Meeting(5, 7), new Meeting(8, 9));

        CiOutput.record(LESSON, "meetings", "회의 " + example + "\n고른 회의 → " + chosen + " (" + chosen.size() + "개)");
    }

    @Test
    @DisplayName("단위 1, 3, 4에서 그리디와 정답이 처음 갈리는 금액은 6, 우리 동전은 1000원까지 갈리지 않는다")
    void counterExample() {
        int first = -1;
        for (int m = 1; m <= 100; m++) {
            if (greedy(ODD, m) != best(ODD, Math.min(m, 6)) && m <= 6) {
                first = m;
                break;
            }
        }
        // 금액 100까지는 표(정답)와 비교한다
        int firstByTable = -1;
        int[] dp = table(new int[] {1, 3, 4}, 100);
        for (int m = 1; m <= 100; m++) {
            if (greedy(ODD, m) != dp[m]) {
                firstByTable = m;
                break;
            }
        }
        assertThat(first).isEqualTo(6);
        assertThat(firstByTable).isEqualTo(6);
        assertThat(best(ODD, 6)).isEqualTo(2);

        int[] wonDp = table(new int[] {10, 50, 100, 500}, 1000);
        int wonDiff = 0;
        for (int m = 10; m <= 1000; m += 10) {
            if (greedy(WON, m) != wonDp[m]) wonDiff++;
        }
        assertThat(wonDiff).isZero();

        int[] six = {6, 5, 1};
        assertThat(greedy(six, 10)).isEqualTo(5);
        assertThat(minCoins(new int[] {1, 5, 6}, 10)).isEqualTo(2);

        CiOutput.record(LESSON, "counter", """
                단위 4, 3, 1에서 그리디와 가장 적은 개수가 처음 다른 금액: %d (그리디 %d개, 정답 %d개)
                단위 500, 100, 50, 10에서 10원부터 1,000원까지 다른 금액: %d개""".formatted(
                firstByTable, greedy(ODD, 6), dp[6], wonDiff));
    }

    @Test
    @DisplayName("완전 탐색의 호출은 지수로 늘고, 표의 계산은 금액 × 단위 수다")
    void costs() {
        int[] units = {1, 3, 4};
        CALLS.set(0);
        best(units, 20);
        long calls20 = CALLS.get();
        CALLS.set(0);
        best(units, 30);
        long calls30 = CALLS.get();
        table(units, 30);
        long cells30 = cells;
        table(units, 10_000);
        long cells10k = cells;

        assertThat(calls30).isGreaterThan(calls20 * 50);
        assertThat(cells30).isEqualTo(90);
        assertThat(cells10k).isEqualTo(30_000);

        CiOutput.record(LESSON, "cost", """
                단위 1, 3, 4
                완전 탐색 best(20)의 호출: %,d번
                완전 탐색 best(30)의 호출: %,d번
                표 minCoins(30)의 칸 계산: %d번
                표 minCoins(10000)의 칸 계산: %,d번""".formatted(calls20, calls30, cells30, cells10k));
    }

    @Test
    @DisplayName("단위 1, 3, 4의 표는 0~7에 0, 1, 2, 1, 1, 2, 2, 2")
    void tableValues() {
        int[] dp = table(new int[] {1, 3, 4}, 7);
        assertThat(dp).containsExactly(0, 1, 2, 1, 1, 2, 2, 2);
        CiOutput.record(LESSON, "table", "dp[0..7] = " + Arrays.toString(dp));
    }

    @Test
    @DisplayName("계단을 1칸이나 2칸 오르는 방법의 수는 ways(n-1) + ways(n-2)")
    void stairs() {
        long[] ways = new long[11];
        ways[0] = 1;
        ways[1] = 1;
        for (int n = 2; n <= 10; n++) ways[n] = ways[n - 1] + ways[n - 2];
        assertThat(ways[4]).isEqualTo(5);
        assertThat(ways[10]).isEqualTo(89);
    }

    static Map<Integer, Long> memo;

    /** 레슨의 코드 computeMemo */
    static long fibCompute(int n) {
        if (n < 2) return n;
        return memo.computeIfAbsent(n, k -> fibCompute(k - 1) + fibCompute(k - 2));
    }

    /** 레슨의 코드 fixedMemo */
    static long fibFixed(int n) {
        if (n < 2) return n;
        Long hit = memo.get(n);
        if (hit != null) return hit;
        long v = fibFixed(n - 1) + fibFixed(n - 2);
        memo.put(n, v);
        return v;
    }

    @Test
    @DisplayName("computeIfAbsent 안에서 같은 맵을 고치면 ConcurrentModificationException, get과 put으로 고치면 832040")
    void computeIfAbsentMemo() {
        memo = new HashMap<>();
        assertThatThrownBy(() -> fibCompute(30)).isInstanceOf(ConcurrentModificationException.class);

        int smallest = -1;
        for (int n = 2; n <= 10; n++) {
            memo = new HashMap<>();
            try {
                fibCompute(n);
            } catch (ConcurrentModificationException e) {
                smallest = n;
                break;
            }
        }
        assertThat(smallest).isEqualTo(3);

        memo = new HashMap<>();
        long fixed = fibFixed(30);
        assertThat(fixed).isEqualTo(832_040L);

        long[] f = new long[31];
        f[1] = 1;
        for (int i = 2; i <= 30; i++) f[i] = f[i - 1] + f[i - 2];
        assertThat(f[30]).isEqualTo(832_040L);

        CiOutput.record(LESSON, "compute", """
                computeIfAbsent로 fib(30) → ConcurrentModificationException
                예외가 나는 가장 작은 n: %d
                get, put으로 고친 fib(30) → %d
                아래에서 위로 채운 fib(30) → %d""".formatted(smallest, fixed, f[30]));
    }
}
