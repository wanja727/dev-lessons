package lab.devlessons.deadlock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "데드락"(C5)의 주장을 검증한다.
 * 데드락은 래치로 두 스레드가 첫 락을 잡은 뒤에 둘째 락을 잡게 맞춰 매번 일으킨다.
 * 멈춘 스레드는 데몬으로 두어 테스트 JVM이 끝날 수 있게 한다.
 */
class DeadlockTest {

    private static final String LESSON = "deadlock";

    static final class Account {
        final long id;
        long balance;
        final ReentrantLock lock = new ReentrantLock();

        Account(long id, long balance) {
            this.id = id;
            this.balance = balance;
        }

        @Override
        public String toString() {
            return "Account#" + id;
        }
    }

    @Test
    @DisplayName("두 스레드가 첫 락을 잡은 뒤 반대쪽 락을 잡으면 둘 다 BLOCKED로 멈추고, ThreadMXBean이 그 둘을 찾는다")
    void deadlockHappensAndIsDetected() throws Exception {
        Account a = new Account(1, 1000);
        Account b = new Account(2, 1000);
        CountDownLatch bothHoldFirst = new CountDownLatch(2);
        AtomicInteger done = new AtomicInteger();

        Thread t1 = new Thread(() -> transferWithPause(a, b, bothHoldFirst, done), "transfer-1");
        Thread t2 = new Thread(() -> transferWithPause(b, a, bothHoldFirst, done), "transfer-2");
        t1.setDaemon(true);
        t2.setDaemon(true);
        t1.start();
        t2.start();

        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        long[] ids = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (ids == null && System.nanoTime() < deadline) {
            Thread.sleep(100);
            ids = mx.findDeadlockedThreads();
        }
        Thread.sleep(1000);

        assertThat(t1.getState()).isEqualTo(Thread.State.BLOCKED);
        assertThat(t2.getState()).isEqualTo(Thread.State.BLOCKED);
        assertThat(done.get()).isZero();
        assertThat(ids).isNotNull();
        assertThat(Arrays.stream(ids).boxed().toList()).contains(t1.threadId(), t2.threadId());

        StringBuilder out = new StringBuilder("1초 뒤 transfer-1 → " + t1.getState() + ", transfer-2 → " + t2.getState()
                + "\n끝난 이체 → " + done.get() + "건\n\nfindDeadlockedThreads():\n");
        for (ThreadInfo info : mx.getThreadInfo(new long[] {t1.threadId(), t2.threadId()})) {
            out.append('"').append(info.getThreadName()).append("\" 기다리는 락: ")
                    .append(info.getLockName().replaceAll("@.*", "").replaceAll(".*[$]", "")).append(", 가진 스레드: \"")
                    .append(info.getLockOwnerName()).append("\"\n");
        }
        CiOutput.record(LESSON, "predict", out.toString().trim());
    }

    static void transferWithPause(Account from, Account to, CountDownLatch bothHoldFirst, AtomicInteger done) {
        synchronized (from) {
            bothHoldFirst.countDown();
            try {
                bothHoldFirst.await(); // 두 스레드가 첫 락을 잡을 때까지 맞춘다
            } catch (InterruptedException e) {
                return;
            }
            synchronized (to) {
                from.balance -= 10;
                to.balance += 10;
                done.incrementAndGet();
            }
        }
    }

    static void orderedTransfer(Account from, Account to, long amount) {
        Account first = from.id < to.id ? from : to;
        Account second = first == from ? to : from;
        synchronized (first) {
            synchronized (second) {
                from.balance -= amount;
                to.balance += amount;
            }
        }
    }

    static void tryLockTransfer(Account from, Account to, long amount, AtomicInteger retries) {
        while (true) {
            try {
                if (from.lock.tryLock(50, TimeUnit.MILLISECONDS)) {
                    try {
                        if (to.lock.tryLock(50, TimeUnit.MILLISECONDS)) {
                            try {
                                from.balance -= amount;
                                to.balance += amount;
                                return;
                            } finally {
                                to.lock.unlock();
                            }
                        }
                    } finally {
                        from.lock.unlock();
                    }
                }
                retries.incrementAndGet();
                Thread.sleep(ThreadLocalRandom.current().nextInt(1, 5)); // 둘이 똑같이 다시 하지 않게
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    static long[] runBothWays(Runnable ab, Runnable ba) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Thread t1 = new Thread(() -> {
            await(start);
            for (int i = 0; i < 10_000; i++) {
                ab.run();
            }
        });
        Thread t2 = new Thread(() -> {
            await(start);
            for (int i = 0; i < 10_000; i++) {
                ba.run();
            }
        });
        t1.setDaemon(true);
        t2.setDaemon(true);
        long t0 = System.nanoTime();
        t1.start();
        t2.start();
        start.countDown();
        t1.join(20_000);
        t2.join(20_000);
        return new long[] {t1.isAlive() || t2.isAlive() ? 1 : 0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)};
    }

    @Test
    @DisplayName("id 순서로 잡는 transfer는 a→b와 b→a를 1만 번씩 동시에 해도 끝나고 두 잔액의 합이 그대로다")
    void lockOrderingPreventsDeadlock() throws Exception {
        Account a = new Account(1, 1_000_000);
        Account b = new Account(2, 1_000_000);
        long[] r = runBothWays(() -> orderedTransfer(a, b, 10), () -> orderedTransfer(b, a, 10));
        assertThat(r[0]).as("두 스레드가 끝났다").isZero();
        assertThat(a.balance + b.balance).isEqualTo(2_000_000);
        CiOutput.record(LESSON, "ordered", "a→b 1만 번, b→a 1만 번을 동시에\n멈춤 → 없음 (" + String.format("%,dms", r[1])
                + ")\n두 잔액의 합 → " + String.format("%,d", a.balance + b.balance));
    }

    @Test
    @DisplayName("tryLock으로 못 잡으면 풀고 다시 하는 transfer도 끝나고 합이 그대로다")
    void tryLockBackoffPreventsDeadlock() throws Exception {
        Account a = new Account(1, 1_000_000);
        Account b = new Account(2, 1_000_000);
        AtomicInteger retries = new AtomicInteger();
        long[] r = runBothWays(() -> tryLockTransfer(a, b, 10, retries), () -> tryLockTransfer(b, a, 10, retries));
        assertThat(r[0]).isZero();
        assertThat(a.balance + b.balance).isEqualTo(2_000_000);
        CiOutput.record(LESSON, "try-lock", "a→b 1만 번, b→a 1만 번을 동시에\n멈춤 → 없음 (" + String.format("%,dms", r[1])
                + ")\n못 잡아서 다시 한 횟수 → " + retries.get() + "\n두 잔액의 합 → " + String.format("%,d", a.balance + b.balance));
    }

    @Test
    @DisplayName("스레드 2개짜리 풀에서 두 작업이 같은 풀의 작업을 get으로 기다리면 끝나지 않는다")
    void poolStarvationDeadlock() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        CountDownLatch bothRunning = new CountDownLatch(2);
        Runnable outer = () -> {
            bothRunning.countDown();
            try {
                bothRunning.await();
                pool.submit(() -> "안쪽 작업").get(); // 실행할 스레드가 없다
            } catch (Exception e) {
                Thread.currentThread().interrupt();
            }
        };
        Future<?> f1 = pool.submit(outer);
        Future<?> f2 = pool.submit(outer);
        assertThatThrownBy(() -> f1.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
        assertThat(f2.isDone()).isFalse();
        CiOutput.record(LESSON, "pool", "스레드 2개 풀, 바깥 작업 2개가 같은 풀의 안쪽 작업을 get()\n1초 뒤 → TimeoutException (안쪽 작업을 실행할 스레드가 없다)");
        pool.shutdownNow();
    }

    static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
