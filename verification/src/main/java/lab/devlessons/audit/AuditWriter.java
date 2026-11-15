package lab.devlessons.audit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 감사 로그 저장. 같은 트랜잭션에 합류하거나, 별도 트랜잭션으로 저장한다 */
@Component
public class AuditWriter {

    private final AuditLogRepository repository;

    public AuditWriter(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** 본 작업 트랜잭션에 합류: 본 작업이 롤백되면 함께 롤백된다 */
    @Transactional(propagation = Propagation.REQUIRED)
    public void writeInCurrentTx(AuditLog log) {
        repository.save(log);
    }

    /** 별도 트랜잭션: 본 작업이 롤백되어도 남는다 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeInNewTx(AuditLog log) {
        repository.save(log);
    }
}
