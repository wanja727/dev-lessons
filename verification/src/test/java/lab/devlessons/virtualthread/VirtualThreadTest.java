package lab.devlessons.virtualthread;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import lab.devlessons.support.ChildJvm;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "가상 스레드"(C12)의 주장을 검증한다. 고정(pinning)은 캐리어를 하나로 줄인 자식 JVM에서 본다(자바 21).
 */
class VirtualThreadTest {

    private static final String LESSON = "virtual-thread";

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static long ms(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    @Test
    @DisplayName("가상 스레드 안에서 isVirtual()은 true이고, 가상 스레드 10만 개를 만들어 모두 끝낼 수 있다")
    void manyVirtualThreads() throws Exception {
        AtomicReference<String> self = new AtomicReference<>();
        Thread vt = Thread.ofVirtual().start(() -> self.set(Thread.currentThread().isVirtual() + " / " + Thread.currentThread()));
        vt.join();

        AtomicInteger done = new AtomicInteger();
        long t0 = System.nanoTime();
        try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 100_000; i++) {
                ex.submit(() -> {
                    sleep(10);
                    done.incrementAndGet();
                });
            }
        }
        long took = ms(t0);
        assertThat(self.get()).startsWith("true / VirtualThread[");
        assertThat(done.get()).isEqualTo(100_000);
        CiOutput.record(LESSON, "create", "isVirtual() / 스레드 → " + self.get().replaceAll("[0-9]+", "N")
                + "\n10ms 자는 가상 스레드 100,000개 → 모두 끝남 (" + String.format("%,dms", took) + ")");
    }

    static long runAll(ExecutorService ex, int tasks) {
        long t0 = System.nanoTime();
        try (ex) {
            for (int i = 0; i < tasks; i++) {
                ex.submit(() -> sleep(100));
            }
        }
        return ms(t0);
    }

    @Test
    @DisplayName("100ms 기다리는 작업 2000개는 플랫폼 스레드 100개 풀에서 2초 가까이 걸리고, 가상 스레드로는 1초 안에 끝난다")
    void blockingTasksOnVirtualThreads() {
        long platform = runAll(Executors.newFixedThreadPool(100), 2000);
        long virtual = runAll(Executors.newVirtualThreadPerTaskExecutor(), 2000);
        // 100개씩 스무 번 기다리니 100ms × 20보다 짧을 수 없다
        assertThat(platform).isGreaterThanOrEqualTo(1990);
        assertThat(virtual).isLessThan(1000);
        CiOutput.record(LESSON, "predict", "100ms 기다리는 작업 2,000개\n플랫폼 스레드 100개 풀 → " + String.format("%,dms", platform)
                + "\n작업마다 가상 스레드  → " + String.format("%,dms", virtual));
    }

    static String carrier(Thread t) {
        String s = t.toString();
        int at = s.indexOf('@');
        return at < 0 ? "" : s.substring(at + 1);
    }

    @Test
    @DisplayName("같은 가상 스레드가 기다리기 전과 뒤에 다른 캐리어에서 돌 수 있다")
    void carrierCanChange() throws Exception {
        if (Runtime.getRuntime().availableProcessors() < 2) {
            return; // 캐리어가 하나면 바뀔 수 없다
        }
        Set<String> moved = ConcurrentHashMap.newKeySet();
        AtomicReference<String> example = new AtomicReference<>();
        for (int attempt = 0; attempt < 5 && moved.isEmpty(); attempt++) {
            try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
                IntStream.range(0, 200).forEach(i -> ex.submit(() -> {
                    String before = carrier(Thread.currentThread());
                    sleep(20);
                    String after = carrier(Thread.currentThread());
                    if (!before.equals(after)) {
                        moved.add(before + " → " + after);
                        example.compareAndSet(null, Thread.currentThread().getName() + "#" + Thread.currentThread().threadId()
                                + ": " + before + " → " + after);
                    }
                }));
            }
        }
        assertThat(moved).isNotEmpty();
        CiOutput.record(LESSON, "carrier", "sleep(20) 전과 뒤의 캐리어\n" + example.get().replaceAll("^#", "가상 스레드 #"));
    }

    /** 자식 JVM에서 실행된다. 캐리어가 하나일 때 synchronized와 ReentrantLock 안에서 자는 가상 스레드 둘의 시간을 잰다. */
    public static class Pinning {
        public static void main(String[] args) throws Exception {
            Object lockA = new Object();
            Object lockB = new Object();
            long t0 = System.nanoTime();
            Thread a = Thread.ofVirtual().start(() -> {
                synchronized (lockA) {
                    sleep(200);
                }
            });
            Thread b = Thread.ofVirtual().start(() -> {
                synchronized (lockB) {
                    sleep(200);
                }
            });
            a.join();
            b.join();
            long sync = ms(t0);

            ReentrantLock la = new ReentrantLock();
            ReentrantLock lb = new ReentrantLock();
            long t1 = System.nanoTime();
            Thread c = Thread.ofVirtual().start(() -> {
                la.lock();
                try {
                    sleep(200);
                } finally {
                    la.unlock();
                }
            });
            Thread d = Thread.ofVirtual().start(() -> {
                lb.lock();
                try {
                    sleep(200);
                } finally {
                    lb.unlock();
                }
            });
            c.join();
            d.join();
            long lock = ms(t1);
            System.out.println("synchronized=" + sync + " reentrant=" + lock);
        }
    }

    @Test
    @DisplayName("캐리어를 하나로 줄인 JVM에서 synchronized 안에서 200ms 자는 가상 스레드 둘은 400ms 가까이, ReentrantLock이면 200ms 남짓 걸린다(자바 21)")
    void synchronizedPinsTheCarrier() {
        ChildJvm.Run run = ChildJvm.run(List.of("-Djdk.virtualThreadScheduler.parallelism=1",
                "-Djdk.virtualThreadScheduler.maxPoolSize=1"), Pinning.class);
        Matcher m = Pattern.compile("synchronized=(\\d+) reentrant=(\\d+)").matcher(run.stdout());
        assertThat(m.find()).as(run.stdout() + run.stderr()).isTrue();
        long sync = Long.parseLong(m.group(1));
        long lock = Long.parseLong(m.group(2));
        assertThat(sync).isGreaterThanOrEqualTo(390);
        assertThat(lock).isLessThan(sync - 100);
        CiOutput.record(LESSON, "pinning", "캐리어 1개 (parallelism=1), 가상 스레드 둘이 각자의 락 안에서 200ms sleep\nsynchronized  → "
                + sync + "ms\nReentrantLock → " + lock + "ms");
    }
}
