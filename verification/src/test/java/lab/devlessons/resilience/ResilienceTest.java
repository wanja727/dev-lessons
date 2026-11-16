package lab.devlessons.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 레슨 "외부 API 타임아웃, 재시도, 서킷 브레이커"의 주장을 진짜 HTTP 서버(가짜 PG사)로 검증한다. */
class ResilienceTest {

    static final String LESSON = "resilience";
    static FakePaymentApi api;
    static final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();

    @BeforeAll
    static void start() {
        api = new FakePaymentApi();
    }

    @AfterAll
    static void stop() {
        api.close();
    }

    @BeforeEach
    void setUp() {
        api.reset();
    }

    /** 결제 API를 부른다. timeout이 null이면 응답을 끝까지 기다린다 */
    static int pay(Duration timeout, String idempotencyKey) {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(api.url())).POST(HttpRequest.BodyPublishers.ofString("{\"amount\":10000}"));
        if (timeout != null) {
            req.timeout(timeout);
        }
        if (idempotencyKey != null) {
            req.header("Idempotency-Key", idempotencyKey);
        }
        try {
            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 500) {
                throw new IllegalStateException("PG 오류 " + res.statusCode());
            }
            return res.statusCode();
        } catch (HttpTimeoutException e) {
            throw new IllegalStateException("타임아웃", e);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * 요청 스레드 10개(톰캣 스레드 풀 대신)에 결제 요청 10건을 넣고, 바로 뒤에 PG와 상관없는 요청(상품 조회)을 넣는다.
     * 상품 조회가 끝나기까지 걸린 시간을 돌려준다.
     */
    long otherRequestWaitMillis(Duration timeout) throws Exception {
        ExecutorService requestThreads = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 10; i++) {
            requestThreads.submit(() -> {
                try {
                    pay(timeout, null);
                } catch (IllegalStateException ignored) {
                    // 타임아웃이면 실패 응답
                }
            });
        }
        long t0 = System.nanoTime();
        Future<?> productView = requestThreads.submit(() -> { }); // PG와 상관없는 가벼운 요청
        productView.get(30, TimeUnit.SECONDS);
        long waited = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        requestThreads.shutdown();
        requestThreads.awaitTermination(30, TimeUnit.SECONDS);
        return waited;
    }

    @Test
    @DisplayName("타임아웃이 없으면 느린 PG 응답(3초)이 요청 스레드를 모두 붙잡아, PG와 상관없는 요청도 3초 가까이 기다린다")
    void noTimeoutExhaustsThreads() throws Exception {
        api.delayMillis = 3_000;

        long withoutTimeout = otherRequestWaitMillis(null);
        long withTimeout = otherRequestWaitMillis(Duration.ofMillis(300));

        assertThat(withoutTimeout).isGreaterThan(2_500);
        assertThat(withTimeout).isLessThan(1_500);
        CiOutput.record(LESSON, "thread-exhaustion", "[요청 스레드 10개, PG 응답 3초, 결제 요청 10건 뒤에 상품 조회 1건]\n"
                + "타임아웃 없음: 상품 조회가 " + withoutTimeout + "ms 기다림\n"
                + "타임아웃 300ms: 상품 조회가 " + withTimeout + "ms 기다림");
    }

    @Test
    @DisplayName("타임아웃은 '실패'가 아니라 '모름'이다: 클라이언트는 실패로 봤지만 PG는 이미 결제를 처리했다")
    void timeoutDoesNotMeanFailure() {
        api.delayMillis = 500;

        assertThatThrownBy(() -> pay(Duration.ofMillis(200), null)).hasMessageContaining("타임아웃");

        assertThat(api.processed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("멱등 키 없이 타임아웃 후 재시도하면 결제가 두 번 처리된다. 같은 멱등 키로 재시도하면 한 번")
    void retryNeedsIdempotencyKey() {
        api.delayMillis = 500;
        Retry retry = Retry.of("pg", RetryConfig.custom().maxAttempts(2).waitDuration(Duration.ofMillis(50)).build());

        try {
            Retry.decorateRunnable(retry, () -> pay(Duration.ofMillis(200), null)).run();
        } catch (IllegalStateException ignored) {
            // 두 번 다 타임아웃
        }
        int withoutKey = api.processed.get();

        api.reset();
        api.delayMillis = 500;
        String key = UUID.randomUUID().toString(); // 재시도해도 같은 키
        try {
            Retry.decorateRunnable(retry, () -> pay(Duration.ofMillis(200), key)).run();
        } catch (IllegalStateException ignored) {
            // 두 번 다 타임아웃
        }
        int withKey = api.processed.get();

        assertThat(withoutKey).isEqualTo(2);
        assertThat(withKey).isEqualTo(1);
        assertThat(api.received.get()).isEqualTo(2);
        CiOutput.record(LESSON, "retry-duplicate", "[PG 응답 500ms, 타임아웃 200ms, 최대 2번 시도]\n"
                + "멱등 키 없음: PG가 결제를 처리한 횟수 " + withoutKey + "\n"
                + "같은 멱등 키로 재시도: PG에 도착한 요청 " + api.received.get() + ", 처리한 횟수 " + withKey);
    }

    @Test
    @DisplayName("지수 백오프: 재시도 간격이 100ms, 200ms, 400ms로 늘어난다")
    void exponentialBackoff() {
        api.failing = true;
        List<Long> attempts = new CopyOnWriteArrayList<>();
        Retry retry = Retry.of("pg", RetryConfig.custom()
                .maxAttempts(4)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofMillis(100), 2.0))
                .build());

        assertThatThrownBy(() -> Retry.decorateRunnable(retry, () -> {
            attempts.add(System.nanoTime());
            pay(Duration.ofSeconds(1), null);
        }).run()).hasMessageContaining("PG 오류");

        assertThat(attempts).hasSize(4);
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < attempts.size(); i++) {
            gaps.add(TimeUnit.NANOSECONDS.toMillis(attempts.get(i) - attempts.get(i - 1)));
        }
        assertThat(gaps.get(0)).isBetween(90L, 190L);
        assertThat(gaps.get(1)).isBetween(190L, 340L);
        assertThat(gaps.get(2)).isBetween(390L, 600L);
        CiOutput.record(LESSON, "backoff", "[PG가 계속 500, 최대 4번 시도, 지수 백오프 100ms x2]\n시도 사이 간격: "
                + String.join(" → ", gaps.stream().map(g -> g + "ms").toList()));
    }

    @Test
    @DisplayName("서킷 브레이커: 실패율이 기준을 넘으면 열리고, 열린 동안은 PG를 부르지 않고 바로 거절한다. 시간이 지나 시험 호출이 성공하면 다시 닫힌다")
    void circuitBreakerOpensAndCloses() throws Exception {
        CircuitBreaker cb = CircuitBreaker.of("pg", CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(500))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build());
        List<String> log = new CopyOnWriteArrayList<>();
        cb.getEventPublisher().onStateTransition(e -> log.add("상태 변경: " + e.getStateTransition().getFromState()
                + " → " + e.getStateTransition().getToState()));
        Runnable call = CircuitBreaker.decorateRunnable(cb, () -> pay(Duration.ofSeconds(1), null));

        api.failing = true;
        for (int i = 0; i < 10; i++) {
            try {
                call.run();
            } catch (IllegalStateException ignored) {
                // PG 오류
            }
        }
        log.add("PG 오류 10번 (실패율 100%)");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        int before = api.received.get();
        long t0 = System.nanoTime();
        assertThatThrownBy(call::run).isInstanceOf(CallNotPermittedException.class);
        long rejectMicros = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - t0);
        assertThat(api.received.get()).isEqualTo(before); // PG에 요청이 가지 않았다
        log.add("열린 동안 호출: PG를 부르지 않고 바로 거절 (" + rejectMicros + "μs)");

        api.failing = false; // PG 복구
        Thread.sleep(600);
        call.run(); // 반열림 상태의 시험 호출 1
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        call.run(); // 시험 호출 2
        log.add("PG 복구 후 시험 호출 2번 성공");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        CiOutput.record(LESSON, "circuit", "[창 10번, 실패율 기준 50%, 열린 상태 유지 500ms, 반열림 시험 2번]\n" + String.join("\n", log));
    }
}
