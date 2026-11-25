package lab.devlessons.timecomplexity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "시간 복잡도"(K1)의 주장을 검증한다. 반복문이 도는 횟수를 직접 센다.
 * 걸린 시간은 환경마다 달라서 CiOutput에 남기기만 하고, 결과가 같은 것만 단정한다.
 */
class TimeComplexityTest {

    private static final String LESSON = "time-complexity";

    record Member(Long id, String email, String name) {
    }

    static final class Order {
        private final Long memberId;
        private String memberName;

        Order(Long memberId) {
            this.memberId = memberId;
        }

        Long memberId() {
            return memberId;
        }

        String memberName() {
            return memberName;
        }

        void setMemberName(String memberName) {
            this.memberName = memberName;
        }
    }

    /** 코드 predict와 같은 반복. equals를 부른 횟수를 돌려준다 */
    static long[] exists(List<Member> members, String email) {
        long calls = 0;
        for (Member m : members) {
            calls++;
            if (m.email().equals(email)) {
                return new long[] {1, calls};
            }
        }
        return new long[] {0, calls};
    }

    static List<Member> members(int n) {
        List<Member> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Member((long) i, "user" + i + "@shop.com", "회원" + i));
        }
        return list;
    }

    static long pairLoopCount(int n) {
        long count = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                count++;
            }
        }
        return count;
    }

    static int halvingSteps(int n) {
        int steps = 0;
        for (int k = n; k > 1; k /= 2) {
            steps++;
        }
        return steps;
    }

    @Test
    @DisplayName("코드 predict는 찾는 값이 없으면 회원 수만큼 equals를 부르고, 맨 앞에 있으면 한 번이다")
    void linearSearchCountsEquals() {
        long[] small = exists(members(1_000), "none@shop.com");
        long[] large = exists(members(100_000), "none@shop.com");
        long[] two = exists(members(2_000), "none@shop.com");
        long[] first = exists(members(1_000), "user0@shop.com");

        assertThat(small).containsExactly(0, 1_000);
        assertThat(large).containsExactly(0, 100_000);
        assertThat(two).containsExactly(0, 2_000);
        assertThat(first).containsExactly(1, 1);

        CiOutput.record(LESSON, "predict", """
                회원 1,000명, 없는 이메일 → equals %,d번
                회원 100,000명, 없는 이메일 → equals %,d번
                회원 1,000명, 맨 앞의 이메일 → equals %,d번""".formatted(small[1], large[1], first[1]));
    }

    @Test
    @DisplayName("겹친 반복의 비교 횟수는 n(n-1)/2, 절반씩 줄이는 반복은 log n번이다")
    void loopShapes() {
        assertThat(pairLoopCount(1_000)).isEqualTo(499_500);
        assertThat(pairLoopCount(2_000)).isEqualTo(1_999_000);
        assertThat(halvingSteps(1_024)).isEqualTo(10);
        assertThat(halvingSteps(1_000_000)).isEqualTo(19);

        // i *= 2로 늘리는 반복도 같은 모양이다 (옮긴 문제)
        int prints = 0;
        for (int i = 1; i < 1024; i *= 2) {
            prints++;
        }
        assertThat(prints).isEqualTo(10);

        // 위젯의 숫자: n이 10, 1천, 10만일 때 네 모양의 단계 수
        StringBuilder out = new StringBuilder();
        for (int n : new int[] {10, 1_000, 100_000}) {
            long quadratic = (long) n * (n - 1) / 2;
            if (n <= 10_000) {
                assertThat(pairLoopCount(n)).isEqualTo(quadratic);
            }
            out.append("n = %,d\n  O(1) 1, O(log n) %d\n  O(n) %,d, O(n²) %,d\n"
                    .formatted(n, halvingSteps(n), n, quadratic));
        }
        assertThat(halvingSteps(10)).isEqualTo(3);
        assertThat(halvingSteps(1_000)).isEqualTo(9);
        assertThat(halvingSteps(100_000)).isEqualTo(16);
        CiOutput.record(LESSON, "shapes", out.toString().strip());
    }

    static boolean hasDuplicatePair(String[] emails) {
        int n = emails.length;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (emails[i].equals(emails[j])) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean hasDuplicateSet(String[] emails) {
        Set<String> seen = new HashSet<>();
        for (String e : emails) {
            if (!seen.add(e)) {
                return true;
            }
        }
        return false;
    }

    static String[] emails(int n) {
        String[] arr = new String[n];
        for (int i = 0; i < n; i++) {
            arr[i] = "user" + i + "@shop.com";
        }
        return arr;
    }

    static long millis(Runnable r) {
        long start = System.nanoTime();
        r.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    @Test
    @DisplayName("중복 찾기를 HashSet으로 바꾸면 결과는 같고, n이 커질수록 겹친 반복보다 훨씬 빨리 끝난다")
    void duplicateCheckTiming() {
        // JIT가 데우도록 작은 입력으로 몇 번 돌린다
        for (int i = 0; i < 20; i++) {
            hasDuplicatePair(emails(2_000));
            hasDuplicateSet(emails(2_000));
        }
        StringBuilder out = new StringBuilder();
        for (int n : new int[] {100, 10_000, 100_000}) {
            String[] arr = emails(n);
            boolean[] results = new boolean[2];
            long slow = millis(() -> results[0] = hasDuplicatePair(arr));
            long fast = millis(() -> results[1] = hasDuplicateSet(arr));
            assertThat(results[0]).isFalse();
            assertThat(results[1]).isFalse();
            out.append("n = %,d\n  겹친 반복 %,dms\n  HashSet %,dms\n".formatted(n, slow, fast));
        }
        // 중복이 있으면 둘 다 true
        String[] dup = emails(1_000);
        dup[999] = dup[3];
        assertThat(hasDuplicatePair(dup)).isTrue();
        assertThat(hasDuplicateSet(dup)).isTrue();
        CiOutput.record(LESSON, "timing", out.toString().strip());
    }

    @Test
    @DisplayName("matchSlow와 matchFast는 같은 결과를 내고, 비교는 주문 수 곱하기 회원 수, get은 주문 수만큼이다")
    void matchOrdersToMembers() {
        List<Member> members = members(1_000);
        List<Order> slowOrders = new ArrayList<>();
        List<Order> fastOrders = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            slowOrders.add(new Order((long) (i * 7 % 1_000)));
            fastOrders.add(new Order((long) (i * 7 % 1_000)));
        }

        long comparisons = 0;
        long slowMs;
        long start = System.nanoTime();
        for (Order o : slowOrders) {
            for (Member m : members) {
                comparisons++;
                if (m.id().equals(o.memberId())) {
                    o.setMemberName(m.name());
                }
            }
        }
        slowMs = (System.nanoTime() - start) / 1_000_000;

        long gets = 0;
        start = System.nanoTime();
        Map<Long, Member> byId = members.stream()
                .collect(Collectors.toMap(Member::id, m -> m));
        for (Order o : fastOrders) {
            gets++;
            Member m = byId.get(o.memberId());
            o.setMemberName(m.name());
        }
        long fastMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(comparisons).isEqualTo(10_000_000L);
        assertThat(gets).isEqualTo(10_000L);
        for (int i = 0; i < slowOrders.size(); i++) {
            assertThat(fastOrders.get(i).memberName()).isEqualTo(slowOrders.get(i).memberName());
        }
        CiOutput.record(LESSON, "match", """
                주문 10,000건, 회원 1,000명
                matchSlow: 비교 %,d번, %,dms
                matchFast: get %,d번, %,dms
                두 결과의 회원 이름이 모두 같다: true""".formatted(comparisons, slowMs, gets, fastMs));
    }

    @Test
    @DisplayName("Collectors.toMap은 키가 겹치면 IllegalStateException을 던지고, 합치는 방법을 주면 던지지 않는다")
    void toMapDuplicateKey() {
        List<Member> dup = List.of(
                new Member(1L, "a@shop.com", "가"),
                new Member(1L, "b@shop.com", "나"));

        assertThatThrownBy(() -> dup.stream().collect(Collectors.toMap(Member::id, Function.identity())))
                .isInstanceOf(IllegalStateException.class);
        Map<Long, Member> merged = dup.stream()
                .collect(Collectors.toMap(Member::id, Function.identity(), (a, b) -> a));
        assertThat(merged).hasSize(1);
        assertThat(merged.get(1L).name()).isEqualTo("가");

        String message = null;
        try {
            dup.stream().collect(Collectors.toMap(Member::id, Function.identity()));
        } catch (IllegalStateException e) {
            message = e.getMessage();
        }
        CiOutput.record(LESSON, "tomap", "IllegalStateException: " + message);
    }

    @Test
    @DisplayName("상품 n개를 List.contains로 보면 O(n×m), HashSet으로 바꾸면 결과는 같다")
    void soldOutListVsSet() {
        List<Long> soldOutList = new ArrayList<>();
        for (long i = 0; i < 1_000; i += 3) {
            soldOutList.add(i);
        }
        Set<Long> soldOutSet = new HashSet<>(soldOutList);
        int byList = 0;
        int bySet = 0;
        for (long id = 0; id < 1_000; id++) {
            if (soldOutList.contains(id)) {
                byList++;
            }
            if (soldOutSet.contains(id)) {
                bySet++;
            }
        }
        assertThat(byList).isEqualTo(bySet).isEqualTo(334);
    }
}
