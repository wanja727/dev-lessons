package lab.devlessons.contextswitch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLongArray;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "컨텍스트 스위칭"(C2)의 주장을 검증한다.
 * 시간은 기계마다 달라서 크기 비교만 단정하고, 잰 값은 CiOutput에 남긴다.
 */
class ContextSwitchingTest {

    private static final String LESSON = "context-switching";
    private static final int CORES = Runtime.getRuntime().availableProcessors();

    @Test
    @DisplayName("availableProcessors()는 이 기계의 코어 수를 돌려준다")
    void cores() {
        assertThat(CORES).isGreaterThanOrEqualTo(1);
        CiOutput.record(LESSON, "cores", "availableProcessors() → " + CORES);
    }

    @Test
    @DisplayName("코어보다 많은 계산 스레드도 모두 조금씩 진행한다")
    void allThreadsProgress() throws Exception {
        int n = CORES * 4;
        AtomicLongArray progress = new AtomicLongArray(n);
        AtomicBoolean running = new AtomicBoolean(true);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int id = i;
            Thread t = new Thread(() -> {
                while (running.get()) {
                    progress.incrementAndGet(id);
                }
            });
            t.setDaemon(true);
            threads.add(t);
            t.start();
        }
        // 기계가 바빠도 흔들리지 않게, 모든 스레드가 한 번이라도 세기를 10초까지 기다린다
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        Thread.sleep(300);
        while (minOf(progress, n) == 0 && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        running.set(false);
        for (Thread t : threads) {
            t.join();
        }
        long min = minOf(progress, n);
        assertThat(min).isGreaterThan(0);
        CiOutput.record(LESSON, "progress", "코어 " + CORES + "개, 계산 스레드 " + n + "개를 함께 돌림\n"
                + "가장 적게 센 스레드도 → " + String.format("%,d", min) + "번 셌다");
    }

    static long minOf(AtomicLongArray a, int n) {
        long min = Long.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            min = Math.min(min, a.get(i));
        }
        return min;
    }

    @Test
    @DisplayName("두 스레드가 값을 10만 번 주고받는 시간은 한 스레드가 같은 일을 하는 시간보다 길다")
    void pingPongCostsMore() throws Exception {
        int rounds = 100_000;
        SynchronousQueue<Integer> ping = new SynchronousQueue<>();
        SynchronousQueue<Integer> pong = new SynchronousQueue<>();
        Thread b = new Thread(() -> {
            try {
                for (int i = 0; i < rounds; i++) {
                    pong.put(ping.take() + 1);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        b.setDaemon(true);
        b.start();
        long t0 = System.nanoTime();
        long sum = 0;
        for (int i = 0; i < rounds; i++) {
            ping.put(i);
            sum += pong.take();
        }
        long twoThreads = System.nanoTime() - t0;
        b.join();

        long t1 = System.nanoTime();
        long sum2 = 0;
        for (int i = 0; i < rounds; i++) {
            sum2 += plusOne(i);
        }
        long oneThread = System.nanoTime() - t1;

        assertThat(sum).isEqualTo(sum2);
        assertThat(twoThreads).isGreaterThan(oneThread);
        CiOutput.record(LESSON, "ping-pong", "10만 번 주고받기(스레드 둘) → " + ms(twoThreads)
                + "\n같은 10만 번을 한 스레드에서 → " + ms(oneThread)
                + "\n주고받기 한 번에 약 " + String.format("%.1f", twoThreads / 1000.0 / rounds) + "µs");
    }

    static int plusOne(int i) {
        return i + 1;
    }

    static long work() {
        long x = 0;
        for (int i = 0; i < 50_000_000; i++) {
            x += i % 7;
        }
        return x;
    }

    static long runTasks(int threads, int tasks, java.util.concurrent.Callable<Long> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            long t0 = System.nanoTime();
            List<Future<Long>> fs = new ArrayList<>();
            for (int i = 0; i < tasks; i++) {
                fs.add(pool.submit(task));
            }
            for (Future<Long> f : fs) {
                f.get();
            }
            return System.nanoTime() - t0;
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("기다리는 작업 16개는 스레드 1개 풀에서 1.6초 넘게 걸리고, 스레드 16개 풀에서는 그 절반도 안 걸린다")
    void waitingTasksScaleWithThreads() throws Exception {
        java.util.concurrent.Callable<Long> io = () -> {
            Thread.sleep(100);
            return 0L;
        };
        long one = runTasks(1, 16, io);
        long sixteen = runTasks(16, 16, io);
        // 100ms를 하나씩 16번 기다리니 1.6초보다 짧을 수 없다
        assertThat(one).isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(1600));
        assertThat(sixteen * 2).isLessThan(one);
        CiOutput.record(LESSON, "io", "100ms 기다리는 작업 16개\n스레드 1개 → " + ms(one) + "\n스레드 16개 → " + ms(sixteen));
    }

    @Test
    @DisplayName("계산 작업은 코어 수를 넘는 스레드에서 코어 수의 스레드보다 크게 빨라지지 않는다")
    void cpuTasksDoNotScalePastCores() throws Exception {
        java.util.concurrent.Callable<Long> cpu = ContextSwitchingTest::work;
        runTasks(CORES, CORES, cpu); // JIT 데우기
        int tasks = 8;
        long one = runTasks(1, tasks, cpu);
        long cores = runTasks(CORES, tasks, cpu);
        long many = runTasks(CORES * 16, tasks, cpu);
        // 작업이 8개뿐이라 스레드 16배 풀도 동시에 도는 것은 8개. 코어 수보다 크게 빨라질 수 없다
        assertThat(many * 1.5).isGreaterThan(cores);
        CiOutput.record(LESSON, "cpu", "계산 작업 8개 (코어 " + CORES + "개)\n스레드 1개 → " + ms(one)
                + "\n스레드 " + CORES + "개 → " + ms(cores) + "\n스레드 " + (CORES * 16) + "개 → " + ms(many));
    }

    static String ms(long nanos) {
        return String.format("%,.0fms", nanos / 1_000_000.0);
    }
}
