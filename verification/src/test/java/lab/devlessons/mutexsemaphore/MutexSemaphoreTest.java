package lab.devlessons.mutexsemaphore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "뮤텍스와 세마포어"(C4)의 주장을 검증한다.
 * 기다리는 순서가 중요한 주장은 래치로 "다른 스레드가 이미 잡았다"를 확인한 뒤에 본다.
 */
class MutexSemaphoreTest {

    private static final String LESSON = "mutex-semaphore";

    @Test
    @DisplayName("두 스레드가 ReentrantLock 안에서 10만 번씩 세면 정확히 20만이다")
    void lockIsExact() throws Exception {
        ReentrantLock lock = new ReentrantLock();
        int[] count = new int[1];
        Runnable job = () -> {
            for (int i = 0; i < 100_000; i++) {
                lock.lock();
                try {
                    count[0]++;
                } finally {
                    lock.unlock();
                }
            }
        };
        Thread a = new Thread(job);
        Thread b = new Thread(job);
        a.start();
        b.start();
        a.join();
        b.join();
        assertThat(count[0]).isEqualTo(200_000);
        CiOutput.record(LESSON, "lock", "lock() ~ finally unlock()으로 감싼 count++\n두 스레드 × 10만 번 → "
                + String.format("%,d", count[0]));
    }

    @Test
    @DisplayName("주인이 아닌 스레드의 unlock은 IllegalMonitorStateException이다")
    void onlyOwnerUnlocks() throws Exception {
        ReentrantLock lock = new ReentrantLock();
        lock.lock();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread other = new Thread(() -> {
            try {
                lock.unlock();
            } catch (Throwable e) {
                error.set(e);
            }
        });
        other.start();
        other.join();
        lock.unlock();
        assertThat(error.get()).isInstanceOf(IllegalMonitorStateException.class);
        CiOutput.record(LESSON, "owner", "스레드 A가 잡은 락을 스레드 B가 unlock()\n→ " + error.get().getClass().getName());
    }

    /** 다른 스레드가 락을 잡고 있는 동안 body를 실행한다. */
    static void whileHeldByOther(ReentrantLock lock, ThrowingRunnable body) throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                held.countDown();
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        });
        holder.start();
        held.await();
        try {
            body.run();
        } finally {
            release.countDown();
            holder.join();
        }
    }

    interface ThrowingRunnable {
        void run() throws Exception;
    }

    @Test
    @DisplayName("다른 스레드가 락을 잡고 있으면 tryLock()은 바로 false이고, tryLock(100ms)는 100ms 남짓 기다린 뒤 false다")
    void tryLockGivesUp() throws Exception {
        ReentrantLock lock = new ReentrantLock();
        AtomicReference<String> out = new AtomicReference<>();
        whileHeldByOther(lock, () -> {
            long t0 = System.nanoTime();
            boolean now = lock.tryLock();
            long t1 = System.nanoTime();
            boolean timed = lock.tryLock(100, TimeUnit.MILLISECONDS);
            long t2 = System.nanoTime();
            assertThat(now).isFalse();
            assertThat(timed).isFalse();
            assertThat(TimeUnit.NANOSECONDS.toMillis(t2 - t1)).isGreaterThanOrEqualTo(90);
            out.set(String.format("tryLock()       → %s (%.1fms)%ntryLock(100ms)  → %s (%.0fms)", now,
                    (t1 - t0) / 1e6, timed, (t2 - t1) / 1e6));
        });
        CiOutput.record(LESSON, "try-lock", "다른 스레드가 락을 잡고 있을 때\n" + out.get());
    }

    @Test
    @DisplayName("lockInterruptibly로 기다리는 스레드는 interrupt하면 InterruptedException으로 기다림을 그만둔다")
    void lockInterruptibly() throws Exception {
        ReentrantLock lock = new ReentrantLock();
        AtomicReference<Throwable> error = new AtomicReference<>();
        whileHeldByOther(lock, () -> {
            Thread waiter = new Thread(() -> {
                try {
                    lock.lockInterruptibly();
                    lock.unlock();
                } catch (InterruptedException e) {
                    error.set(e);
                }
            });
            waiter.start();
            Thread.sleep(100);
            waiter.interrupt();
            waiter.join(2000);
            assertThat(waiter.isAlive()).isFalse();
        });
        assertThat(error.get()).isInstanceOf(InterruptedException.class);
    }

    @Test
    @DisplayName("허가 3개인 세마포어에 스레드 10개를 보내면 동시에 안에 있는 수는 최대 3이고, 200ms 호출 10개는 0.8초 남짓 걸린다")
    void semaphoreLimits() throws Exception {
        Semaphore permits = new Semaphore(3);
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxInside = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    permits.acquire();
                    try {
                        int now = inside.incrementAndGet();
                        maxInside.accumulateAndGet(now, Math::max);
                        Thread.sleep(200); // 결제 API 호출
                        inside.decrementAndGet();
                    } finally {
                        permits.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads.add(t);
            t.start();
        }
        long t0 = System.nanoTime();
        start.countDown();
        for (Thread t : threads) {
            t.join();
        }
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        assertThat(maxInside.get()).isEqualTo(3);
        // 세 개씩 네 묶음(3, 3, 3, 1)이라 200ms × 4보다 짧을 수 없다
        assertThat(ms).isGreaterThanOrEqualTo(790);
        CiOutput.record(LESSON, "predict", "허가 3개, 스레드 10개, 호출마다 200ms\n동시에 안에 있던 최대 수 → " + maxInside.get()
                + "\n모두 끝난 시간 → " + String.format("%,dms", ms));
    }

    @Test
    @DisplayName("세마포어에는 주인이 없다: 다른 스레드가 받은 허가를 돌려줄 수 있고, 받지 않고 release하면 허가가 처음보다 늘어난다")
    void semaphoreHasNoOwner() throws Exception {
        Semaphore permits = new Semaphore(2);
        Thread a = new Thread(() -> permits.acquireUninterruptibly());
        a.start();
        a.join();
        int afterA = permits.availablePermits();
        Thread b = new Thread(permits::release);
        b.start();
        b.join();
        int afterB = permits.availablePermits();
        permits.release();
        int extra = permits.availablePermits();
        assertThat(afterA).isEqualTo(1);
        assertThat(afterB).isEqualTo(2);
        assertThat(extra).isEqualTo(3);
        CiOutput.record(LESSON, "no-owner", "new Semaphore(2)\n스레드 A가 acquire() → 남은 허가 " + afterA
                + "\n스레드 B가 release() → 남은 허가 " + afterB + "\n받지 않고 한 번 더 release() → 남은 허가 " + extra);
    }

    static void callPaymentApi() {
        throw new IllegalStateException("결제사 응답 오류");
    }

    @Test
    @DisplayName("acquire 뒤 예외로 release를 건너뛰면 허가가 줄어들고, try-finally면 그대로다")
    void permitLeak() {
        Semaphore leaky = new Semaphore(3);
        Semaphore safe = new Semaphore(3);
        for (int i = 0; i < 3; i++) {
            try {
                leaky.acquireUninterruptibly();
                callPaymentApi();
                leaky.release();
            } catch (IllegalStateException ignored) {
                // 예외로 release를 건너뛰었다
            }
            try {
                safe.acquireUninterruptibly();
                try {
                    callPaymentApi();
                } finally {
                    safe.release();
                }
            } catch (IllegalStateException ignored) {
                // 허가는 돌려줬다
            }
        }
        boolean next = leaky.tryAcquire();
        assertThat(leaky.availablePermits()).isZero();
        assertThat(next).isFalse();
        assertThat(safe.availablePermits()).isEqualTo(3);
        CiOutput.record(LESSON, "leak", "결제 API가 예외를 세 번 던진 뒤 남은 허가\nrelease가 finally 밖 → "
                + leaky.availablePermits() + " (다음 호출은 영원히 기다림)\nrelease가 finally 안 → " + safe.availablePermits());
    }
}
