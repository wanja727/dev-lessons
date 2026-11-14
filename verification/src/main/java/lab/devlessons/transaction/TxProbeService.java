package lab.devlessons.transaction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 메서드 안에서 실제로 트랜잭션이 열려 있는지 확인하는 탐침. 레슨 "@Transactional 동작 원리" 검증용. */
@Service
public class TxProbeService {

    private final TransactionTemplate transactionTemplate;

    public TxProbeService(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional
    public boolean annotated() {
        return active();
    }

    /** @Transactional이 없는 메서드가 같은 객체의 @Transactional 메서드를 부른다 */
    public boolean callAnnotatedInternally() {
        return annotated();
    }

    public boolean notAnnotated() {
        return active();
    }

    /** 프로그래밍 방식: 프록시 없이 직접 트랜잭션 경계를 연다 */
    public boolean withTemplate() {
        Boolean result = transactionTemplate.execute(status -> active());
        return Boolean.TRUE.equals(result);
    }

    private static boolean active() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }
}
