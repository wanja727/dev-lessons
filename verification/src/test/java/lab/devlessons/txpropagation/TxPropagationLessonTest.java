package lab.devlessons.txpropagation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * 레슨 "트랜잭션 전파와 롤백 규칙"(S11)에서 예전 테스트(propagation/PropagationTest)가 다루지 않은 주장을 확인한다.
 * 테스트에는 트랜잭션이 없다. 레슨용 빈은 @Import로만 넣는다.
 */
@SpringBootTest
@Import({PostgresContainerConfig.class, TxPropagationLessonTest.Inner.class, TxPropagationLessonTest.Outer.class})
class TxPropagationLessonTest {

    private static final String LESSON = "tx-propagation";

    public static class QuietException extends RuntimeException {
        public QuietException(String m) {
            super(m);
        }
    }

    public static class Inner {
        private final JdbcTemplate jdbc;

        public Inner(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        int pid() {
            return jdbc.queryForObject("select pg_backend_pid()", Integer.class);
        }

        @Transactional
        public int requiredPid() {
            return pid();
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public int requiresNewPid() {
            return pid();
        }

        /** 리포지토리의 저장처럼, @Transactional 메서드 안에서 유니크 제약을 어긴다 */
        @Transactional
        public void insertCoupon(String code) {
            jdbc.update("insert into tx_prop_coupon(code) values (?)", code);
        }

        @Transactional(noRollbackFor = QuietException.class)
        public void quietFail(String name) {
            jdbc.update("insert into tx_prop_row(name) values (?)", name);
            throw new QuietException("기록만 하고 넘어갈 실패");
        }
    }

    public static class Outer {
        private final Inner inner;
        private final JdbcTemplate jdbc;

        public Outer(Inner inner, JdbcTemplate jdbc) {
            this.inner = inner;
            this.jdbc = jdbc;
        }

        @Transactional
        public List<Integer> pids() {
            int outer = inner.pid();
            return List.of(outer, inner.requiredPid(), inner.requiresNewPid());
        }

        /** 입금 단계. SQL을 보내기 전에 자바 예외로 실패한다(받는 계좌가 막혀 있음) */
        void deposit() {
            throw new IllegalStateException("받는 계좌가 막혀 있어요");
        }

        /** 레슨의 코드 swallow: 같은 메서드 안에서 잡고 삼킨다 */
        @Transactional
        public void transferSwallow() {
            jdbc.update("insert into tx_prop_row(name) values ('출금')");
            try {
                deposit();
            } catch (Exception e) {
                // 로그만 남긴다
            }
        }

        @Transactional
        public void transferRollbackOnly() {
            jdbc.update("insert into tx_prop_row(name) values ('출금')");
            try {
                deposit();
            } catch (Exception e) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }
        }

        @Transactional
        public void transferRethrow() {
            jdbc.update("insert into tx_prop_row(name) values ('출금')");
            try {
                deposit();
            } catch (IllegalStateException e) {
                throw new IllegalStateException("이체 실패", e);
            }
        }

        /** PostgreSQL: 트랜잭션 안에서 SQL 자체가 실패하면, 자바에서 삼켜도 그 트랜잭션은 커밋되지 않는다 */
        @Transactional
        public void transferSwallowSqlError() {
            jdbc.update("insert into tx_prop_row(name) values ('출금')");
            try {
                jdbc.update("insert into tx_prop_row(name) values (null)"); // NOT NULL 위반
            } catch (DataIntegrityViolationException e) {
                // 로그만 남긴다
            }
        }

        /** 합류한 안쪽 @Transactional 메서드의 중복 예외를 잡고 넘어가려 한다 */
        @Transactional
        public void issueIgnoringDuplicate() {
            jdbc.update("insert into tx_prop_row(name) values ('발급 시도')");
            try {
                inner.insertCoupon("WELCOME");
            } catch (DataIntegrityViolationException e) {
                // 중복이면 넘어간다
            }
        }

        @Transactional
        public void callQuiet() {
            jdbc.update("insert into tx_prop_row(name) values ('outer')");
            try {
                inner.quietFail("inner");
            } catch (QuietException e) {
                // 넘어간다
            }
        }
    }

    @Autowired Outer outer;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void tables() {
        jdbc.execute("create table if not exists tx_prop_row(id serial primary key, name text not null)");
        jdbc.execute("create table if not exists tx_prop_coupon(code text primary key)");
        jdbc.update("delete from tx_prop_row");
        jdbc.update("delete from tx_prop_coupon");
    }

    private List<String> rows() {
        return jdbc.queryForList("select name from tx_prop_row order by id", String.class);
    }

    @Test
    @DisplayName("합류한 안쪽은 바깥과 같은 커넥션, REQUIRES_NEW 안쪽은 다른 커넥션을 쓴다")
    void connections() {
        List<Integer> pids = outer.pids();

        assertThat(pids.get(1)).isEqualTo(pids.get(0));
        assertThat(pids.get(2)).isNotEqualTo(pids.get(0));
        CiOutput.record(LESSON, "pid", "바깥 트랜잭션의 커넥션(pg_backend_pid): " + pids.get(0)
                + "\nREQUIRED 안쪽: " + pids.get(1) + "\nREQUIRES_NEW 안쪽: " + pids.get(2));
    }

    @Test
    @DisplayName("같은 메서드 안에서 예외를 잡고 삼키면 커밋되어 반쯤 된 변경이 남는다. 다시 던지거나 setRollbackOnly면 롤백된다")
    void swallowCommits() {
        outer.transferSwallow();
        List<String> swallow = rows();
        jdbc.update("delete from tx_prop_row");
        outer.transferRollbackOnly();
        List<String> rollbackOnly = rows();
        jdbc.update("delete from tx_prop_row");
        assertThatThrownBy(() -> outer.transferRethrow()).isInstanceOf(IllegalStateException.class);
        List<String> rethrow = rows();

        assertThat(swallow).containsExactly("출금");
        assertThat(rollbackOnly).isEmpty();
        assertThat(rethrow).isEmpty();
        CiOutput.record(LESSON, "swallow", "catch에서 삼킴 → 남은 행 " + swallow + " (출금만 커밋됨)"
                + "\ncatch에서 setRollbackOnly() → 남은 행 " + rollbackOnly
                + "\ncatch에서 다시 던짐 → 남은 행 " + rethrow);
    }

    @Test
    @DisplayName("PostgreSQL에서는 트랜잭션 안의 SQL 오류를 자바에서 삼켜도 그 트랜잭션의 변경이 남지 않는다")
    void postgresAbortedTransaction() {
        Throwable thrown = null;
        try {
            outer.transferSwallowSqlError();
        } catch (Exception e) {
            thrown = e;
        }

        assertThat(rows()).isEmpty();
        CiOutput.record(LESSON, "pg-aborted", "출금 insert 뒤 입금 insert가 NOT NULL 위반, 자바에서 catch하고 정상 종료\n호출한 쪽이 받은 예외: "
                + (thrown == null ? "없음" : thrown.getClass().getSimpleName()) + "\n남은 행: " + rows());
    }

    @Test
    @DisplayName("합류한 안쪽 @Transactional 메서드가 던진 중복 예외를 잡아도 UnexpectedRollbackException이 나고 모두 롤백된다")
    void caughtInnerExceptionStillRollsBack() {
        jdbc.update("insert into tx_prop_coupon(code) values ('WELCOME')");

        assertThatThrownBy(() -> outer.issueIgnoringDuplicate()).isInstanceOf(UnexpectedRollbackException.class)
                .satisfies(e -> CiOutput.record(LESSON, "duplicate", "안쪽 insertCoupon(@Transactional)에서 중복 키 → 바깥이 catch\n→ "
                        + e.getClass().getSimpleName() + ": " + e.getMessage().lines().findFirst().orElse("")
                        + "\n남은 행: " + rows()));
        assertThat(rows()).isEmpty();
    }

    @Test
    @DisplayName("noRollbackFor에 적은 런타임 예외는 합류한 안쪽에서 나도 롤백 전용을 표시하지 않아, 바깥이 잡으면 둘 다 커밋된다")
    void noRollbackFor() {
        outer.callQuiet();

        assertThat(rows()).containsExactly("outer", "inner");
        CiOutput.record(LESSON, "no-rollback-for", "안쪽 @Transactional(noRollbackFor = QuietException.class)이 QuietException\n바깥이 catch → 남은 행 " + rows());
    }
}
