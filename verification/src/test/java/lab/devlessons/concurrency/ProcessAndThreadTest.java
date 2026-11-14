package lab.devlessons.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 레슨 "프로세스와 스레드"의 주장을 검증한다. DB가 필요 없는 순수 JUnit 테스트. */
class ProcessAndThreadTest {

    private static final int THREADS = 2;
    private static final int TIMES = 1_000_000;

    @Test
    @DisplayName("프로세스는 메모리를 공유하지 않는다: 자식 JVM이 static 변수를 바꿔도 부모는 그대로, PID도 다르다")
    void processesDoNotShareMemory() throws Exception {
        ChildProcessMain.counter = 0;
        String java = ProcessHandle.current().info().command().orElse("java");
        String classes = Path.of(ChildProcessMain.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();

        Process child = new ProcessBuilder(java, "-cp", classes, ChildProcessMain.class.getName())
                .redirectErrorStream(true)
                .start();
        String out = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertThat(child.waitFor(30, TimeUnit.SECONDS)).isTrue();

        Matcher m = Pattern.compile("pid=(\\d+) counter=(\\d+)").matcher(out);
        assertThat(m.find()).as(out).isTrue();
        assertThat(Long.parseLong(m.group(1))).isNotEqualTo(ProcessHandle.current().pid());
        assertThat(m.group(2)).isEqualTo("100");      // 자식 프로세스 안에서는 100
        assertThat(ChildProcessMain.counter).isZero(); // 부모 프로세스는 그대로 0
    }

    @Test
    @DisplayName("스레드는 힙을 공유한다: 다른 스레드가 쓴 값을 그대로 읽는다")
    void threadsShareHeap() throws Exception {
        int[] box = new int[1];
        Thread worker = new Thread(() -> box[0] = 42);
        worker.start();
        worker.join();

        assertThat(box[0]).isEqualTo(42);
    }

    @Test
    @DisplayName("동기화 없이 두 스레드가 count++를 100만 번씩 하면 200만보다 작은 값이 나온다 (갱신 손실)")
    void unsynchronizedIncrementLosesUpdates() throws Exception {
        boolean lost = false;
        int observed = 0;
        for (int attempt = 0; attempt < 20 && !lost; attempt++) {
            Counter counter = new Counter();
            runConcurrently(() -> counter.value++);
            observed = counter.value;
            lost = counter.value < THREADS * TIMES;
        }
        assertThat(lost).as("마지막 관측값 %d", observed).isTrue();
    }

    @Test
    @DisplayName("AtomicInteger는 정확히 200만")
    void atomicIsExact() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        runConcurrently(counter::incrementAndGet);

        assertThat(counter.get()).isEqualTo(THREADS * TIMES);
    }

    @Test
    @DisplayName("synchronized는 정확히 200만")
    void synchronizedIsExact() throws Exception {
        Counter counter = new Counter();
        Object lock = new Object();
        runConcurrently(() -> {
            synchronized (lock) {
                counter.value++;
            }
        });

        assertThat(counter.value).isEqualTo(THREADS * TIMES);
    }

    static class Counter {
        int value;
    }

    /** 두 스레드가 동시에 출발해서 각각 action을 TIMES번 실행한다. */
    private static void runConcurrently(Runnable action) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            Thread t = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int j = 0; j < TIMES; j++) {
                    action.run();
                }
            });
            t.start();
            threads.add(t);
        }
        start.countDown();
        for (Thread t : threads) {
            t.join();
        }
    }
}
