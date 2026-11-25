package lab.devlessons.graphsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 레슨 "그래프와 탐색"(K11)의 주장을 검증한다. 순수 JUnit 테스트와 작은 스프링 컨텍스트.
 */
class GraphSearchTest {

    private static final String LESSON = "graph-search";

    static final Map<Integer, List<Integer>> G = Map.of(
            1, List.of(2, 3),
            2, List.of(1, 4, 5),
            3, List.of(1, 6),
            4, List.of(2),
            5, List.of(2, 7),
            6, List.of(3),
            7, List.of(5));

    /** 레슨의 코드 bfs */
    static List<Integer> bfs(Map<Integer, List<Integer>> g, int start) {
        List<Integer> order = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        Queue<Integer> q = new ArrayDeque<>();
        q.offer(start);
        seen.add(start);
        while (!q.isEmpty()) {
            int v = q.poll();
            order.add(v);
            for (int w : g.get(v)) {
                if (seen.add(w)) {
                    q.offer(w);
                }
            }
        }
        return order;
    }

    /** 레슨의 코드 dfs */
    static void dfs(Map<Integer, List<Integer>> g, int v, Set<Integer> seen, List<Integer> order) {
        seen.add(v);
        order.add(v);
        for (int w : g.get(v)) {
            if (!seen.contains(w)) {
                dfs(g, w, seen, order);
            }
        }
    }

    static List<Integer> dfs(Map<Integer, List<Integer>> g, int start) {
        List<Integer> order = new ArrayList<>();
        dfs(g, start, new HashSet<>(), order);
        return order;
    }

    @Test
    @DisplayName("1부터 너비 우선은 1, 2, 3, 4, 5, 6, 7, 깊이 우선은 1, 2, 4, 5, 7, 3, 6")
    void orders() {
        assertThat(bfs(G, 1)).containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(dfs(G, 1)).containsExactly(1, 2, 4, 5, 7, 3, 6);
        assertThat(bfs(G, 3)).containsExactly(3, 1, 6, 2, 4, 5, 7);
        assertThat(dfs(G, 3)).containsExactly(3, 1, 2, 4, 5, 7, 6);

        int edges = G.values().stream().mapToInt(List::size).sum() / 2;
        assertThat(edges).isEqualTo(6);

        CiOutput.record(LESSON, "predict", """
                1부터 너비 우선: %s
                1부터 깊이 우선: %s""".formatted(bfs(G, 1), dfs(G, 1)));
        CiOutput.record(LESSON, "from3", """
                3부터 너비 우선: %s
                3부터 깊이 우선: %s""".formatted(bfs(G, 3), dfs(G, 3)));
    }

    @Test
    @DisplayName("방문 표시가 없는 너비 우선은 끝나지 않는다")
    void noSeenNeverEnds() {
        Queue<Integer> q = new ArrayDeque<>();
        q.offer(1);
        int polled = 0;
        while (!q.isEmpty() && polled <= 10_000) {
            int v = q.poll();
            polled++;
            for (int w : G.get(v)) q.offer(w);
        }
        assertThat(polled).isGreaterThan(10_000);
        CiOutput.record(LESSON, "noSeen", "seen 없이 돌린 너비 우선: 꺼낸 횟수가 1만을 넘어 멈춤. 그때 큐에 남은 점 " + q.size() + "개");
    }

    static Map<Integer, Integer> distances(Map<Integer, List<Integer>> g, int start) {
        Map<Integer, Integer> dist = new HashMap<>();
        Queue<Integer> q = new ArrayDeque<>();
        q.offer(start);
        dist.put(start, 0);
        while (!q.isEmpty()) {
            int v = q.poll();
            for (int w : g.get(v)) {
                if (!dist.containsKey(w)) {
                    dist.put(w, dist.get(v) + 1);
                    q.offer(w);
                }
            }
        }
        return dist;
    }

    @Test
    @DisplayName("1에서 6까지 2, 7까지 3, 4에서 6까지 4")
    void shortestDistance() {
        Map<Integer, Integer> from1 = distances(G, 1);
        assertThat(from1.get(6)).isEqualTo(2);
        assertThat(from1.get(7)).isEqualTo(3);
        assertThat(distances(G, 4).get(6)).isEqualTo(4);
        CiOutput.record(LESSON, "distance", "1번에서의 거리: " + new java.util.TreeMap<>(from1));
    }

    @Test
    @DisplayName("한 줄로 이어진 점 10만 개에서 재귀 깊이 우선은 StackOverflowError, 스택 버전은 모두 방문한다")
    void deepLine() {
        int n = 100_000;
        Map<Integer, List<Integer>> line = new HashMap<>();
        for (int i = 1; i <= n; i++) {
            List<Integer> next = new ArrayList<>();
            if (i > 1) next.add(i - 1);
            if (i < n) next.add(i + 1);
            line.put(i, next);
        }
        assertThatThrownBy(() -> dfs(line, 1)).isInstanceOf(StackOverflowError.class);

        Set<Integer> seen = new HashSet<>();
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(1);
        int visited = 0;
        while (!stack.isEmpty()) {
            int v = stack.pop();
            if (!seen.add(v)) continue;
            visited++;
            for (int w : line.get(v)) {
                if (!seen.contains(w)) stack.push(w);
            }
        }
        assertThat(visited).isEqualTo(n);
        CiOutput.record(LESSON, "deep", """
                점 10만 개가 한 줄로 이어진 그래프
                재귀 깊이 우선 → StackOverflowError
                ArrayDeque를 스택으로 쓴 깊이 우선 → %d개 방문""".formatted(visited));
    }

    @Test
    @DisplayName("모두 서로 친구인 1천 명에서 넣을 때 표시하면 offer 1000번, 꺼낼 때 표시하면 훨씬 많다")
    void markTiming() {
        int n = 1000;
        Map<Integer, List<Integer>> all = new HashMap<>();
        for (int i = 1; i <= n; i++) {
            List<Integer> others = new ArrayList<>();
            for (int j = 1; j <= n; j++) if (j != i) others.add(j);
            all.put(i, others);
        }
        // 넣을 때 표시 (코드 bfs)
        int early = 0;
        List<Integer> earlyOrder = new ArrayList<>();
        {
            Set<Integer> seen = new HashSet<>();
            Queue<Integer> q = new ArrayDeque<>();
            q.offer(1);
            early++;
            seen.add(1);
            while (!q.isEmpty()) {
                int v = q.poll();
                earlyOrder.add(v);
                for (int w : all.get(v)) {
                    if (seen.add(w)) {
                        q.offer(w);
                        early++;
                    }
                }
            }
        }
        // 꺼낼 때 표시 (코드 lateMark)
        int late = 0;
        int maxQueue = 0;
        List<Integer> lateOrder = new ArrayList<>();
        {
            Set<Integer> seen = new HashSet<>();
            Queue<Integer> q = new ArrayDeque<>();
            q.offer(1);
            late++;
            while (!q.isEmpty()) {
                maxQueue = Math.max(maxQueue, q.size());
                int v = q.poll();
                if (!seen.add(v)) continue;
                lateOrder.add(v);
                for (int w : all.get(v)) {
                    if (!seen.contains(w)) {
                        q.offer(w);
                        late++;
                    }
                }
            }
        }
        assertThat(early).isEqualTo(1000);
        assertThat(late).isGreaterThan(100_000);
        assertThat(lateOrder).isEqualTo(earlyOrder);

        // 예제 그래프에서도 순서는 같다
        List<Integer> small = new ArrayList<>();
        {
            Set<Integer> seen = new HashSet<>();
            Queue<Integer> q = new ArrayDeque<>();
            q.offer(1);
            while (!q.isEmpty()) {
                int v = q.poll();
                if (!seen.add(v)) continue;
                small.add(v);
                for (int w : G.get(v)) if (!seen.contains(w)) q.offer(w);
            }
        }
        assertThat(small).containsExactly(1, 2, 3, 4, 5, 6, 7);

        CiOutput.record(LESSON, "lateMark", """
                모두 서로 친구인 회원 1천 명, 1번부터 너비 우선
                넣을 때 표시: offer %d번
                꺼낼 때 표시: offer %d번, 큐가 가장 길 때 %d개
                방문 순서가 같은가: %s""".formatted(early, late, maxQueue, lateOrder.equals(earlyOrder)));
    }

    /** 방향 그래프의 순환을 찾아 그 길을 돌려준다. 없으면 빈 리스트 */
    static List<String> findCycle(Map<String, List<String>> deps) {
        Set<String> done = new HashSet<>();
        for (String start : new java.util.TreeSet<>(deps.keySet())) {
            List<String> found = visit(deps, start, new LinkedHashSet<>(), done);
            if (!found.isEmpty()) return found;
        }
        return List.of();
    }

    static List<String> visit(Map<String, List<String>> deps, String v, LinkedHashSet<String> path, Set<String> done) {
        if (path.contains(v)) {
            List<String> cycle = new ArrayList<>();
            boolean in = false;
            for (String p : path) {
                if (p.equals(v)) in = true;
                if (in) cycle.add(p);
            }
            cycle.add(v);
            return cycle;
        }
        if (done.contains(v)) return List.of();
        path.add(v);
        for (String w : deps.getOrDefault(v, List.of())) {
            List<String> found = visit(deps, w, path, done);
            if (!found.isEmpty()) return found;
        }
        path.remove(v);
        done.add(v);
        return List.of();
    }

    @Test
    @DisplayName("의존 관계에서 order, member, coupon, order의 순환을 찾는다")
    void dependencyCycle() {
        Map<String, List<String>> deps = Map.of(
                "order", List.of("member", "payment"),
                "payment", List.of("member"),
                "member", List.of("coupon"),
                "coupon", List.of("order"));
        List<String> cycle = findCycle(deps);
        assertThat(cycle).containsExactly("coupon", "order", "member", "coupon");

        Map<String, List<String>> fixed = Map.of(
                "order", List.of("member", "payment"),
                "payment", List.of("member"),
                "member", List.of("coupon"),
                "coupon", List.of());
        assertThat(findCycle(fixed)).isEmpty();

        // 이미 다 본 점을 다시 만나는 것은 순환이 아니다: payment → member는 order → member 뒤에 다시 만난다
        Map<String, List<String>> diamond = Map.of(
                "order", List.of("member", "payment"),
                "payment", List.of("member"),
                "member", List.of());
        assertThat(findCycle(diamond)).isEmpty();

        CiOutput.record(LESSON, "cycle", """
                찾은 순환: %s
                coupon의 의존을 지운 뒤: %s""".formatted(String.join(" → ", cycle), findCycle(fixed)));
    }

    static class OrderService {
        OrderService(MemberService members) {
        }
    }

    static class MemberService {
        MemberService(OrderService orders) {
        }
    }

    @Test
    @DisplayName("서로를 생성자로 받는 두 빈을 띄우면 BeanCurrentlyInCreationException이 원인으로 나온다")
    void springCircular() {
        Throwable thrown = null;
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(OrderService.class);
            ctx.registerBean(MemberService.class);
            ctx.refresh();
        } catch (Exception e) {
            thrown = e;
        }
        assertThat(thrown).isNotNull();
        Throwable root = thrown;
        while (root.getCause() != null) root = root.getCause();
        assertThat(root).isInstanceOf(BeanCurrentlyInCreationException.class);
        CiOutput.record(LESSON, "spring", root.getClass().getSimpleName() + ": " + root.getMessage());
    }
}
