package lab.devlessons.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lab.devlessons.idempotency.PaymentServices.CheckThenInsertPaymentService;
import lab.devlessons.idempotency.PaymentServices.IdempotentPaymentService;
import lab.devlessons.idempotency.PaymentServices.NaivePaymentService;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

/** 레슨 "멱등성과 중복 요청 방지"의 주장을 실제 PostgreSQL에서 검증한다. 테스트에는 트랜잭션이 없다. */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class IdempotencyTest {

    static final String LESSON = "idempotency";

    @Autowired NaivePaymentService naive;
    @Autowired CheckThenInsertPaymentService checkThenInsert;
    @Autowired IdempotentPaymentService idempotent;
    @Autowired PaymentRepository payments;
    @Autowired UnguardedPaymentRepository unguarded;

    @BeforeEach
    void setUp() {
        payments.deleteAll();
        unguarded.deleteAll();
        checkThenInsert.setAfterCheck(() -> { });
    }

    @AfterEach
    void tearDown() {
        checkThenInsert.setAfterCheck(() -> { });
    }

    @Test
    @DisplayName("대책이 없으면 같은 결제 요청을 두 번 보낼 때 결제가 두 건 저장된다 (이중 결제)")
    void naiveDoublePays() {
        naive.pay(1L, 10_000);
        naive.pay(1L, 10_000); // 응답을 못 받은 클라이언트의 재시도

        assertThat(unguarded.countByOrderId(1L)).isEqualTo(2);
        CiOutput.record(LESSON, "naive", "[대책 없음] pay(주문 1, 10000원)을 두 번\n주문 1의 결제: " + unguarded.countByOrderId(1L) + "건");
    }

    @Test
    @DisplayName("먼저 조회 후 저장: 순차 재시도는 막는다")
    void checkThenInsertStopsSequentialRetry() {
        assertThat(checkThenInsert.pay("key-1", 1L, 10_000)).isTrue();
        assertThat(checkThenInsert.pay("key-1", 1L, 10_000)).isFalse();

        assertThat(unguarded.countByIdempotencyKey("key-1")).isEqualTo(1);
    }

    @Test
    @DisplayName("먼저 조회 후 저장: 두 요청이 동시에 조회를 통과하면 둘 다 저장된다")
    void checkThenInsertFailsUnderConcurrency() throws Exception {
        CyclicBarrier bothChecked = new CyclicBarrier(2);
        checkThenInsert.setAfterCheck(() -> await(bothChecked)); // 두 요청이 모두 "없음"을 본 뒤에 저장

        List<Boolean> results = runConcurrently(2, () -> checkThenInsert.pay("key-1", 1L, 10_000));

        assertThat(results).containsExactly(true, true);
        assertThat(unguarded.countByIdempotencyKey("key-1")).isEqualTo(2);
        CiOutput.record(LESSON, "check-race", "[먼저 조회 후 저장] 같은 키의 요청 두 개가 둘 다 조회를 마친 뒤 저장\n두 요청의 결과: " + results + "\nkey-1의 결제: " + unguarded.countByIdempotencyKey("key-1") + "건");
    }

    @Test
    @DisplayName("유니크 제약: 같은 키로 두 번째 INSERT를 하면 DB가 DataIntegrityViolationException으로 막는다")
    void uniqueConstraintRejectsSecondInsert() {
        payments.saveAndFlush(new Payment("key-1", 1L, 10_000));

        assertThatThrownBy(() -> payments.saveAndFlush(new Payment("key-1", 1L, 10_000)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(payments.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("멱등 처리: 같은 키로 다시 요청하면 새로 저장하지 않고 처음 결과(같은 결제 id)를 돌려준다")
    void idempotentRetryReturnsFirstResult() {
        Payment first = idempotent.pay("key-1", 1L, 10_000);
        Payment retry = idempotent.pay("key-1", 1L, 10_000);

        assertThat(retry.getId()).isEqualTo(first.getId());
        assertThat(payments.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("멱등 처리: 같은 키로 동시에 10번 요청해도 결제는 1건, 모두 같은 결제 id를 받는다")
    void idempotentUnderConcurrency() throws Exception {
        List<Payment> results = runConcurrently(10, () -> idempotent.pay("key-1", 1L, 10_000));

        Set<Long> ids = new HashSet<>();
        results.forEach(p -> ids.add(p.getId()));
        assertThat(ids).hasSize(1);
        assertThat(payments.count()).isEqualTo(1);
        CiOutput.record(LESSON, "idempotent", "[멱등 키 + 유니크 제약] 같은 키로 동시에 10번\n저장된 결제: " + payments.count() + "건\n10개 응답이 받은 결제 id: " + ids);
    }

    @Test
    @DisplayName("같은 키로 내용이 다른 요청(금액 변경)이 오면 처음 결과를 돌려주지 않고 거절한다")
    void sameKeyDifferentBodyRejected() {
        idempotent.pay("key-1", 1L, 10_000);

        assertThatThrownBy(() -> idempotent.pay("key-1", 1L, 99_000)).isInstanceOf(IllegalStateException.class);
        assertThat(payments.count()).isEqualTo(1);
    }

    private static <T> List<T> runConcurrently(int n, Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
