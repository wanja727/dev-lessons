package lab.devlessons.propagation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 바깥 서비스가 부르는 안쪽 서비스. 다른 빈이라 호출이 프록시를 거친다 */
@Service
public class InnerService {

    private final PropRecordRepository repository;

    public InnerService(PropRecordRepository repository) {
        this.repository = repository;
    }

    /** REQUIRED: 바깥 트랜잭션에 합류한 뒤 런타임 예외 */
    @Transactional
    public void saveAndFail(String name) {
        repository.save(new PropRecord(name));
        throw new IllegalStateException("inner 실패");
    }

    /** REQUIRES_NEW: 새 트랜잭션에서 저장한 뒤 런타임 예외 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveNewAndFail(String name) {
        repository.save(new PropRecord(name));
        throw new IllegalStateException("inner 실패");
    }

    /** REQUIRES_NEW: 새 트랜잭션에서 저장하고 바로 커밋 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveNew(String name) {
        repository.save(new PropRecord(name));
    }

    /** REQUIRES_NEW: 새 트랜잭션(다른 커넥션)에서 행 수를 센다 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public long countInNewTx() {
        return repository.count();
    }

    /** 체크 예외: 기본 규칙에서는 롤백하지 않는다 */
    @Transactional
    public void saveAndThrowChecked(String name) throws Exception {
        repository.save(new PropRecord(name));
        throw new Exception("체크 예외");
    }

    /** 체크 예외 + rollbackFor: 롤백한다 */
    @Transactional(rollbackFor = Exception.class)
    public void saveAndThrowCheckedWithRollbackFor(String name) throws Exception {
        repository.save(new PropRecord(name));
        throw new Exception("체크 예외");
    }
}
