package lab.devlessons.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 이 메서드를 감사 로그로 남긴다. 첫 번째 인자를 대상 id로 본다. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    String action();

    /** 실패 기록을 별도 트랜잭션(REQUIRES_NEW)으로 남길지. false면 본 작업과 함께 롤백된다 */
    boolean failureInNewTx() default true;
}
