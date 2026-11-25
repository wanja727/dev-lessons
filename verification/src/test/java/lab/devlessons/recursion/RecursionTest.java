package lab.devlessons.recursion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "재귀"(K5)의 주장을 검증한다. 호출 횟수와 스택에 쌓인 프레임을 센다.
 * StackOverflowError가 나는 깊이는 환경마다 달라서 남기기만 하고, 나는지 안 나는지만 단정한다.
 */
class RecursionTest {

    private static final String LESSON = "recursion";

    record Category(String name, List<Category> children) {
    }

    static int count(Category c) {
        int total = 1;
        for (Category child : c.children()) {
            total += count(child);
        }
        return total;
    }

    static int countIter(Category root) {
        Deque<Category> st = new ArrayDeque<>();
        st.push(root);
        int total = 0;
        while (!st.isEmpty()) {
            Category c = st.pop();
            total++;
            c.children().forEach(st::push);
        }
        return total;
    }

    static Category leaf(String name) {
        return new Category(name, List.of());
    }

    /** 전자제품 > (컴퓨터 > (노트북, 데스크톱), 휴대폰 > (스마트폰), TV) */
    static Category sampleTree() {
        return new Category("전자제품", List.of(
                new Category("컴퓨터", List.of(leaf("노트북"), leaf("데스크톱"))),
                new Category("휴대폰", List.of(leaf("스마트폰"))),
                leaf("TV")));
    }

    static int sum(int n) {
        if (n == 0) {
            return 0;
        }
        return n + sum(n - 1);
    }

    static int sumWrong(int n) {
        return n + sumWrong(n - 1);
    }

    static int pow(int b, int e) {
        return b * pow(b, e - 1);
    }

    @Test
    @DisplayName("count는 예제 트리의 카테고리 수 7을 돌려주고, 반복문 버전도 같다. 하위가 없으면 1이다")
    void countCategories() {
        Category tree = sampleTree();
        assertThat(count(tree)).isEqualTo(7);
        assertThat(countIter(tree)).isEqualTo(7);
        assertThat(count(leaf("TV"))).isEqualTo(1);
        CiOutput.record(LESSON, "count", "count(전자제품) → " + count(tree)
                + "\ncountIter(전자제품) → " + countIter(tree) + "\ncount(TV) → " + count(leaf("TV")));
    }

    @Test
    @DisplayName("끝나는 조건이 없거나 가까워지지 않으면 StackOverflowError다: sumWrong, sum(-1), 끝나는 조건 없는 pow")
    void noBaseCase() {
        assertThatThrownBy(() -> sumWrong(5)).isInstanceOf(StackOverflowError.class);
        assertThatThrownBy(() -> sum(-1)).isInstanceOf(StackOverflowError.class);
        assertThatThrownBy(() -> pow(2, 3)).isInstanceOf(StackOverflowError.class);
        CiOutput.record(LESSON, "nobase", "sumWrong(5) → StackOverflowError\nsum(-1) → StackOverflowError");
    }

    static int depthReached;

    static int sumCounting(int n) {
        depthReached++;
        if (n == 0) {
            return 0;
        }
        return n + sumCounting(n - 1);
    }

    @Test
    @DisplayName("기본 설정에서 sum(100_000)은 StackOverflowError를 던진다. 작은 n은 정상이다")
    void deepSumOverflows() {
        assertThat(sum(3)).isEqualTo(6);
        assertThat(sum(1_000)).isEqualTo(500_500);
        depthReached = 0;
        Throwable error = null;
        try {
            sumCounting(100_000);
        } catch (StackOverflowError e) {
            error = e;
        }
        assertThat(error).isInstanceOf(StackOverflowError.class);
        int reached = depthReached;
        CiOutput.record(LESSON, "predict", """
                sum(3) → 6
                sum(1000) → 500500
                sum(100000) → StackOverflowError
                (에러가 나기 전까지 쌓은 호출: %,d겹)""".formatted(reached));
    }

    static List<String> framesAtBottom = new ArrayList<>();

    static int sumTrace(int n) {
        if (n == 0) {
            framesAtBottom = Arrays.stream(Thread.currentThread().getStackTrace())
                    .filter(f -> f.getMethodName().equals("sumTrace"))
                    .map(f -> "sumTrace")
                    .toList();
            return 0;
        }
        return n + sumTrace(n - 1);
    }

    @Test
    @DisplayName("sum(3)에서 sum(0)이 실행되는 순간 스택에 sum의 프레임이 4개다")
    void framesOnStack() {
        assertThat(sumTrace(3)).isEqualTo(6);
        assertThat(framesAtBottom).hasSize(4);
    }

    static Category chain(int depth) {
        Category c = leaf("c" + depth);
        for (int i = depth - 1; i >= 1; i--) {
            c = new Category("c" + i, List.of(c));
        }
        return c;
    }

    @Test
    @DisplayName("깊이 10만의 트리에서 재귀 버전은 StackOverflowError, 반복문 버전은 100000을 돌려준다. 세 겹 트리의 프레임은 3개다")
    void deepTree() {
        Category deep = chain(100_000);
        assertThatThrownBy(() -> count(deep)).isInstanceOf(StackOverflowError.class);
        assertThat(countIter(deep)).isEqualTo(100_000);

        // 카테고리 2천 개가 세 겹: 루트 1, 둘째 겹 39, 셋째 겹 1960
        List<Category> second = new ArrayList<>();
        for (int i = 0; i < 39; i++) {
            List<Category> third = new ArrayList<>();
            for (int j = 0; j < 50 + (i < 10 ? 1 : 0); j++) {
                third.add(leaf("c" + i + "-" + j));
            }
            second.add(new Category("b" + i, third));
        }
        Category wide = new Category("root", second);
        assertThat(count(wide)).isEqualTo(1 + 39 + 39 * 50 + 10);
        maxFrames = 0;
        countDepth(wide);
        assertThat(maxFrames).isEqualTo(3);

        CiOutput.record(LESSON, "deep", """
                한 줄로 이어진 카테고리 100,000개
                count(재귀)     → StackOverflowError
                countIter(반복) → %,d

                카테고리 %,d개, 세 겹
                가장 깊을 때 count 프레임: %d개""".formatted(countIter(deep), count(wide), maxFrames));
    }

    static int maxFrames;

    static int countDepth(Category c) {
        int frames = (int) Arrays.stream(Thread.currentThread().getStackTrace())
                .filter(f -> f.getMethodName().equals("countDepth")).count();
        maxFrames = Math.max(maxFrames, frames);
        int total = 1;
        for (Category child : c.children()) {
            total += countDepth(child);
        }
        return total;
    }

    static long calls;
    static Map<Integer, Integer> callsByArg = new HashMap<>();

    static long fib(int n) {
        calls++;
        callsByArg.merge(n, 1, Integer::sum);
        if (n < 2) {
            return n;
        }
        return fib(n - 1) + fib(n - 2);
    }

    static long fibPlain(int n) {
        calls++;
        if (n < 2) {
            return n;
        }
        return fibPlain(n - 1) + fibPlain(n - 2);
    }

    static long fibMemo(int n, long[] memo) {
        calls++;
        if (n < 2) {
            return n;
        }
        if (memo[n] != 0) {
            return memo[n];
        }
        memo[n] = fibMemo(n - 1, memo) + fibMemo(n - 2, memo);
        return memo[n];
    }

    @Test
    @DisplayName("fib(5)에서 fib(3)은 2번, fib(2)는 3번 불리고, n이 10 늘 때 호출 수가 100배 넘게 는다. 메모이제이션이면 fib(40)이 79번이다")
    void fibonacciCalls() {
        callsByArg.clear();
        calls = 0;
        assertThat(fib(5)).isEqualTo(5);
        assertThat(callsByArg.get(3)).isEqualTo(2);
        assertThat(callsByArg.get(2)).isEqualTo(3);
        assertThat(calls).isEqualTo(15);

        StringBuilder out = new StringBuilder();
        long prev = 0;
        for (int n : new int[] {20, 30, 40}) {
            calls = 0;
            long start = System.nanoTime();
            long value = fibPlain(n);
            long ms = (System.nanoTime() - start) / 1_000_000;
            if (prev > 0) {
                assertThat(calls).isGreaterThan(prev * 100);
            }
            prev = calls;
            out.append("fib(%d) = %,d\n  호출 %,d번, %,dms\n".formatted(n, value, calls, ms));
            callsByArg.clear();
        }
        CiOutput.record(LESSON, "fib", out.toString().strip());

        calls = 0;
        long value = fibMemo(40, new long[41]);
        assertThat(value).isEqualTo(102_334_155L);
        assertThat(calls).isEqualTo(79);
        CiOutput.record(LESSON, "memo", "fibMemo(40) = %,d\n  호출 %d번".formatted(value, calls));
    }

    static Map<String, Integer> pathCalls = new HashMap<>();

    static long paths(int r, int c) {
        pathCalls.merge(r + "," + c, 1, Integer::sum);
        if (r == 0 || c == 0) {
            return 1;
        }
        return paths(r - 1, c) + paths(r, c - 1);
    }

    @Test
    @DisplayName("격자 길 찾기 재귀도 같은 (r, c)를 여러 번 계산한다")
    void gridPathsRepeat() {
        pathCalls.clear();
        assertThat(paths(5, 5)).isEqualTo(252);
        assertThat(pathCalls.get("1,1")).isGreaterThan(1);
        assertThat(pathCalls.values().stream().mapToInt(Integer::intValue).sum()).isGreaterThan(pathCalls.size() * 5);
    }

    /** 부모를 가리키는 카테고리 (엔티티 대신 순수 자바 객체) */
    static final class Node {
        final long id;
        Node parent;

        Node(long id) {
            this.id = id;
        }
    }

    static int depth(Node c) {
        if (c.parent == null) {
            return 0;
        }
        return 1 + depth(c.parent);
    }

    static int depthSafe(Node c) {
        Set<Long> seen = new HashSet<>();
        int d = 0;
        for (Node p = c; p.parent != null; p = p.parent) {
            if (!seen.add(p.id)) {
                throw new IllegalStateException("카테고리 순환: id " + p.id);
            }
            d++;
        }
        return d;
    }

    @Test
    @DisplayName("부모가 서로를 가리키면 depth는 StackOverflowError, 방문한 id를 담는 버전은 순환을 알리는 예외를 던진다")
    void cyclicParent() {
        Node root = new Node(1);
        Node pc = new Node(2);
        Node laptop = new Node(3);
        pc.parent = root;
        laptop.parent = pc;
        assertThat(depth(laptop)).isEqualTo(2);
        assertThat(depthSafe(laptop)).isEqualTo(2);

        Node a = new Node(10);
        Node b = new Node(20);
        a.parent = b;
        b.parent = a;
        assertThatThrownBy(() -> depth(a)).isInstanceOf(StackOverflowError.class);
        assertThatThrownBy(() -> depthSafe(a))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("카테고리 순환: id 10");
        CiOutput.record(LESSON, "cycle", """
                depth(노트북) → 2
                A.parent = B, B.parent = A
                depth(A)     → StackOverflowError
                depthSafe(A) → IllegalStateException: 카테고리 순환: id 10""");
    }
}
