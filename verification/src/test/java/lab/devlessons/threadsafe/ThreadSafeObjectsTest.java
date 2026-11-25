package lab.devlessons.threadsafe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "스레드에 안전한 객체"(C6)의 주장을 검증한다.
 * 끼어드는 순서가 중요한 주장은 래치로 순서를 맞추고, 경쟁 상태는 여러 번 돌려 한 번이라도 생기는지만 단정한다.
 */
class ThreadSafeObjectsTest {

    private static final String LESSON = "thread-safe-objects";

    static class MutablePrice {
        long amount = 10000;
        String currency = "KRW";

        void set(long a, String c, CountDownLatch between, CountDownLatch resume) throws InterruptedException {
            amount = a;
            between.countDown(); // 두 줄 사이에서 멈춘다
            resume.await();
            currency = c;
        }
    }

    record Price(long amount, String currency) {
    }

    @Test
    @DisplayName("쓰는 스레드를 두 필드 쓰기 사이에 세우면, 읽는 스레드는 금액만 바뀐 섞인 값을 본다")
    void mutableObjectShowsTornState() throws Exception {
        MutablePrice price = new MutablePrice();
        CountDownLatch between = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        Thread writer = new Thread(() -> {
            try {
                price.set(12000, "USD", between, resume);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        writer.start();
        between.await();
        String seen = price.amount + " " + price.currency; // 래치가 쓰기를 보이게 한다
        resume.countDown();
        writer.join();
        assertThat(seen).isEqualTo("12000 KRW");
        CiOutput.record(LESSON, "torn", "처음 (10000, KRW) → set(12000, \"USD\") 중간에 읽음\n읽은 값 → (" + seen.replace(" ", ", ")
                + ")");
    }

    static volatile Price current = new Price(10000, "KRW");

    @Test
    @DisplayName("불변 Price를 volatile 참조로 바꾸면, 쓰는 스레드가 계속 바꾸는 동안 읽는 스레드는 섞인 값을 한 번도 보지 않는다")
    void immutableSwapIsNeverTorn() throws Exception {
        current = new Price(10000, "KRW");
        AtomicLong reads = new AtomicLong();
        AtomicLong torn = new AtomicLong();
        Thread writer = new Thread(() -> {
            for (int i = 0; i < 1_000_000; i++) {
                current = (i % 2 == 0) ? new Price(12000, "USD") : new Price(10000, "KRW");
            }
        });
        Thread reader = new Thread(() -> {
            while (writer.isAlive()) {
                Price p = current;
                reads.incrementAndGet();
                boolean ok = (p.amount() == 10000 && p.currency().equals("KRW"))
                        || (p.amount() == 12000 && p.currency().equals("USD"));
                if (!ok) {
                    torn.incrementAndGet();
                }
            }
        });
        writer.start();
        reader.start();
        writer.join();
        reader.join();
        assertThat(torn.get()).isZero();
        CiOutput.record(LESSON, "immutable", "쓰는 스레드가 참조를 100만 번 바꾸는 동안\n읽은 횟수 → " + String.format("%,d", reads.get())
                + "\n섞인 값을 본 횟수 → " + torn.get());
    }

    static final ThreadLocal<String> USER = new ThreadLocal<>();

    @Test
    @DisplayName("ThreadLocal은 스레드마다 다른 값을 돌려준다")
    void threadLocalPerThread() throws Exception {
        AtomicReference<String> a = new AtomicReference<>();
        AtomicReference<String> b = new AtomicReference<>();
        CountDownLatch bothSet = new CountDownLatch(2);
        Thread ta = new Thread(() -> {
            USER.set("kim");
            bothSet.countDown();
            await(bothSet);
            a.set(USER.get());
        });
        Thread tb = new Thread(() -> {
            USER.set("lee");
            bothSet.countDown();
            await(bothSet);
            b.set(USER.get());
        });
        ta.start();
        tb.start();
        ta.join();
        tb.join();
        assertThat(a.get()).isEqualTo("kim");
        assertThat(b.get()).isEqualTo("lee");
        CiOutput.record(LESSON, "per-thread", "스레드 A: set(\"kim\") 뒤 get() → " + a.get() + "\n스레드 B: set(\"lee\") 뒤 get() → "
                + b.get());
    }

    @Test
    @DisplayName("스레드 하나짜리 풀에서 앞 작업이 지우지 않은 ThreadLocal 값을 다음 작업이 읽고, remove하면 null이다")
    void threadLocalLeaksInPool() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(() -> USER.set("kim")).get();
            String leaked = pool.submit(USER::get).get();

            pool.submit(() -> {
                try {
                    USER.set("kim");
                } finally {
                    USER.remove();
                }
            }).get();
            String cleaned = pool.submit(USER::get).get();

            assertThat(leaked).isEqualTo("kim");
            assertThat(cleaned).isNull();
            CiOutput.record(LESSON, "predict", "지우지 않음 → 둘째 작업의 user.get() = " + leaked
                    + "\nfinally에서 remove() → 둘째 작업의 user.get() = " + cleaned);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("요청 스레드에서 넣은 ThreadLocal 값을 다른 스레드에서 읽으면 null이다")
    void threadLocalDoesNotFollow() throws Exception {
        USER.set("kim");
        try {
            String here = USER.get();
            String there = CompletableFuture.supplyAsync(USER::get).get(5, TimeUnit.SECONDS);
            assertThat(here).isEqualTo("kim");
            assertThat(there).isNull();
            CiOutput.record(LESSON, "other-thread", "넣은 스레드에서 get() → " + here + "\nsupplyAsync 안에서 get() → " + there);
        } finally {
            USER.remove();
        }
    }

    static void runOn(int threads, java.util.function.IntConsumer body) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> list = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int id = t;
            Thread th = new Thread(() -> {
                await(start);
                body.accept(id);
            });
            list.add(th);
            th.start();
        }
        start.countDown();
        for (Thread th : list) {
            th.join();
        }
    }

    @Test
    @DisplayName("스레드 8개가 HashMap에 서로 다른 키 1만 개씩 넣으면 size가 8만이 아니거나 예외가 날 수 있고, ConcurrentHashMap은 정확히 8만이다")
    void hashMapIsNotThreadSafe() throws Exception {
        String hashMapResult = null;
        for (int attempt = 0; attempt < 20 && hashMapResult == null; attempt++) {
            Map<Integer, Integer> map = new HashMap<>();
            AtomicReference<Throwable> error = new AtomicReference<>();
            runOn(8, id -> {
                try {
                    for (int i = 0; i < 10_000; i++) {
                        map.put(id * 10_000 + i, 1);
                    }
                } catch (Throwable e) {
                    error.compareAndSet(null, e);
                }
            });
            if (error.get() != null) {
                hashMapResult = "예외 " + error.get().getClass().getSimpleName();
            } else if (map.size() != 80_000) {
                hashMapResult = "size() " + String.format("%,d", map.size());
            }
        }
        Map<Integer, Integer> chm = new ConcurrentHashMap<>();
        runOn(8, id -> {
            for (int i = 0; i < 10_000; i++) {
                chm.put(id * 10_000 + i, 1);
            }
        });
        assertThat(hashMapResult).as("20번 가운데 한 번은 어긋난다").isNotNull();
        assertThat(chm.size()).isEqualTo(80_000);
        CiOutput.record(LESSON, "hashmap", "스레드 8개가 서로 다른 키를 1만 개씩 (기대 80,000)\nHashMap           → " + hashMapResult
                + "\nConcurrentHashMap → size() " + String.format("%,d", chm.size()));
    }

    @Test
    @DisplayName("ConcurrentHashMap에서도 get 뒤 put으로 세면 8만보다 작을 수 있고, merge는 정확히 8만이다")
    void getThenPutLosesButMergeIsExact() throws Exception {
        int lost = -1;
        for (int attempt = 0; attempt < 10 && lost < 0; attempt++) {
            Map<Long, Integer> views = new ConcurrentHashMap<>();
            runOn(8, id -> {
                for (int i = 0; i < 10_000; i++) {
                    Integer v = views.get(1L);
                    views.put(1L, v == null ? 1 : v + 1);
                }
            });
            if (views.get(1L) < 80_000) {
                lost = views.get(1L);
            }
        }
        Map<Long, Integer> merged = new ConcurrentHashMap<>();
        runOn(8, id -> {
            for (int i = 0; i < 10_000; i++) {
                merged.merge(1L, 1, Integer::sum);
            }
        });
        assertThat(lost).isBetween(0, 79_999);
        assertThat(merged.get(1L)).isEqualTo(80_000);
        CiOutput.record(LESSON, "merge", "스레드 8개 × 1만 번, 같은 상품의 조회 수 (기대 80,000)\nget 뒤 put → " + String.format("%,d", lost)
                + "\nmerge      → " + String.format("%,d", merged.get(1L)));
    }

    @Test
    @DisplayName("computeIfAbsent는 스레드 32개가 같은 키로 동시에 불러도 값을 한 번만 만들고, containsKey 뒤 put은 여러 번 만들 수 있다")
    void computeIfAbsentCreatesOnce() throws Exception {
        Map<String, Object> cache = new ConcurrentHashMap<>();
        AtomicInteger created = new AtomicInteger();
        runOn(32, id -> cache.computeIfAbsent("USD", k -> {
            created.incrementAndGet();
            return new Object();
        }));

        int racyCreated = 0;
        for (int attempt = 0; attempt < 20 && racyCreated <= 1; attempt++) {
            Map<String, Object> racy = new ConcurrentHashMap<>();
            AtomicInteger count = new AtomicInteger();
            runOn(32, id -> {
                if (!racy.containsKey("USD")) {
                    count.incrementAndGet();
                    racy.put("USD", new Object());
                }
            });
            racyCreated = count.get();
        }
        assertThat(created.get()).isEqualTo(1);
        assertThat(racyCreated).isGreaterThan(1);
        CiOutput.record(LESSON, "compute", "스레드 32개가 같은 키 \"USD\"로 동시에\ncomputeIfAbsent    → 값을 만든 횟수 " + created.get()
                + "\ncontainsKey 뒤 put → 값을 만든 횟수 " + racyCreated);
    }

    static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
