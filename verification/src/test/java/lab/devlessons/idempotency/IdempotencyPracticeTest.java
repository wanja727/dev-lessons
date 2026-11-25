package lab.devlessons.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 레슨 "멱등성과 중복 요청 방지"의 질문 4와 실무 구역을 실제 PostgreSQL에서 검증한다. 테스트에는 트랜잭션이 없다. */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class IdempotencyPracticeTest {

    static final String LESSON = "idempotency";

    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired PaymentRepository payments;

    @BeforeEach
    void setUp() {
        jdbc.execute("create table if not exists a6_order_payment (id bigserial primary key,"
                + " idem_key varchar(64) not null unique, order_id bigint not null unique, amount bigint not null)");
        jdbc.execute("truncate a6_order_payment restart identity");
        payments.deleteAll();
    }

    @Test
    @DisplayName("order_id에 유니크 제약을 걸면 멱등 키가 달라도 같은 주문의 두 번째 결제는 막힌다")
    void businessKeyBlocksDifferentIdempotencyKey() {
        jdbc.update("insert into a6_order_payment(idem_key, order_id, amount) values ('key-a', 1, 10000)");

        assertThatThrownBy(() -> jdbc.update(
                "insert into a6_order_payment(idem_key, order_id, amount) values ('key-b', 1, 10000)"))
                .isInstanceOf(DuplicateKeyException.class);
        Integer count = jdbc.queryForObject("select count(*) from a6_order_payment where order_id = 1", Integer.class);
        assertThat(count).isEqualTo(1);
        CiOutput.record(LESSON, "business-key", "[order_id에 유니크 제약]\n"
                + "멱등 키 key-a, 주문 1 → 저장\n"
                + "멱등 키 key-b, 주문 1 → DuplicateKeyException\n"
                + "주문 1의 결제: " + count + "건");
    }

    @Test
    @DisplayName("같은 트랜잭션 안에서 유니크 위반을 잡고 처음 결제를 조회하면 처음 결과를 돌려주지 못한다")
    void catchInsideSameTransactionFails() {
        payments.saveAndFlush(new Payment("key-1", 1L, 10_000));
        List<String> log = new ArrayList<>();

        String outcome;
        try {
            tx.executeWithoutResult(status -> {
                try {
                    payments.saveAndFlush(new Payment("key-1", 1L, 10_000));
                } catch (DataIntegrityViolationException e) {
                    log.add("saveAndFlush → " + e.getClass().getSimpleName() + " (잡음)");
                    try {
                        Payment first = payments.findByIdempotencyKey("key-1").orElseThrow();
                        log.add("findByIdempotencyKey → 결제 id " + first.getId());
                    } catch (RuntimeException again) {
                        log.add("findByIdempotencyKey → " + again.getClass().getSimpleName());
                        log.add("  원인: " + rootMessage(again));
                    }
                }
            });
            outcome = "커밋됨";
        } catch (RuntimeException commit) {
            outcome = commit.getClass().getSimpleName();
        }
        log.add("트랜잭션 끝 → " + outcome);

        assertThat(String.join("\n", log)).satisfiesAnyOf(
                s -> assertThat(s).contains("current transaction is aborted"),
                s -> assertThat(s).contains("UnexpectedRollbackException"));
        CiOutput.record(LESSON, "same-tx", "[@Transactional 안에서 유니크 위반을 잡고 조회]\n" + String.join("\n", log));
    }

    private static String rootMessage(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null) {
            r = r.getCause();
        }
        String m = String.valueOf(r.getMessage());
        int nl = m.indexOf('\n');
        return nl > 0 ? m.substring(0, nl) : m;
    }
}
