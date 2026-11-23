package lab.devlessons.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 레슨 "CompletableFuture"의 주장을 검증한다. */
class CompletableFutureTest {

    private ExecutorService pool;

    @BeforeEach
    void setUp() {
        pool = Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r);
            t.setName("api-pool-" + t.getId());
            return t;
        });
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    @Test
    @DisplayName("executor를 넘기지 않은 supplyAsync는 공용 풀(ForkJoinPool.commonPool)에서 실행된다")
    void defaultPoolIsCommonPool() {
        String thread = CompletableFuture.supplyAsync(() -> Thread.currentThread().getName()).join();

        assertThat(thread).contains("ForkJoinPool.commonPool");
    }

    @Test
    @DisplayName("executor를 넘기면 그 풀에서 실행된다")
    void customExecutor() {
        String thread = CompletableFuture.supplyAsync(() -> Thread.currentThread().getName(), pool).join();

        assertThat(thread).startsWith("api-pool-");
    }

    @Test
    @DisplayName("thenApply는 값을 완성시킨 스레드에서, thenApplyAsync는 지정한 풀에서 실행된다")
    void thenApplyVsAsync() {
        // 두 단계를 같은 future에 걸지 않는다. 같이 걸면 풀 스레드가 자기 단계를 마친 뒤
        // 남아 있는 thenApply까지 대신 실행할 때가 있어서, thenApply가 도는 스레드가 실행마다 달라진다
        CompletableFuture<String> syncSource = new CompletableFuture<>();
        CompletableFuture<String> asyncSource = new CompletableFuture<>();
        AtomicReference<String> syncThread = new AtomicReference<>();
        AtomicReference<String> asyncThread = new AtomicReference<>();
        CompletableFuture<Void> a = syncSource.thenApply(v -> { syncThread.set(Thread.currentThread().getName()); return v; }).thenAccept(v -> { });
        CompletableFuture<Void> b = asyncSource.thenApplyAsync(v -> { asyncThread.set(Thread.currentThread().getName()); return v; }, pool).thenAccept(v -> { });

        syncSource.complete("ok"); // 테스트 스레드가 값을 완성
        asyncSource.complete("ok");
        a.join();
        b.join();

        assertThat(syncThread.get()).isEqualTo(Thread.currentThread().getName());
        assertThat(asyncThread.get()).startsWith("api-pool-");
    }

    @Test
    @DisplayName("thenCombine: 두 작업(각 300ms)이 동시에 실행되어 합친 결과가 0.55초 안에 나온다")
    void thenCombineRunsInParallel() {
        long start = System.nanoTime();
        CompletableFuture<Integer> rate = CompletableFuture.supplyAsync(() -> slow(1300), pool);
        CompletableFuture<Integer> price = CompletableFuture.supplyAsync(() -> slow(1000), pool);

        int krw = rate.thenCombine(price, (r, p) -> r * p / 1000).join();

        assertThat(krw).isEqualTo(1300);
        assertThat(elapsedMillis(start)).isLessThan(550);
    }

    @Test
    @DisplayName("thenCompose: 앞 결과로 다음 비동기 호출을 이어 붙인다 (thenApply면 CompletableFuture가 겹친다)")
    void thenCompose() {
        CompletableFuture<Long> userId = CompletableFuture.supplyAsync(() -> 7L, pool);

        CompletableFuture<String> grade = userId.thenCompose(id -> CompletableFuture.supplyAsync(() -> "GOLD-" + id, pool));
        CompletableFuture<CompletableFuture<String>> nested = userId.thenApply(id -> CompletableFuture.supplyAsync(() -> "GOLD-" + id, pool));

        assertThat(grade.join()).isEqualTo("GOLD-7");
        assertThat(nested.join().join()).isEqualTo("GOLD-7");
    }

    @Test
    @DisplayName("allOf: 세 작업(300ms)을 모두 기다려 결과를 모은다. 총 시간은 가장 긴 작업 정도")
    void allOf() {
        long start = System.nanoTime();
        List<CompletableFuture<Integer>> calls = List.of(
                CompletableFuture.supplyAsync(() -> slow(1), pool),
                CompletableFuture.supplyAsync(() -> slow(2), pool),
                CompletableFuture.supplyAsync(() -> slow(3), pool));

        List<Integer> results = CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new))
                .thenApply(v -> calls.stream().map(CompletableFuture::join).toList())
                .join();

        assertThat(results).containsExactly(1, 2, 3);
        assertThat(elapsedMillis(start)).isLessThan(800);
    }

    @Test
    @DisplayName("중간 단계에서 예외가 나면 뒤의 thenApply는 실행되지 않고, join은 CompletionException으로 감싸 던진다")
    void exceptionSkipsLaterStages() {
        AtomicBoolean laterRan = new AtomicBoolean(false);
        CompletableFuture<Integer> f = CompletableFuture
                .supplyAsync(() -> { throw new IllegalStateException("API 실패"); }, pool)
                .thenApply(v -> { laterRan.set(true); return 1; });

        assertThatThrownBy(f::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(laterRan).isFalse();
    }

    @Test
    @DisplayName("get()은 체크 예외 ExecutionException, join()은 언체크 예외 CompletionException")
    void getVsJoin() {
        CompletableFuture<Integer> failed = CompletableFuture.failedFuture(new IllegalStateException("x"));

        assertThatThrownBy(failed::get).isInstanceOf(ExecutionException.class);
        assertThatThrownBy(failed::join).isInstanceOf(CompletionException.class);
    }

    @Test
    @DisplayName("exceptionally는 실패만 기본값으로 바꾸고, handle은 성공과 실패를 모두 받는다")
    void exceptionallyAndHandle() {
        CompletableFuture<Integer> failed = CompletableFuture.supplyAsync(() -> { throw new IllegalStateException(); }, pool);
        CompletableFuture<Integer> ok = CompletableFuture.supplyAsync(() -> 5, pool);

        assertThat(failed.exceptionally(e -> 0).join()).isZero();
        assertThat(ok.exceptionally(e -> 0).join()).isEqualTo(5);
        assertThat(failed.handle((v, e) -> e == null ? "성공 " + v : "실패").join()).isEqualTo("실패");
        assertThat(ok.handle((v, e) -> e == null ? "성공 " + v : "실패").join()).isEqualTo("성공 5");
    }

    @Test
    @DisplayName("orTimeout은 시간 안에 안 끝나면 TimeoutException으로 실패시키고, completeOnTimeout은 기본값으로 완료한다")
    void timeouts() {
        CompletableFuture<Integer> slowCall = CompletableFuture.supplyAsync(() -> slow(1, 2_000), pool);

        assertThatThrownBy(() -> slowCall.copy().orTimeout(200, TimeUnit.MILLISECONDS).join())
                .hasCauseInstanceOf(TimeoutException.class);
        assertThat(slowCall.copy().completeOnTimeout(-1, 200, TimeUnit.MILLISECONDS).join()).isEqualTo(-1);
    }

    private static int slow(int value) {
        return slow(value, 300);
    }

    private static int slow(int value, long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return value;
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
