package lab.devlessons.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * @Audited 메서드의 전후 값을 감사 로그로 남긴다.
 * 트랜잭션 어드바이스(order 0, AuditTxConfig)보다 안쪽(order 10)에서 실행되어 본 작업 트랜잭션 안에서 기록한다.
 */
@Aspect
@Component
@Order(10)
public class AuditAspect {

    private final AuditWriter writer;
    private final LimitAccountRepository accounts;

    public AuditAspect(AuditWriter writer, LimitAccountRepository accounts) {
        this.writer = writer;
        this.accounts = accounts;
    }

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        Long targetId = (Long) pjp.getArgs()[0];
        String before = snapshot(targetId);
        try {
            Object result = pjp.proceed();
            writer.writeInCurrentTx(new AuditLog(CurrentActor.get(), audited.action(), targetId, before, snapshot(targetId), "SUCCESS", null));
            return result;
        } catch (RuntimeException e) {
            AuditLog failure = new AuditLog(CurrentActor.get(), audited.action(), targetId, before, null, "FAILED", e.getMessage());
            if (audited.failureInNewTx()) {
                writer.writeInNewTx(failure);
            } else {
                writer.writeInCurrentTx(failure); // 곧 롤백될 트랜잭션에 합류 → 함께 사라진다
            }
            throw e;
        }
    }

    private String snapshot(Long id) {
        return accounts.findById(id).map(a -> "dailyLimit=" + a.getDailyLimit()).orElse("없음");
    }
}
