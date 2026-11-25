package lab.devlessons.threadpool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 레슨 "스레드 풀"(C7)의 주장을 검증한다. 붙잡아 둘 작업은 release 래치로 끝나지 않게 한다. */
class ThreadPoolTest {

    private ExecutorService pool;
    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void tearDown() {
        release.countDown();
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    /** release가 열릴 때까지 붙잡혀 있는 작업 */
    private Runnable blocking() {
        return () -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    @Test
    @DisplayName("스레드 풀은 스레드를 재사용한다: 작업 20개를 스레드 2개가 나눠 처리")
    void reusesThreads() throws Exception {
        pool = Executors.newFixedThreadPool(2);
        Set<String> names = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < 20; i++) {
            pool.submit(() -> names.add(Thread.currentThread().getName())).get();
        }

        assertThat(names).hasSizeLessThanOrEqualTo(2);
        CiOutput.record("thread-pool", "reuse", "작업 20개를 newFixedThreadPool(2)에 넣음\n실행한 스레드 → "
                + new java.util.TreeSet<>(names));
    }

    @Test
    @DisplayName("core 2, max 4, 큐 2: 큐가 가득 찬 뒤에야 스레드가 max까지 늘고, 그다음 작업은 거절된다")
    void coreQueueMaxReject() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 4, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2));
        pool = executor;

        executor.execute(blocking());
        executor.execute(blocking());
        assertThat(executor.getPoolSize()).isEqualTo(2);   // core만큼 스레드 생성
        assertThat(executor.getQueue()).isEmpty();

        executor.execute(blocking());
        executor.execute(blocking());
        assertThat(executor.getPoolSize()).isEqualTo(2);   // 스레드를 늘리지 않고 큐에 넣는다
        assertThat(executor.getQueue()).hasSize(2);

        executor.execute(blocking());
        executor.execute(blocking());
        assertThat(executor.getPoolSize()).isEqualTo(4);   // 큐가 가득 차서 max까지 늘린다
        assertThat(executor.getQueue()).hasSize(2);

        assertThatThrownBy(() -> executor.execute(blocking()))
                .isInstanceOf(RejectedExecutionException.class); // 스레드도 큐도 가득: 거절 (기본 AbortPolicy)
    }

    @Test
    @DisplayName("newFixedThreadPool(2)의 큐는 끝이 없어서 작업 100개를 넣어도 스레드는 2개, 큐에 98개")
    void fixedPoolUnboundedQueue() {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
        pool = executor;

        for (int i = 0; i < 100; i++) {
            executor.execute(blocking());
        }

        assertThat(executor.getPoolSize()).isEqualTo(2);
        assertThat(executor.getQueue()).hasSize(98);
        CiOutput.record("thread-pool", "fixed", "newFixedThreadPool(2)에 붙잡힌 작업 100개\n스레드 → "
                + executor.getPoolSize() + "개, 큐 → " + executor.getQueue().size() + "개");
    }

    @Test
    @DisplayName("newCachedThreadPool은 쉬는 스레드가 없으면 작업마다 새 스레드를 만든다 (작업 50개 → 스레드 50개)")
    void cachedPoolGrowsPerTask() {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        pool = executor;

        for (int i = 0; i < 50; i++) {
            executor.execute(blocking());
        }

        assertThat(executor.getPoolSize()).isEqualTo(50);
        CiOutput.record("thread-pool", "cached", "newCachedThreadPool()에 붙잡힌 작업 50개\n스레드 → "
                + executor.getPoolSize() + "개");
    }

    @Test
    @DisplayName("CallerRunsPolicy: 거절된 작업은 제출한 스레드가 직접 실행한다")
    void callerRuns() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.CallerRunsPolicy());
        pool = executor;
        executor.execute(blocking());
        executor.execute(blocking());

        AtomicReference<String> ranOn = new AtomicReference<>();
        executor.execute(() -> ranOn.set(Thread.currentThread().getName()));

        assertThat(ranOn.get()).isEqualTo(Thread.currentThread().getName());
        CiOutput.record("thread-pool", "caller-runs", "core 1, max 1, 큐 1칸이 찬 풀에 셋째 작업\n실행한 스레드 → "
                + ranOn.get() + " (작업을 넣은 스레드)");
    }

    @Test
    @DisplayName("shutdown: 새 작업은 거절하고, 이미 받은 작업은 끝까지 처리한다")
    void shutdownFinishesAcceptedTasks() throws Exception {
        pool = Executors.newFixedThreadPool(1);
        CountDownLatch done = new CountDownLatch(3);
        for (int i = 0; i < 3; i++) {
            pool.execute(() -> {
                sleep(100);
                done.countDown();
            });
        }

        pool.shutdown();

        assertThatThrownBy(() -> pool.execute(() -> { })).isInstanceOf(RejectedExecutionException.class);
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(done.getCount()).isZero();
    }

    @Test
    @DisplayName("shutdownNow: 실행 중인 작업을 인터럽트하고, 대기 중이던 작업 목록을 돌려준다")
    void shutdownNowReturnsQueued() throws Exception {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        pool = executor;
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        executor.execute(() -> {
            started.countDown();
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
        });
        executor.execute(() -> { });
        executor.execute(() -> { });
        started.await(5, TimeUnit.SECONDS);

        List<Runnable> neverRun = executor.shutdownNow();

        assertThat(neverRun).hasSize(2);
        assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("execute로 던진 예외는 스레드의 UncaughtExceptionHandler로 가고, submit의 예외는 get()까지 숨어 있다")
    void executeVsSubmitExceptions() throws Exception {
        AtomicReference<Throwable> uncaught = new AtomicReference<>();
        CountDownLatch handled = new CountDownLatch(1);
        pool = Executors.newFixedThreadPool(1, r -> {
            Thread t = new Thread(r);
            t.setUncaughtExceptionHandler((thread, e) -> {
                uncaught.set(e);
                handled.countDown();
            });
            return t;
        });

        Future<?> submitted = pool.submit(() -> {
            throw new IllegalStateException("submit");
        });
        assertThatThrownBy(submitted::get).isInstanceOf(ExecutionException.class);
        assertThat(uncaught.get()).isNull();

        pool.execute(() -> {
            throw new IllegalStateException("execute");
        });
        assertThat(handled.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(uncaught.get()).hasMessage("execute");
        CiOutput.record("thread-pool", "exceptions", "submit의 예외 → get()을 부르기 전까지 아무 데도 없음, get()에서 ExecutionException\n"
                + "execute의 예외 → UncaughtExceptionHandler가 받음: " + uncaught.get());
    }

    @Test
    @DisplayName("요청마다 만들고 끝내지 않은 풀은 스레드를 남긴다")
    void poolPerRequestLeaksThreads() throws Exception {
        int before = Thread.activeCount();
        List<ExecutorService> pools = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < 10; i++) {
                ExecutorService perRequest = Executors.newFixedThreadPool(4);
                pools.add(perRequest);
                perRequest.submit(() -> "보고서").get();
            }
            int after = Thread.activeCount();
            assertThat(after - before).isGreaterThanOrEqualTo(10);
            CiOutput.record("thread-pool", "per-request", "요청 10번, 요청마다 newFixedThreadPool(4)를 만들고 끝내지 않음\n"
                    + "살아 있는 스레드 → " + before + "개에서 " + after + "개로");
        } finally {
            pools.forEach(ExecutorService::shutdownNow);
        }
    }

    @Test
    @DisplayName("병렬 스트림은 ForkJoinPool.commonPool의 스레드에서 돌고, 공용 풀의 크기는 코어 수보다 하나 적다")
    void parallelStreamUsesCommonPool() {
        Set<String> names = ConcurrentHashMap.newKeySet();
        java.util.stream.IntStream.range(0, 10_000).parallel()
                .forEach(i -> names.add(Thread.currentThread().getName().replaceAll("[0-9]+$", "N")));
        int cores = Runtime.getRuntime().availableProcessors();
        int parallelism = java.util.concurrent.ForkJoinPool.commonPool().getParallelism();
        assertThat(parallelism).isEqualTo(Math.max(1, cores - 1));
        if (cores > 1) {
            assertThat(names).contains("ForkJoinPool.commonPool-worker-N");
        }
        CiOutput.record("thread-pool", "parallel", "코어 " + cores + "개\ncommonPool().getParallelism() → " + parallelism
                + "\n병렬 스트림을 실행한 스레드 → " + new java.util.TreeSet<>(names));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
