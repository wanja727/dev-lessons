package lab.devlessons.heap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 레슨 "힙과 우선순위 큐"(K9)의 주장을 검증한다. 순수 JUnit 테스트와 스프링의 ThreadPoolTaskScheduler.
 */
class HeapPriorityQueueTest {

    private static final String LESSON = "heap-priority-queue";

    @Test
    @DisplayName("5, 1, 4, 2, 3을 넣으면 배열은 [1, 2, 4, 5, 3]이고 poll은 1")
    void predict() {
        Queue<Integer> pq = new PriorityQueue<>();
        StringBuilder steps = new StringBuilder();
        for (int x : new int[] {5, 1, 4, 2, 3}) {
            pq.offer(x);
            steps.append("offer(").append(x).append(") → ").append(pq).append('\n');
        }
        String printed = pq.toString();
        int first = pq.poll();
        String afterPoll = pq.toString();

        assertThat(printed).isEqualTo("[1, 2, 4, 5, 3]");
        assertThat(first).isEqualTo(1);
        assertThat(afterPoll).isEqualTo("[2, 3, 4, 5]");

        List<Integer> rest = new ArrayList<>();
        while (!pq.isEmpty()) rest.add(pq.poll());
        assertThat(rest).containsExactly(2, 3, 4, 5);

        CiOutput.record(LESSON, "predict", printed + "\n" + first);
        CiOutput.record(LESSON, "steps", steps + "poll() → 1, 남은 배열 " + afterPoll);
        CiOutput.record(LESSON, "drain", "출력: " + printed + "\npoll을 되풀이한 순서: [1, " + rest.toString().substring(1));
    }

    @Test
    @DisplayName("[1, 2, 4, 5, 3]에 0을 넣으면 두 번 올라가 0번 칸")
    void offerZero() {
        PriorityQueue<Integer> pq = new PriorityQueue<>(List.of());
        for (int x : new int[] {5, 1, 4, 2, 3}) pq.offer(x);
        pq.offer(0);
        assertThat(pq.toString()).isEqualTo("[0, 2, 1, 5, 3, 4]");

        MinHeap h = new MinHeap();
        for (int x : new int[] {1, 2, 4, 5, 3}) h.offer(x);
        int before = h.swaps;
        h.offer(0);
        assertThat(h.swaps - before).isEqualTo(2);
        assertThat(h.peek()).isZero();
    }

    @Test
    @DisplayName("PriorityQueue의 배열은 언제나 부모가 자식보다 작거나 같다")
    void heapInvariant() {
        Random random = new Random(1);
        PriorityQueue<Integer> pq = new PriorityQueue<>();
        for (int round = 0; round < 5000; round++) {
            if (random.nextInt(3) == 0 && !pq.isEmpty()) pq.poll();
            else pq.offer(random.nextInt(1000));
            Object[] a = pq.toArray();
            for (int i = 1; i < a.length; i++) {
                assertThat((Integer) a[(i - 1) / 2]).isLessThanOrEqualTo((Integer) a[i]);
            }
        }
        // 옮긴 문제의 배열
        int[] given = {2, 5, 3, 9, 6};
        for (int i = 1; i < given.length; i++) assertThat(given[(i - 1) / 2]).isLessThanOrEqualTo(given[i]);
    }

    /** 레슨의 설명대로 짠 최소 힙. 자리 바꿈을 센다 */
    static class MinHeap {
        int[] a = new int[16];
        int size;
        int swaps;

        void offer(int x) {
            if (size == a.length) a = Arrays.copyOf(a, size * 2);
            int i = size++;
            a[i] = x;
            while (i > 0 && a[(i - 1) / 2] > a[i]) {
                swap(i, (i - 1) / 2);
                i = (i - 1) / 2;
            }
        }

        int poll() {
            int top = a[0];
            a[0] = a[--size];
            int i = 0;
            while (true) {
                int l = 2 * i + 1;
                int r = 2 * i + 2;
                int min = i;
                if (l < size && a[l] < a[min]) min = l;
                if (r < size && a[r] < a[min]) min = r;
                if (min == i) break;
                swap(i, min);
                i = min;
            }
            return top;
        }

        int peek() {
            return a[0];
        }

        void swap(int i, int j) {
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
            swaps++;
        }
    }

    @Test
    @DisplayName("원소 100만 개의 힙에서 넣기와 꺼내기 한 번의 자리 바꿈은 20번을 넘지 않는다")
    void swapsBoundedByHeight() {
        Random random = new Random(3);
        MinHeap h = new MinHeap();
        int maxOffer = 0;
        for (int i = 0; i < 1_000_000; i++) {
            int before = h.swaps;
            h.offer(random.nextInt());
            maxOffer = Math.max(maxOffer, h.swaps - before);
        }
        int maxPoll = 0;
        for (int i = 0; i < 1000; i++) {
            int before = h.swaps;
            h.poll();
            maxPoll = Math.max(maxPoll, h.swaps - before);
        }
        // 차례로 커지는 값을 넣어도, 차례로 작아지는 값을 넣어도 높이는 같다
        MinHeap desc = new MinHeap();
        int maxDesc = 0;
        for (int i = 1_000_000; i > 0; i--) {
            int before = desc.swaps;
            desc.offer(i);
            maxDesc = Math.max(maxDesc, desc.swaps - before);
        }

        AtomicInteger count = new AtomicInteger();
        PriorityQueue<Integer> pq = new PriorityQueue<>((x, y) -> {
            count.incrementAndGet();
            return Integer.compare(x, y);
        });
        for (int i = 0; i < 1_000_000; i++) pq.offer(random.nextInt());
        int maxPollCompares = 0;
        for (int i = 0; i < 1000; i++) {
            count.set(0);
            pq.poll();
            maxPollCompares = Math.max(maxPollCompares, count.get());
        }

        assertThat(maxOffer).isLessThanOrEqualTo(20);
        assertThat(maxPoll).isLessThanOrEqualTo(20);
        assertThat(maxDesc).isLessThanOrEqualTo(20);
        assertThat(maxPollCompares).isLessThanOrEqualTo(40);

        CiOutput.record(LESSON, "swaps", """
                원소 100만 개를 넣는 동안 offer 한 번의 자리 바꿈 최대: %d번
                100만 개에서 poll 1천 번, 한 번의 자리 바꿈 최대: %d번
                100만부터 1까지 작아지는 순서로 넣어도 offer의 최대: %d번

                PriorityQueue에서 poll 한 번의 비교 최대: %d번""".formatted(maxOffer, maxPoll, maxDesc, maxPollCompares));
    }

    @Test
    @DisplayName("Comparator.reverseOrder()를 주면 큰 것부터 나온다")
    void reverse() {
        PriorityQueue<Integer> asc = new PriorityQueue<>(List.of(70, 95, 82));
        PriorityQueue<Integer> desc = new PriorityQueue<>(Comparator.reverseOrder());
        desc.addAll(List.of(70, 95, 82));
        assertThat(asc.poll()).isEqualTo(70);
        assertThat(desc.poll()).isEqualTo(95);
    }

    @Test
    @DisplayName("상위 k개를 크기 k의 최소 힙으로 구한 결과는 정렬해서 자른 결과와 같다")
    void topK() {
        // 옮긴 문제: k=3, 7, 2, 9, 4, 8
        Queue<Integer> small = topK(new int[] {7, 2, 9, 4, 8}, 3);
        List<Integer> left = new ArrayList<>(small);
        left.sort(null);
        assertThat(left).containsExactly(7, 8, 9);

        Random random = new Random(11);
        int[] scores = new int[1_000_000];
        for (int i = 0; i < scores.length; i++) scores[i] = random.nextInt(100_000_000);

        // 데우기
        for (int i = 0; i < 3; i++) {
            topK(scores, 10);
            sortedTop(scores, 10);
        }
        long t0 = System.nanoTime();
        Queue<Integer> heap = topK(scores, 10);
        long t1 = System.nanoTime();
        int[] sorted = sortedTop(scores, 10);
        long t2 = System.nanoTime();

        List<Integer> fromHeap = new ArrayList<>(heap);
        fromHeap.sort(Comparator.reverseOrder());
        List<Integer> fromSort = new ArrayList<>();
        for (int s : sorted) fromSort.add(s);
        assertThat(fromHeap).isEqualTo(fromSort);

        CiOutput.record(LESSON, "topk", """
                점수 100만 개에서 상위 10개
                크기 10의 최소 힙: %d ms
                전부 정렬하고 자르기: %d ms
                두 결과가 같은가: %s""".formatted(
                (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, fromHeap.equals(fromSort)));
    }

    static Queue<Integer> topK(int[] scores, int k) {
        Queue<Integer> top = new PriorityQueue<>();
        for (int score : scores) {
            top.offer(score);
            if (top.size() > k) {
                top.poll();
            }
        }
        return top;
    }

    static int[] sortedTop(int[] scores, int k) {
        int[] copy = scores.clone();
        Arrays.sort(copy);
        int[] out = new int[k];
        for (int i = 0; i < k; i++) out[i] = copy[copy.length - 1 - i];
        return out;
    }

    static class Job {
        String name;
        int priority;

        Job(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }
    }

    @Test
    @DisplayName("큐에 든 객체의 우선순위를 바꾸면 옛 자리에 남는다. 꺼내서 바꾸고 다시 넣으면 맞다")
    void mutablePriority() {
        Job a = new Job("a", 5);
        Job b = new Job("b", 3);
        Job c = new Job("c", 8);
        Queue<Job> jobs = new PriorityQueue<>(Comparator.comparingInt((Job j) -> j.priority));
        jobs.offer(a);
        jobs.offer(b);
        jobs.offer(c);
        c.priority = 1;
        String wrong = jobs.poll().name;

        Job a2 = new Job("a", 5);
        Job b2 = new Job("b", 3);
        Job c2 = new Job("c", 8);
        Queue<Job> fixed = new PriorityQueue<>(Comparator.comparingInt((Job j) -> j.priority));
        fixed.offer(a2);
        fixed.offer(b2);
        fixed.offer(c2);
        fixed.remove(c2);
        c2.priority = 1;
        fixed.offer(c2);
        String right = fixed.poll().name;

        assertThat(wrong).isEqualTo("b");
        assertThat(right).isEqualTo("c");
        CiOutput.record(LESSON, "mutable", """
                c.priority를 1로 바꾸고 poll → %s
                remove(c), 바꾸고, offer(c) 뒤 poll → %s""".formatted(wrong, right));
    }

    @Test
    @DisplayName("ScheduledThreadPoolExecutor의 큐는 실행 시각이 가장 이른 작업을 맨 앞에 둔다. 스프링의 스케줄러도 이 실행기를 쓴다")
    void scheduledQueue() throws Exception {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        try {
            executor.schedule(() -> { }, 30, TimeUnit.SECONDS);
            executor.schedule(() -> { }, 10, TimeUnit.SECONDS);
            executor.schedule(() -> { }, 20, TimeUnit.SECONDS);
            Delayed head = (Delayed) executor.getQueue().peek();
            long seconds = Math.round(head.getDelay(TimeUnit.MILLISECONDS) / 1000.0);
            String queueClass = executor.getQueue().getClass().getName();
            assertThat(seconds).isEqualTo(10);
            assertThat(queueClass).endsWith("DelayedWorkQueue");

            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.initialize();
            ScheduledExecutorService inside = scheduler.getScheduledExecutor();
            assertThat(inside).isInstanceOf(ScheduledThreadPoolExecutor.class);
            scheduler.shutdown();

            CiOutput.record(LESSON, "scheduled", """
                    30초, 10초, 20초 뒤에 실행할 작업을 차례로 schedule
                    큐의 맨 앞 작업: %d초 뒤
                    큐의 클래스: %s
                    스프링 ThreadPoolTaskScheduler 안의 실행기가 ScheduledThreadPoolExecutor인가: %s""".formatted(
                    seconds, queueClass, inside instanceof ScheduledThreadPoolExecutor));
        } finally {
            executor.shutdownNow();
        }
    }
}
