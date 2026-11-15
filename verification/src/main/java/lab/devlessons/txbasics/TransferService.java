package lab.devlessons.txbasics;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계좌 이체: 받는 쪽에 먼저 더하고, 보내는 쪽에서 뺀다.
 * 보내는 쪽 잔액이 모자라면 CHECK (balance >= 0) 제약 때문에 두 번째 UPDATE가 실패한다.
 */
@Service
public class TransferService {

    private final JdbcTemplate jdbc;

    public TransferService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 트랜잭션 없이: 문장마다 바로 커밋된다 (자동 커밋) */
    public void transferWithoutTransaction(long from, long to, int amount) {
        jdbc.update("UPDATE tb_account SET balance = balance + ? WHERE id = ?", amount, to);
        jdbc.update("UPDATE tb_account SET balance = balance - ? WHERE id = ?", amount, from);
    }

    /** 한 트랜잭션으로: 둘 다 되거나 둘 다 안 된다 */
    @Transactional
    public void transfer(long from, long to, int amount) {
        jdbc.update("UPDATE tb_account SET balance = balance + ? WHERE id = ?", amount, to);
        jdbc.update("UPDATE tb_account SET balance = balance - ? WHERE id = ?", amount, from);
    }
}
