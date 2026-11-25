package lab.devlessons.synchronization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "경쟁 상태와 동기화"(C3)의 주장을 검증한다.
 * 경쟁 상태는 실행마다 달라서 "생길 수 있다"만 단정한다(여러 번 돌려 한 번이라도 생기는지).
 * 끼어드는 순서가 중요한 주장은 래치와 배리어로 순서를 맞춰 매번 같은 결과를 본다.
 */
class SynchronizationTest {

    private static final String LESSON = "synchronization";
    private static final int TIMES = 1_000_000;

    /** 두 스레드가 같은 일을 동시에 시작하게 하고 둘 다 끝날 때까지 기다린다. */
    static void runTwice(Runnable job) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        Runnable wrapped = () -> {
            try {
                start.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            job.run();
        };
        Thread a = new Thread(wrapped);
        Thread b = new Thread(wrapped);
        a.start();
        b.start();
        start.countDown();
        a.join();
        b.join();
    }

    @Test
    @DisplayName("두 스레드를 확인 직후에 맞춰 세우면 확인하고-행동하기 코드가 같은 사용자에게 두 번 발급한다")
    void checkThenActIssuesTwice() throws Exception {
        Set<String> issued = Collections.synchronizedSet(new HashSet<>());
        AtomicInteger couponCount = new AtomicInteger();
        CyclicBarrier afterCheck = new CyclicBarrier(2);
        runTwice(() -> {
            if (!issued.contains("user-1")) {
                try {
                    afterCheck.await(5, TimeUnit.SECONDS); // 둘 다 "아직 없다"를 본 뒤에 넘어간다
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                issued.add("user-1");
                couponCount.incrementAndGet();
            }
        });
        assertThat(couponCount.get()).isEqualTo(2);
        CiOutput.record(LESSON, "check-then-act", "같은 user-1로 두 스레드가 동시에 발급\n발급한 쿠폰 수 → " + couponCount.get());
    }

    static volatile int volatileCount;

    @Test
    @DisplayName("volatile만 붙인 count++는 두 스레드 100만 번씩에서 2000000보다 작은 값이 나올 수 있다")
    void volatileIsNotAtomic() throws Exception {
        List<Integer> results = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            volatileCount = 0;
            runTwice(() -> {
                for (int j = 0; j < TIMES; j++) {
                    volatileCount++;
                }
            });
            results.add(volatileCount);
            if (results.size() >= 3 && results.stream().anyMatch(v -> v < 2 * TIMES)) {
                break;
            }
        }
        assertThat(results).anyMatch(v -> v < 2 * TIMES);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            out.append(i + 1).append("번째 실행 → ").append(String.format("%,d", results.get(i))).append('\n');
        }
        CiOutput.record(LESSON, "predict", out.toString().trim());
    }

    static volatile boolean running;

    @Test
    @DisplayName("volatile 플래그를 false로 바꾸면 일하는 스레드가 멈춘다")
    void volatileVisibility() throws Exception {
        running = true;
        AtomicLong loops = new AtomicLong();
        Thread worker = new Thread(() -> {
            long n = 0;
            while (running) {
                n++;
            }
            loops.set(n);
        });
        worker.setDaemon(true);
        worker.start();
        Thread.sleep(200);
        long t0 = System.nanoTime();
        running = false;
        worker.join(2000);
        assertThat(worker.isAlive()).isFalse();
        CiOutput.record(LESSON, "flag", "running = false로 바꿈\n일하는 스레드가 멈춤 → " + !worker.isAlive()
                + String.format(" (%.1fms 안에)", (System.nanoTime() - t0) / 1_000_000.0));
    }

    static class Counter {
        private int count = 0;

        synchronized void inc() {
            count++;
        }

        void incWrongLock() {
            synchronized (new Object()) {
                count++;
            }
        }

        synchronized int get() {
            return count;
        }
    }

    @Test
    @DisplayName("부를 때마다 새 락 객체로 잠그면 결과가 2000000보다 작을 수 있다")
    void newLockEachCall() throws Exception {
        int lost = -1;
        for (int i = 0; i < 10 && lost < 0; i++) {
            Counter c = new Counter();
            runTwice(() -> {
                for (int j = 0; j < TIMES; j++) {
                    c.incWrongLock();
                }
            });
            if (c.get() < 2 * TIMES) {
                lost = c.get();
            }
        }
        assertThat(lost).isBetween(0, 2 * TIMES - 1);
        CiOutput.record(LESSON, "wrong-lock", "synchronized (new Object()) → " + String.format("%,d", lost));
    }

    @Test
    @DisplayName("같은 객체의 synchronized 메서드로 세면 정확히 2000000이다")
    void sameLockIsExact() throws Exception {
        Counter c = new Counter();
        runTwice(() -> {
            for (int j = 0; j < TIMES; j++) {
                c.inc();
            }
        });
        assertThat(c.get()).isEqualTo(2 * TIMES);
        CiOutput.record(LESSON, "sync", "synchronized void inc() → " + String.format("%,d", c.get()));
    }

    static class Reentrant {
        synchronized String outer() {
            return "outer → " + inner();
        }

        synchronized String inner() {
            return "inner";
        }
    }

    @Test
    @DisplayName("synchronized는 재진입할 수 있다: 락을 가진 스레드가 같은 락의 다른 synchronized 메서드를 불러도 멈추지 않는다")
    void reentrant() throws Exception {
        AtomicReference<String> result = new AtomicReference<>();
        Thread t = new Thread(() -> result.set(new Reentrant().outer()));
        t.start();
        t.join(2000);
        assertThat(result.get()).isEqualTo("outer → inner");
    }

    @Test
    @DisplayName("두 스레드가 StringBuffer에 10만 글자씩 append하면 길이가 정확히 20만이고, StringBuilder는 어긋나거나 예외가 날 수 있다")
    void stringBufferVsBuilder() throws Exception {
        StringBuffer buffer = new StringBuffer();
        runTwice(() -> {
            for (int i = 0; i < 100_000; i++) {
                buffer.append('a');
            }
        });
        assertThat(buffer.length()).isEqualTo(200_000);

        String builderResult = null;
        for (int attempt = 0; attempt < 20 && builderResult == null; attempt++) {
            StringBuilder builder = new StringBuilder();
            AtomicReference<Throwable> error = new AtomicReference<>();
            runTwice(() -> {
                try {
                    for (int i = 0; i < 100_000; i++) {
                        builder.append('a');
                    }
                } catch (Throwable e) {
                    error.compareAndSet(null, e);
                }
            });
            if (error.get() != null) {
                builderResult = "예외 " + error.get().getClass().getSimpleName();
            } else if (builder.length() != 200_000) {
                builderResult = "길이 " + String.format("%,d", builder.length());
            }
        }
        assertThat(builderResult).as("20번 가운데 한 번은 어긋난다").isNotNull();
        CiOutput.record(LESSON, "buffer", "두 스레드가 10만 글자씩 append (기대 200,000)\nStringBuffer  → 길이 "
                + String.format("%,d", buffer.length()) + "\nStringBuilder → " + builderResult);
    }

    @Test
    @DisplayName("compareAndSet은 기대한 값일 때만 바꾸고 결과를 true와 false로 알려 준다")
    void casBasics() {
        AtomicInteger v = new AtomicInteger(5);
        boolean first = v.compareAndSet(5, 6);
        boolean second = v.compareAndSet(5, 7);
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(v.get()).isEqualTo(6);
        CiOutput.record(LESSON, "cas", "값 5에서\ncompareAndSet(5, 6) → " + first + ", 값 6\ncompareAndSet(5, 7) → " + second
                + ", 값 " + v.get());
    }

    @Test
    @DisplayName("AtomicInteger와 CAS 재시도 반복은 락 없이 정확히 2000000을 만든다")
    void casLoopIsExact() throws Exception {
        AtomicInteger atomic = new AtomicInteger();
        runTwice(() -> {
            for (int j = 0; j < TIMES; j++) {
                atomic.incrementAndGet();
            }
        });
        AtomicInteger viaCas = new AtomicInteger();
        runTwice(() -> {
            for (int j = 0; j < TIMES; j++) {
                int old;
                int next;
                do {
                    old = viaCas.get();
                    next = old + 1;
                } while (!viaCas.compareAndSet(old, next));
            }
        });
        assertThat(atomic.get()).isEqualTo(2 * TIMES);
        assertThat(viaCas.get()).isEqualTo(2 * TIMES);
        CiOutput.record(LESSON, "atomic", "incrementAndGet() → " + String.format("%,d", atomic.get())
                + "\nCAS 재시도 반복   → " + String.format("%,d", viaCas.get()));
    }

    static class CouponService {
        private int issued = 0;

        void issue() {
            issued++;
        }
    }

    static class SafeCouponService {
        private final AtomicLong issued = new AtomicLong();

        void issue() {
            issued.incrementAndGet();
        }
    }

    @Test
    @DisplayName("싱글톤 서비스의 int 필드를 여러 스레드가 올리면 부른 수보다 작을 수 있고, AtomicLong이면 정확하다")
    void serviceCounter() throws Exception {
        int lost = -1;
        for (int attempt = 0; attempt < 10 && lost < 0; attempt++) {
            CouponService s = new CouponService();
            runOn(8, 100_000, s::issue);
            if (s.issued < 800_000) {
                lost = s.issued;
            }
        }
        assertThat(lost).isBetween(0, 799_999);
        SafeCouponService safe = new SafeCouponService();
        runOn(8, 100_000, safe::issue);
        assertThat(safe.issued.get()).isEqualTo(800_000);
        CiOutput.record(LESSON, "service", "스레드 8개 × issue() 10만 번 = 80만 번\nint 필드        → "
                + String.format("%,d", lost) + "\nAtomicLong 필드 → " + String.format("%,d", safe.issued.get()));
    }

    static void runOn(int threads, int times, Runnable call) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> list = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            Thread th = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    return;
                }
                for (int i = 0; i < times; i++) {
                    call.run();
                }
            });
            th.start();
            list.add(th);
        }
        start.countDown();
        for (Thread th : list) {
            th.join();
        }
    }
}
