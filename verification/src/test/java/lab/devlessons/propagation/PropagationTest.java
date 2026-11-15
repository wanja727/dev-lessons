package lab.devlessons.propagation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.UnexpectedRollbackException;

/** 레슨 "트랜잭션 전파와 롤백 규칙"의 주장을 검증한다. 테스트에는 트랜잭션이 없다. */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class PropagationTest {

    @Autowired OuterService outer;
    @Autowired InnerService inner;
    @Autowired PropRecordRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("REQUIRED: 안쪽 예외를 바깥에서 잡아도 rollback-only가 되어 커밋 시 UnexpectedRollbackException, 전부 롤백")
    void requiredFailureMarksRollbackOnly() {
        assertThatThrownBy(() -> outer.catchRequiredFailure())
                .isInstanceOf(UnexpectedRollbackException.class);

        assertThat(repository.findAllNames()).isEmpty();
    }

    @Test
    @DisplayName("REQUIRES_NEW: 안쪽만 롤백되고, 예외를 잡은 바깥은 정상 커밋")
    void requiresNewFailureRollsBackOnlyInner() {
        outer.catchRequiresNewFailure();

        assertThat(repository.findAllNames()).containsExactly("outer");
    }

    @Test
    @DisplayName("REQUIRES_NEW: 안쪽이 먼저 커밋되면 바깥이 롤백되어도 안쪽 행은 남는다")
    void requiresNewCommitSurvivesOuterRollback() {
        assertThatThrownBy(() -> outer.failAfterRequiresNew())
                .isInstanceOf(IllegalStateException.class);

        assertThat(repository.findAllNames()).containsExactly("inner");
    }

    @Test
    @DisplayName("REQUIRES_NEW는 별도 커넥션의 별도 트랜잭션: 바깥이 flush만 한 행은 보이지 않는다")
    void requiresNewIsSeparateTransaction() {
        long seenInside = outer.countFromRequiresNewBeforeCommit();

        assertThat(seenInside).isZero();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("체크 예외는 기본적으로 롤백하지 않는다: 예외가 나도 저장한 행은 커밋된다")
    void checkedExceptionCommitsByDefault() {
        assertThatThrownBy(() -> inner.saveAndThrowChecked("checked"))
                .isInstanceOf(Exception.class)
                .isNotInstanceOf(RuntimeException.class);

        assertThat(repository.findAllNames()).containsExactly("checked");
    }

    @Test
    @DisplayName("rollbackFor = Exception.class를 주면 체크 예외에도 롤백한다")
    void rollbackForMakesCheckedRollback() {
        assertThatThrownBy(() -> inner.saveAndThrowCheckedWithRollbackFor("checked"))
                .isInstanceOf(Exception.class);

        assertThat(repository.findAllNames()).isEmpty();
    }

    @Test
    @DisplayName("런타임 예외는 기본적으로 롤백한다")
    void runtimeExceptionRollsBack() {
        assertThatThrownBy(() -> inner.saveAndFail("runtime"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(repository.findAllNames()).isEmpty();
    }
}
