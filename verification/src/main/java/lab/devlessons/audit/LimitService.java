package lab.devlessons.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LimitService {

    private final LimitAccountRepository accounts;

    public LimitService(LimitAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    @Audited(action = "CHANGE_LIMIT")
    public void changeLimit(Long accountId, int limit) {
        accounts.findById(accountId).orElseThrow().changeLimit(limit);
    }

    /** 실패 기록을 본 작업과 같은 트랜잭션에 남기는 (잘못된) 설정 */
    @Transactional
    @Audited(action = "CHANGE_LIMIT_NAIVE", failureInNewTx = false)
    public void changeLimitNaive(Long accountId, int limit) {
        accounts.findById(accountId).orElseThrow().changeLimit(limit);
    }

    /** 내부 호출: changeLimit의 @Audited가 적용되지 않는다 (트랜잭션은 이 메서드의 것) */
    @Transactional
    public void resetLimit(Long accountId) {
        changeLimit(accountId, 0);
    }
}
