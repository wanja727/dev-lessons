package lab.devlessons.shortestpath;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "최단 경로"(K14)의 주장을 검증한다. 물류 센터 다섯 곳의 도로망으로
 * 너비 우선 탐색과 다익스트라를 비교하고, 확정 순서와 거리표를 출력한다.
 * 걸린 시간은 환경마다 달라서 남기기만 하고, 결과가 같은 것만 단정한다.
 */
class ShortestPathTest {

    private static final String LESSON = "shortest-path";

    record Edge(String to, int minutes) {
    }

    record Entry(String node, int dist) {
    }

    static Map<String, List<Edge>> graph(boolean directed, String... roads) {
        Map<String, List<Edge>> g = new LinkedHashMap<>();
        for (String road : roads) {
            String[] p = road.split(" ");
            g.computeIfAbsent(p[0], k -> new ArrayList<>()).add(new Edge(p[1], Integer.parseInt(p[2])));
            g.computeIfAbsent(p[1], k -> new ArrayList<>());
            if (!directed) {
                g.get(p[1]).add(new Edge(p[0], Integer.parseInt(p[2])));
            }
        }
        return g;
    }

    /** A-B 4, A-C 1, C-B 2, B-D 1, C-D 5, D-E 3 */
    static Map<String, List<Edge>> centers() {
        return graph(false, "A B 4", "A C 1", "C B 2", "B D 1", "C D 5", "D E 3");
    }

    static final class Result {
        final Map<String, Integer> dist = new LinkedHashMap<>();
        final Map<String, String> prev = new HashMap<>();
        final List<String> order = new ArrayList<>();
        int stale;
    }

    static Result dijkstra(Map<String, List<Edge>> graph, String start) {
        Result r = new Result();
        Map<String, Integer> dist = r.dist;
        Set<String> done = new HashSet<>();
        PriorityQueue<Entry> pq = new PriorityQueue<>((a, b) -> Integer.compare(a.dist(), b.dist()));
        dist.put(start, 0);
        pq.add(new Entry(start, 0));
        while (!pq.isEmpty()) {
            Entry e = pq.poll();
            if (!done.add(e.node())) {
                r.stale++;
                continue;
            }
            r.order.add(e.node() + " " + e.dist());
            for (Edge ed : graph.get(e.node())) {
                int nd = e.dist() + ed.minutes();
                if (nd < dist.getOrDefault(ed.to(), Integer.MAX_VALUE)) {
                    dist.put(ed.to(), nd);
                    r.prev.put(ed.to(), e.node());
                    pq.add(new Entry(ed.to(), nd));
                }
            }
        }
        return r;
    }

    /** 흔한 실수: 너비 우선 탐색처럼 넣을 때 확정 표시를 한다 */
    static Map<String, Integer> dijkstraMarkOnPush(Map<String, List<Edge>> graph, String start) {
        Map<String, Integer> dist = new LinkedHashMap<>();
        Set<String> done = new HashSet<>();
        PriorityQueue<Entry> pq = new PriorityQueue<>((a, b) -> Integer.compare(a.dist(), b.dist()));
        dist.put(start, 0);
        done.add(start);
        pq.add(new Entry(start, 0));
        while (!pq.isEmpty()) {
            Entry e = pq.poll();
            for (Edge ed : graph.get(e.node())) {
                int nd = e.dist() + ed.minutes();
                if (nd < dist.getOrDefault(ed.to(), Integer.MAX_VALUE)) {
                    dist.put(ed.to(), nd);
                    if (done.add(ed.to())) {
                        pq.add(new Entry(ed.to(), nd));
                    }
                }
            }
        }
        return dist;
    }

    static List<String> path(Map<String, String> prev, String to) {
        List<String> path = new ArrayList<>();
        for (String at = to; at != null; at = prev.get(at)) {
            path.add(0, at);
        }
        return path;
    }

    static int pathMinutes(Map<String, List<Edge>> graph, List<String> path) {
        int total = 0;
        for (int i = 0; i + 1 < path.size(); i++) {
            String from = path.get(i);
            String to = path.get(i + 1);
            total += graph.get(from).stream().filter(e -> e.to().equals(to)).findFirst().orElseThrow().minutes();
        }
        return total;
    }

    @Test
    @DisplayName("너비 우선 탐색이 처음 닿는 D까지의 길은 선 2개이고 5분이다. 다익스트라는 4분(A-C-B-D)을 낸다")
    void bfsVersusDijkstra() {
        Map<String, List<Edge>> g = centers();
        Map<String, Integer> hops = new HashMap<>();
        Map<String, String> bfsPrev = new HashMap<>();
        Deque<String> q = new ArrayDeque<>();
        hops.put("A", 0);
        q.add("A");
        while (!q.isEmpty()) {
            String cur = q.poll();
            for (Edge ed : g.get(cur)) {
                if (!hops.containsKey(ed.to())) {
                    hops.put(ed.to(), hops.get(cur) + 1);
                    bfsPrev.put(ed.to(), cur);
                    q.add(ed.to());
                }
            }
        }
        List<String> bfsPath = path(bfsPrev, "D");
        Result r = dijkstra(g, "A");
        List<String> best = path(r.prev, "D");

        assertThat(hops.get("D")).isEqualTo(2);
        assertThat(bfsPath).containsExactly("A", "B", "D");
        assertThat(pathMinutes(g, bfsPath)).isEqualTo(5);
        assertThat(pathMinutes(g, List.of("A", "C", "D"))).isEqualTo(6);
        assertThat(r.dist.get("D")).isEqualTo(4);
        assertThat(best).containsExactly("A", "C", "B", "D");

        CiOutput.record(LESSON, "predict", """
                너비 우선 탐색이 찾은 길: %s
                  선 %d개, %d분
                다익스트라가 찾은 길: %s
                  선 %d개, %d분""".formatted(String.join("-", bfsPath), bfsPath.size() - 1,
                pathMinutes(g, bfsPath), String.join("-", best), best.size() - 1, r.dist.get("D")));
    }

    @Test
    @DisplayName("다익스트라는 A, C, B, D, E 순서로 확정하고 거리는 A 0, C 1, B 3, D 4, E 7이다. 낡은 항목 2개를 건너뛴다")
    void dijkstraOrder() {
        Result r = dijkstra(centers(), "A");
        assertThat(r.order).containsExactly("A 0", "C 1", "B 3", "D 4", "E 7");
        assertThat(r.dist).containsEntry("A", 0).containsEntry("C", 1).containsEntry("B", 3)
                .containsEntry("D", 4).containsEntry("E", 7);
        assertThat(path(r.prev, "D")).containsExactly("A", "C", "B", "D");
        assertThat(path(r.prev, "E")).containsExactly("A", "C", "B", "D", "E");
        assertThat(r.prev).containsEntry("B", "C").containsEntry("C", "A").containsEntry("D", "B")
                .containsEntry("E", "D");
        // B는 4로 넣었다가 3으로, D는 6으로 넣었다가 4로 다시 넣었다
        assertThat(r.stale).isEqualTo(2);

        CiOutput.record(LESSON, "order", """
                확정한 순서 (점 거리): %s
                최종 거리: A 0, B %d, C %d, D %d, E %d
                prev: B←%s, C←%s, D←%s, E←%s
                D까지의 길: %s
                건너뛴 낡은 항목: %d개""".formatted(String.join(", ", r.order),
                r.dist.get("B"), r.dist.get("C"), r.dist.get("D"), r.dist.get("E"),
                r.prev.get("B"), r.prev.get("C"), r.prev.get("D"), r.prev.get("E"),
                String.join("-", path(r.prev, "D")), r.stale));
    }

    static Map<String, Integer> bellmanFord(Map<String, List<Edge>> g, String start) {
        Map<String, Integer> dist = new LinkedHashMap<>();
        for (String v : g.keySet()) {
            dist.put(v, Integer.MAX_VALUE);
        }
        dist.put(start, 0);
        for (int i = 0; i < g.size() - 1; i++) {
            for (var e : g.entrySet()) {
                int du = dist.get(e.getKey());
                if (du == Integer.MAX_VALUE) {
                    continue;
                }
                for (Edge ed : e.getValue()) {
                    if (du + ed.minutes() < dist.get(ed.to())) {
                        dist.put(ed.to(), du + ed.minutes());
                    }
                }
            }
        }
        return dist;
    }

    @Test
    @DisplayName("음수 가중치(A→B 2, B→D 1, A→C 3, C→B -2)에서 다익스트라는 D를 3으로, 벨만 포드는 2로 낸다")
    void negativeWeight() {
        Map<String, List<Edge>> g = graph(true, "A B 2", "B D 1", "A C 3", "C B -2");
        Result r = dijkstra(g, "A");
        Map<String, Integer> bf = bellmanFord(g, "A");

        // C와 D는 둘 다 거리 3이라 어느 쪽을 먼저 꺼낼지 정해지지 않는다. B는 C보다 먼저 확정된다
        assertThat(r.order.subList(0, 2)).containsExactly("A 0", "B 2");
        assertThat(r.order.subList(2, 4)).containsExactlyInAnyOrder("C 3", "D 3");
        assertThat(r.dist.get("D")).isEqualTo(3);
        assertThat(bf.get("D")).isEqualTo(2);
        assertThat(bf.get("B")).isEqualTo(1);

        CiOutput.record(LESSON, "negative", """
                방향이 있는 도로: A→B 2, B→D 1, A→C 3, C→B -2
                다익스트라가 확정한 순서: %s
                다익스트라의 D: %d
                벨만 포드의 D: %d (A→C→B→D = 3 - 2 + 1)""".formatted(
                String.join(", ", r.order), r.dist.get("D"), bf.get("D")));
    }

    @Test
    @DisplayName("넣을 때 확정 표시를 하면 D까지 5를 낸다. 꺼낼 때 확정하면 4다")
    void markOnPushIsWrong() {
        Map<String, Integer> wrong = dijkstraMarkOnPush(centers(), "A");
        Result right = dijkstra(centers(), "A");
        assertThat(wrong.get("D")).isEqualTo(5);
        assertThat(right.dist.get("D")).isEqualTo(4);
        CiOutput.record(LESSON, "mark", """
                넣을 때 확정: A→D %d분, A→E %d분
                꺼낼 때 확정: A→D %d분, A→E %d분""".formatted(
                wrong.get("D"), wrong.get("E"), right.dist.get("D"), right.dist.get("E")));
    }

    // ── 배열 다익스트라와 우선순위 큐 다익스트라 ──

    static int[][][] randomGraph(int v, int e, long seed) {
        Random rnd = new Random(seed);
        List<List<int[]>> adj = new ArrayList<>();
        for (int i = 0; i < v; i++) {
            adj.add(new ArrayList<>());
        }
        // 모든 점이 이어지게 먼저 한 줄로 잇는다
        for (int i = 1; i < v; i++) {
            int w = 1 + rnd.nextInt(100);
            adj.get(i - 1).add(new int[] {i, w});
            adj.get(i).add(new int[] {i - 1, w});
        }
        for (int i = v - 1; i < e; i++) {
            int a = rnd.nextInt(v);
            int b = rnd.nextInt(v);
            int w = 1 + rnd.nextInt(100);
            adj.get(a).add(new int[] {b, w});
            adj.get(b).add(new int[] {a, w});
        }
        int[][][] out = new int[v][][];
        for (int i = 0; i < v; i++) {
            out[i] = adj.get(i).toArray(new int[0][]);
        }
        return out;
    }

    static int[] arrayDijkstra(int[][][] g, int start) {
        int n = g.length;
        int[] dist = new int[n];
        boolean[] done = new boolean[n];
        Arrays.fill(dist, Integer.MAX_VALUE);
        dist[start] = 0;
        for (int round = 0; round < n; round++) {
            int u = -1;
            for (int i = 0; i < n; i++) {
                if (!done[i] && dist[i] != Integer.MAX_VALUE && (u < 0 || dist[i] < dist[u])) {
                    u = i;
                }
            }
            if (u < 0) {
                break;
            }
            done[u] = true;
            for (int[] ed : g[u]) {
                if (dist[u] + ed[1] < dist[ed[0]]) {
                    dist[ed[0]] = dist[u] + ed[1];
                }
            }
        }
        return dist;
    }

    static int[] heapDijkstra(int[][][] g, int start) {
        int n = g.length;
        int[] dist = new int[n];
        boolean[] done = new boolean[n];
        Arrays.fill(dist, Integer.MAX_VALUE);
        dist[start] = 0;
        PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> Integer.compare(a[1], b[1]));
        pq.add(new int[] {start, 0});
        while (!pq.isEmpty()) {
            int[] e = pq.poll();
            if (done[e[0]]) {
                continue;
            }
            done[e[0]] = true;
            for (int[] ed : g[e[0]]) {
                int nd = e[1] + ed[1];
                if (nd < dist[ed[0]]) {
                    dist[ed[0]] = nd;
                    pq.add(new int[] {ed[0], nd});
                }
            }
        }
        return dist;
    }

    @Test
    @DisplayName("점 2만 개, 선 10만 개에서 배열 다익스트라와 우선순위 큐 다익스트라의 거리표가 같고, 우선순위 큐 쪽이 빨리 끝난다")
    void arrayVersusHeap() {
        int[][][] g = randomGraph(20_000, 100_000, 7);
        for (int i = 0; i < 3; i++) {
            heapDijkstra(randomGraph(2_000, 10_000, i), 0);
            arrayDijkstra(randomGraph(2_000, 10_000, i), 0);
        }
        long start = System.nanoTime();
        int[] byArray = arrayDijkstra(g, 0);
        long arrayMs = (System.nanoTime() - start) / 1_000_000;
        start = System.nanoTime();
        int[] byHeap = heapDijkstra(g, 0);
        long heapMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(byHeap).isEqualTo(byArray);
        assertThat(heapMs).isLessThan(arrayMs);
        CiOutput.record(LESSON, "speed", """
                점 20,000개, 선 100,000개
                배열에서 가장 작은 점 찾기: %,dms
                우선순위 큐에서 꺼내기:     %,dms
                두 거리표가 같다: true""".formatted(arrayMs, heapMs));
    }
}
