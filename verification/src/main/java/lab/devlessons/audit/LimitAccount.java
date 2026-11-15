package lab.devlessons.audit;

import jakarta.persistence.*;

/** 일일 이체 한도가 있는 계좌. 한도 변경은 감사 대상이다 */
@Entity
@Table(name = "limit_account")
public class LimitAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int dailyLimit;

    protected LimitAccount() {
    }

    public LimitAccount(int dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public void changeLimit(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("한도는 0 이상이어야 합니다");
        }
        this.dailyLimit = limit;
    }

    public Long getId() {
        return id;
    }

    public int getDailyLimit() {
        return dailyLimit;
    }
}
