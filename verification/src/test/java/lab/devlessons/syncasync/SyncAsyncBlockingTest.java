package lab.devlessons.syncasync;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "동기와 비동기, 블로킹과 논블로킹"(C9)의 주장을 검증한다.
 * 비동기 API는 스레드 하나짜리 타이머로 "200ms 뒤에 답이 온다"를 흉내 낸다. 시간은 아래쪽 한계 위주로 단정한다.
 */
class SyncAsyncBlockingTest {

    private static final String LESSON = "sync-async-blocking";
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void tearDown() {
        timer.shutdownNow();
    }

    /** 200ms 뒤에 "답이 왔다"고 알려 주는 비동기 API. 기다리는 동안 어떤 스레드도 붙잡지 않는다. */
    CompletableFuture<String> deliveryApiAsync() {
        CompletableFuture<String> cf = new CompletableFuture<>();
        timer.schedule(() -> cf.complete("배송 중"), 200, TimeUnit.MILLISECONDS);
        return cf;
    }

    static String deliveryApi() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "배송 중";
    }

    static long ms(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    @Test
    @DisplayName("비동기로 맡기고 콜백을 붙이면 바로 다음 줄이 먼저 실행되고, 결과는 나중에 콜백이 처리한다")
    void callbackRunsLater() {
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<Void> done = deliveryApiAsync().thenAccept(d -> log.add("콜백: " + d));
        log.add("다음 줄");
        done.join();
        assertThat(log).containsExactly("다음 줄", "콜백: 배송 중");
        CiOutput.record(LESSON, "callback", "찍힌 순서\n1. " + log.get(0) + "\n2. " + log.get(1));
    }

    @Test
    @DisplayName("빈 큐의 poll은 바로 null이고, take를 부른 스레드는 WAITING으로 멈춘다")
    void pollVersusTake() throws Exception {
        BlockingQueue<String> q = new LinkedBlockingQueue<>();
        long t0 = System.nanoTime();
        String polled = q.poll();
        long pollMs = ms(t0);

        AtomicReference<String> taken = new AtomicReference<>();
        Thread taker = new Thread(() -> {
            try {
                taken.set(q.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        taker.start();
        Thread.sleep(200);
        Thread.State state = taker.getState();
        q.put("배송 시작");
        taker.join(2000);

        assertThat(polled).isNull();
        assertThat(pollMs).isLessThan(50);
        assertThat(state).isEqualTo(Thread.State.WAITING);
        assertThat(taken.get()).isEqualTo("배송 시작");
        CiOutput.record(LESSON, "take-poll", "빈 큐에서\npoll() → " + polled + " (" + pollMs + "ms)\ntake() → 200ms 뒤 스레드 상태 "
                + state + ", 값을 넣자 \"" + taken.get() + "\"을 받고 끝남");
    }

    @Test
    @DisplayName("200ms 비동기 호출 셋을 하나씩 join하면 0.6초 넘게, 다 맡긴 뒤 모으면 그보다 훨씬 빨리 끝난다")
    void asyncThenJoinImmediatelyIsSerial() {
        long t0 = System.nanoTime();
        for (int i = 0; i < 3; i++) {
            deliveryApiAsync().join();
        }
        long serial = ms(t0);

        long t1 = System.nanoTime();
        var a = deliveryApiAsync();
        var b = deliveryApiAsync();
        var c = deliveryApiAsync();
        CompletableFuture.allOf(a, b, c).join();
        long together = ms(t1);

        assertThat(serial).isGreaterThanOrEqualTo(590);
        assertThat(together).isLessThan(serial - 200);
        CiOutput.record(LESSON, "async-blocking", "200ms 비동기 호출 셋\n맡기자마자 join → " + serial + "ms\n다 맡긴 뒤 allOf → " + together
                + "ms");
    }

    @Test
    @DisplayName("200ms 블로킹 호출 100건을 스레드 10개 풀로 처리하면 2초 남짓 걸리고, 같은 100건을 스레드 하나의 타이머로 맡기면 1초 안에 끝난다")
    void blockingThreadsVersusCallbacks() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        long blocking;
        try {
            long t0 = System.nanoTime();
            List<Future<String>> fs = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                fs.add(pool.submit(SyncAsyncBlockingTest::deliveryApi));
            }
            for (Future<String> f : fs) {
                f.get();
            }
            blocking = ms(t0);
        } finally {
            pool.shutdownNow();
        }

        long t1 = System.nanoTime();
        List<CompletableFuture<String>> cfs = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            cfs.add(deliveryApiAsync());
        }
        CompletableFuture.allOf(cfs.toArray(CompletableFuture[]::new)).join();
        long callbacks = ms(t1);

        // 10개씩 열 번 기다리니 200ms × 10보다 짧을 수 없다
        assertThat(blocking).isGreaterThanOrEqualTo(1990);
        assertThat(callbacks).isLessThan(1000);
        CiOutput.record(LESSON, "predict", "200ms 기다리는 호출 100건\n스레드 10개가 블로킹으로 기다림 → " + String.format("%,dms", blocking));
        CiOutput.record(LESSON, "callbacks", "200ms 기다리는 호출 100건\n스레드 1개의 타이머에 맡기고 알림 받음 → " + String.format("%,dms", callbacks));
    }

    @Test
    @DisplayName("이벤트 처리 스레드가 하나일 때 한 작업이 100ms 블로킹하면 다음 작업이 그만큼 밀린다")
    void blockingTheEventLoopDelaysOthers() throws Exception {
        ExecutorService loop = Executors.newSingleThreadExecutor();
        try {
            AtomicLong startedAfter = new AtomicLong();
            long t0 = System.nanoTime();
            loop.submit(() -> {
                try {
                    Thread.sleep(100); // 이벤트 루프에서 JDBC를 부른 셈
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            loop.submit(() -> startedAfter.set(ms(t0))).get();
            assertThat(startedAfter.get()).isGreaterThanOrEqualTo(95);
            CiOutput.record(LESSON, "loop-blocked", "스레드 하나짜리 실행기\n작업 1: Thread.sleep(100)\n작업 2: 바로 끝나는 일 → "
                    + startedAfter.get() + "ms 뒤에야 시작");
        } finally {
            loop.shutdownNow();
        }
    }
}
