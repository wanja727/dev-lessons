package lab.devlessons.completablefuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "Future와 CompletableFuture"(C8)의 주장을 검증한다. 예전 레슨 둘(future-and-callable, completable-future)의
 * 테스트를 합쳤다. 시간은 아래쪽 한계(하나씩 기다리면 합보다 짧을 수 없다)와 넉넉한 위쪽 한계로만 단정한다.
 */
class FutureAndCompletableFutureTest {

    private static final String LESSON = "completable-future";
    private ExecutorService pool;

    @AfterEach
    void tearDown() {
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    static String call(String name, long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return name;
    }

    static long ms(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    @Test
    @DisplayName("submit은 바로 Future를 돌려주고, get은 작업이 끝날 때까지 부른 스레드를 막는다")
    void submitReturnsGetBlocks() throws Exception {
        pool = Executors.newFixedThreadPool(1);
        long t0 = System.nanoTime();
        Future<String> f = pool.submit(() -> call("주문", 300));
        long submitMs = ms(t0);
        boolean doneAtFirst = f.isDone();
        String v = f.get();
        long getMs = ms(t0);
        assertThat(submitMs).isLessThan(100);
        assertThat(doneAtFirst).isFalse();
        assertThat(getMs).isGreaterThanOrEqualTo(290);
        CiOutput.record(LESSON, "submit-get", "300ms 작업을 submit\nsubmit이 돌아온 시각 → " + submitMs + "ms (isDone = " + doneAtFirst
                + ")\nget()이 돌아온 시각 → " + getMs + "ms, 값 = " + v);
    }

    @Test
    @DisplayName("모두 맡긴 뒤 get하면 300ms 셋이 0.3초 남짓에, 맡기자마자 하나씩 get하면 0.9초 넘게 걸린다")
    void submitAllThenGetVersusSerial() throws Exception {
        pool = Executors.newFixedThreadPool(3);
        long t0 = System.nanoTime();
        Future<String> o = pool.submit(() -> call("주문", 300));
        Future<String> d = pool.submit(() -> call("배송", 300));
        Future<String> c = pool.submit(() -> call("쿠폰", 300));
        String page = o.get() + d.get() + c.get();
        long all = ms(t0);

        long t1 = System.nanoTime();
        String serial = pool.submit(() -> call("주문", 300)).get() + pool.submit(() -> call("배송", 300)).get()
                + pool.submit(() -> call("쿠폰", 300)).get();
        long oneByOne = ms(t1);

        assertThat(page).isEqualTo(serial);
        assertThat(all).isBetween(290L, 850L);
        assertThat(oneByOne).isGreaterThanOrEqualTo(890L);
        CiOutput.record(LESSON, "predict", "300ms 호출 셋, 스레드 3개\n모두 맡긴 뒤 get → " + all + "ms");
        CiOutput.record(LESSON, "serial", "300ms 호출 셋, 스레드 3개\n맡기자마자 하나씩 get → " + oneByOne + "ms");
    }

    @Test
    @DisplayName("스레드 2개 풀에 300ms 작업 4개를 넣고 모두 get하면 0.6초 넘게 걸린다")
    void queueWhenPoolIsFull() throws Exception {
        pool = Executors.newFixedThreadPool(2);
        long t0 = System.nanoTime();
        List<Future<String>> fs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            fs.add(pool.submit(() -> call("작업", 300)));
        }
        for (Future<String> f : fs) {
            f.get();
        }
        long took = ms(t0);
        assertThat(took).isGreaterThanOrEqualTo(590L);
        CiOutput.record(LESSON, "queue", "스레드 2개 풀에 300ms 작업 4개, 모두 get → " + took + "ms");
    }

    @Test
    @DisplayName("get(500ms)는 2초 작업에서 TimeoutException이고 작업은 끝나지 않은 채 계속 돈다")
    void getWithTimeout() {
        pool = Executors.newFixedThreadPool(1);
        Future<String> f = pool.submit(() -> call("느린 주문", 2000));
        assertThatThrownBy(() -> f.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        assertThat(f.isDone()).isFalse();
        CiOutput.record(LESSON, "timeout", "f.get(500, MILLISECONDS) → TimeoutException\n그 뒤 f.isDone() → " + f.isDone()
                + " (작업은 계속 돈다)");
    }

    @Test
    @DisplayName("작업의 예외는 get에서 ExecutionException으로 감싸져 나오고 getCause가 원래 예외다. submit을 감싼 catch에는 걸리지 않는다")
    void exceptionWrappedInGet() throws Exception {
        pool = Executors.newFixedThreadPool(1);
        boolean caughtAtSubmit = false;
        Future<String> f = null;
        try {
            f = pool.submit(() -> {
                throw new IllegalStateException("주문 없음");
            });
        } catch (IllegalStateException e) {
            caughtAtSubmit = true;
        }
        Throwable thrown = null;
        try {
            f.get();
        } catch (ExecutionException e) {
            thrown = e;
        }
        assertThat(caughtAtSubmit).isFalse();
        assertThat(thrown).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalStateException.class);
        CiOutput.record(LESSON, "fail-get", "submit을 감싼 catch → 걸리지 않음\nf.get() → " + thrown.getClass().getName()
                + "\ngetCause() → " + thrown.getCause());
    }

    @Test
    @DisplayName("cancel(true)는 실행 중인 스레드를 인터럽트하고, 그 뒤 get은 CancellationException이다")
    void cancelInterrupts() throws Exception {
        pool = Executors.newFixedThreadPool(1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        Future<?> f = pool.submit(() -> {
            started.countDown();
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
        });
        started.await(5, TimeUnit.SECONDS);
        f.cancel(true);
        assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(f::get).isInstanceOf(CancellationException.class);
    }

    @Test
    @DisplayName("join은 CompletionException, get은 ExecutionException으로 감싼다")
    void joinVersusGet() {
        CompletableFuture<String> failed = CompletableFuture.supplyAsync(() -> {
            throw new IllegalStateException("주문 없음");
        });
        Throwable byJoin = catchIt(failed::join);
        Throwable byGet = catchIt(failed::get);
        assertThat(byJoin).isInstanceOf(CompletionException.class).hasCauseInstanceOf(IllegalStateException.class);
        assertThat(byGet).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalStateException.class);
        CiOutput.record(LESSON, "join-get", "join() → " + byJoin.getClass().getSimpleName() + " (언체크)\nget()  → "
                + byGet.getClass().getSimpleName() + " (체크)\n둘 다 getCause() → " + byJoin.getCause().getClass().getSimpleName());
    }

    interface Call {
        Object run() throws Exception;
    }

    static Throwable catchIt(Call c) {
        try {
            c.run();
            return null;
        } catch (Throwable e) {
            return e;
        }
    }

    record Page(String order, String delivery) {
    }

    @Test
    @DisplayName("thenCombine으로 300ms 두 작업을 0.3초 남짓에 합치고, allOf로 셋을 모아도 0.3초 남짓이다")
    void combineAndAllOf() {
        pool = Executors.newFixedThreadPool(3);
        long t0 = System.nanoTime();
        var order = CompletableFuture.supplyAsync(() -> call("주문", 300), pool);
        var delivery = CompletableFuture.supplyAsync(() -> call("배송", 300), pool);
        Page page = order.thenCombine(delivery, Page::new).join();
        long combined = ms(t0);

        long t1 = System.nanoTime();
        var o = CompletableFuture.supplyAsync(() -> call("주문", 300), pool);
        var d = CompletableFuture.supplyAsync(() -> call("배송", 300), pool);
        var c = CompletableFuture.supplyAsync(() -> call("쿠폰", 300), pool);
        CompletableFuture.allOf(o, d, c).join();
        String all = o.join() + ", " + d.join() + ", " + c.join();
        long allMs = ms(t1);

        assertThat(page).isEqualTo(new Page("주문", "배송"));
        assertThat(combined).isBetween(290L, 850L);
        assertThat(allMs).isBetween(290L, 850L);
        CiOutput.record(LESSON, "combine", "thenCombine(300ms, 300ms) → " + page + ", " + combined + "ms\nallOf(300ms × 3) → ["
                + all + "], " + allMs + "ms");
    }

    static CompletableFuture<String> couponApiAsync(String couponId, ExecutorService pool) {
        return CompletableFuture.supplyAsync(() -> "쿠폰 " + couponId, pool);
    }

    @Test
    @DisplayName("thenCompose는 다음 비동기 호출을 펼쳐 잇고, thenApply로 이으면 CompletableFuture가 겹친다")
    void thenComposeFlattens() {
        pool = Executors.newFixedThreadPool(2);
        var order = CompletableFuture.supplyAsync(() -> "C-7", pool);
        CompletableFuture<CompletableFuture<String>> nested = order.thenApply(id -> couponApiAsync(id, pool));
        CompletableFuture<String> flat = order.thenCompose(id -> couponApiAsync(id, pool));
        assertThat(nested.join().join()).isEqualTo("쿠폰 C-7");
        assertThat(flat.join()).isEqualTo("쿠폰 C-7");
        CiOutput.record(LESSON, "compose", "thenApply   → " + nested.join().getClass().getSimpleName() + " 안에 또 하나 (join 두 번)\n"
                + "thenCompose → \"" + flat.join() + "\" (join 한 번)");
    }

    @Test
    @DisplayName("중간 단계가 실패하면 뒤의 thenApply는 실행되지 않고, exceptionally가 기본값으로 바꾼다. handle은 성공과 실패 모두에 불린다")
    void failureSkipsStagesAndRecovers() {
        AtomicInteger ran = new AtomicInteger();
        CompletableFuture<Integer> failed = CompletableFuture.supplyAsync(() -> {
            throw new IllegalStateException("쿠폰 서비스 오류");
        });
        CompletableFuture<Integer> next = failed.thenApply(x -> {
            ran.incrementAndGet();
            return x + 1;
        });
        Throwable joined = catchIt(next::join);
        int recovered = next.exceptionally(e -> -1).join();
        String handledFail = failed.handle((v, e) -> e == null ? "값 " + v : "실패 " + e.getCause().getMessage()).join();
        String handledOk = CompletableFuture.completedFuture(5).handle((v, e) -> e == null ? "값 " + v : "실패").join();

        assertThat(ran.get()).isZero();
        assertThat(joined).isInstanceOf(CompletionException.class);
        assertThat(recovered).isEqualTo(-1);
        assertThat(handledFail).isEqualTo("실패 쿠폰 서비스 오류");
        assertThat(handledOk).isEqualTo("값 5");
        CiOutput.record(LESSON, "failure", "실패 뒤 thenApply가 실행된 횟수 → " + ran.get() + "\njoin() → "
                + joined.getClass().getSimpleName() + "\n.exceptionally(e -> -1) → " + recovered + "\nhandle(실패) → " + handledFail
                + "\nhandle(성공 5) → " + handledOk);
    }

    @Test
    @DisplayName("orTimeout은 시간을 넘기면 TimeoutException으로 실패시키고, completeOnTimeout은 기본값으로 끝낸다")
    void timeouts() {
        pool = Executors.newFixedThreadPool(2);
        long t0 = System.nanoTime();
        var slow = CompletableFuture.supplyAsync(() -> call("쿠폰 목록", 2000), pool).orTimeout(500, TimeUnit.MILLISECONDS);
        Throwable e = catchIt(slow::join);
        long took = ms(t0);
        String recovered = CompletableFuture.supplyAsync(() -> call("쿠폰 목록", 2000), pool)
                .orTimeout(500, TimeUnit.MILLISECONDS).exceptionally(x -> "빈 목록").join();
        String filled = CompletableFuture.supplyAsync(() -> call("쿠폰 목록", 2000), pool)
                .completeOnTimeout("빈 목록", 500, TimeUnit.MILLISECONDS).join();
        assertThat(e).isInstanceOf(CompletionException.class).hasCauseInstanceOf(TimeoutException.class);
        assertThat(took).isLessThan(1500);
        assertThat(recovered).isEqualTo("빈 목록");
        assertThat(filled).isEqualTo("빈 목록");
        CiOutput.record(LESSON, "recover", "2초 걸리는 쿠폰 호출\norTimeout(500ms) → " + e.getCause().getClass().getSimpleName() + " (" + took
                + "ms)\norTimeout(500ms).exceptionally(...) → " + recovered + "\ncompleteOnTimeout(\"빈 목록\", 500ms) → " + filled);
    }

    @Test
    @DisplayName("executor 없는 supplyAsync는 ForkJoinPool.commonPool에서, 넘기면 그 풀에서 돈다. thenApplyAsync는 지정한 풀에서 돈다")
    void whichThread() {
        pool = Executors.newFixedThreadPool(1, r -> new Thread(r, "api-pool-1"));
        AtomicReference<String> defaultPool = new AtomicReference<>();
        AtomicReference<String> givenPool = new AtomicReference<>();
        CompletableFuture.supplyAsync(() -> defaultPool.getAndSet(Thread.currentThread().getName())).join();
        CompletableFuture.supplyAsync(() -> givenPool.getAndSet(Thread.currentThread().getName()), pool).join();
        String asyncStage = CompletableFuture.completedFuture(1)
                .thenApplyAsync(x -> Thread.currentThread().getName(), pool).join();
        assertThat(defaultPool.get()).startsWith("ForkJoinPool.commonPool-worker-");
        assertThat(givenPool.get()).isEqualTo("api-pool-1");
        assertThat(asyncStage).isEqualTo("api-pool-1");
        CiOutput.record(LESSON, "threads", "supplyAsync(공급자)       → " + defaultPool.get().replaceAll("[0-9]+$", "N")
                + "\nsupplyAsync(공급자, pool) → " + givenPool.get() + "\nthenApplyAsync(f, pool)  → " + asyncStage);
    }
}
