package lab.devlessons.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 레슨 "동기화"의 주장을 검증한다. 순수 JUnit 테스트. */
class SynchronizationTest {

    private static final int THREADS = 2;
    private static final int TIMES = 1_000_000;

    static class VolatileCounter {
        volatile int value;
    }

    static class Counter {
        int value;
    }

    @Test
    @DisplayName("volatile만으로는 count++가 정확하지 않다: 두 스레드 100만 번씩, 200만보다 작은 값이 나온다")
    void volatileIsNotAtomic() throws Exception {
        boolean lost = false;
        for (int attempt = 0; attempt < 20 && !lost; attempt++) {
            VolatileCounter c = new VolatileCounter();
            runConcurrently(() -> c.value++);
            lost = c.value < THREADS * TIMES;
        }
        assertThat(lost).isTrue();
    }

    @Test
    @DisplayName("volatile은 가시성을 보장한다: 다른 스레드가 바꾼 플래그를 읽고 반복을 멈춘다")
    void volatileVisibility() throws Exception {
        class Flag {
            volatile boolean running = true;
        }
        Flag flag = new Flag();
        CountDownLatch stopped = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            while (flag.running) {
                Thread.onSpinWait();
            }
            stopped.countDown();
        });
        worker.start();
        Thread.sleep(100);

        flag.running = false;

        assertThat(stopped.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("호출마다 새 락 객체를 쓰면 synchronized가 아무것도 막지 못한다")
    void differentLocksDoNotExclude() throws Exception {
        boolean lost = false;
        for (int attempt = 0; attempt < 20 && !lost; attempt++) {
            Counter c = new Counter();
            runConcurrently(() -> {
                synchronized (new Object()) { // 스레드마다, 호출마다 다른 락
                    c.value++;
                }
            });
            lost = c.value < THREADS * TIMES;
        }
        assertThat(lost).isTrue();
    }

    @Test
    @DisplayName("같은 락 객체로 synchronized하면 정확히 200만")
    void sameLockIsExact() throws Exception {
        Counter c = new Counter();
        Object lock = new Object();
        runConcurrently(() -> {
            synchronized (lock) {
                c.value++;
            }
        });
        assertThat(c.value).isEqualTo(THREADS * TIMES);
    }

    @Test
    @DisplayName("synchronized는 재진입 가능하다: 락을 가진 스레드가 같은 락의 다른 synchronized 메서드를 불러도 멈추지 않는다")
    void reentrant() {
        class Account {
            int balance;

            synchronized void deposit(int amount) {
                balance += amount;
                log(); // 같은 락(this)을 다시 잡는다
            }

            synchronized void log() {
            }
        }
        Account account = new Account();
        account.deposit(100);
        assertThat(account.balance).isEqualTo(100);
    }

    @Test
    @DisplayName("CAS: compareAndSet은 기대한 값일 때만 바꾸고 결과를 true/false로 알려 준다")
    void compareAndSet() {
        AtomicInteger stock = new AtomicInteger(10);

        assertThat(stock.compareAndSet(10, 9)).isTrue();   // 10이 맞으니 9로
        assertThat(stock.compareAndSet(10, 8)).isFalse();  // 이미 9라서 실패
        assertThat(stock.get()).isEqualTo(9);
    }

    @Test
    @DisplayName("CAS 재시도 루프로 만든 증가는 락 없이도 정확히 200만")
    void casLoopIsExact() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        runConcurrently(() -> {
            int current;
            do {
                current = counter.get();
            } while (!counter.compareAndSet(current, current + 1));
        });
        assertThat(counter.get()).isEqualTo(THREADS * TIMES);
    }

    @Test
    @DisplayName("ConcurrentHashMap도 containsKey 후 put은 원자적이지 않다: 두 스레드 모두 값을 만든다")
    void checkThenPutIsNotAtomic() throws Exception {
        Map<String, Integer> cache = new ConcurrentHashMap<>();
        AtomicInteger created = new AtomicInteger();
        CyclicBarrier bothChecked = new CyclicBarrier(2);

        runTwice(() -> {
            if (!cache.containsKey("rate")) {
                await(bothChecked); // 두 스레드 모두 "없음"을 확인한 뒤
                cache.put("rate", created.incrementAndGet());
            }
        });

        assertThat(created.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("computeIfAbsent는 원자적이다: 32개 스레드가 동시에 불러도 값은 한 번만 만든다")
    void computeIfAbsentIsAtomic() throws Exception {
        Map<String, Integer> cache = new ConcurrentHashMap<>();
        AtomicInteger created = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            Thread t = new Thread(() -> {
                awaitLatch(start);
                cache.computeIfAbsent("rate", k -> created.incrementAndGet());
            });
            t.start();
            threads.add(t);
        }
        start.countDown();
        for (Thread t : threads) {
            t.join();
        }

        assertThat(created.get()).isEqualTo(1);
    }

    private static void runConcurrently(Runnable action) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            Thread t = new Thread(() -> {
                awaitLatch(start);
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

    private static void runTwice(Runnable action) throws InterruptedException {
        Thread a = new Thread(action);
        Thread b = new Thread(action);
        a.start();
        b.start();
        a.join();
        b.join();
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
