package lab.devlessons.propagation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OuterService {

    private final PropRecordRepository repository;
    private final InnerService inner;

    public OuterService(PropRecordRepository repository, InnerService inner) {
        this.repository = repository;
        this.inner = inner;
    }

    /** 안쪽 REQUIRED의 예외를 잡고 계속 진행한다. 커밋할 수 있을까? */
    @Transactional
    public void catchRequiredFailure() {
        repository.save(new PropRecord("outer"));
        try {
            inner.saveAndFail("inner");
        } catch (IllegalStateException ignored) {
            // 잡았으니 괜찮다고 생각하기 쉽다
        }
    }

    /** 안쪽 REQUIRES_NEW의 예외를 잡고 계속 진행한다 */
    @Transactional
    public void catchRequiresNewFailure() {
        repository.save(new PropRecord("outer"));
        try {
            inner.saveNewAndFail("inner");
        } catch (IllegalStateException ignored) {
            // 안쪽 트랜잭션만 롤백됐다
        }
    }

    /** 안쪽 REQUIRES_NEW가 커밋한 뒤 바깥이 실패한다 */
    @Transactional
    public void failAfterRequiresNew() {
        inner.saveNew("inner");
        repository.save(new PropRecord("outer"));
        throw new IllegalStateException("outer 실패");
    }

    /** 바깥이 저장하고 flush한 뒤(커밋 전), REQUIRES_NEW 안에서 행 수를 센다 */
    @Transactional
    public long countFromRequiresNewBeforeCommit() {
        repository.saveAndFlush(new PropRecord("outer"));
        return inner.countInNewTx();
    }
}
