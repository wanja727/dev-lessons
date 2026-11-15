package lab.devlessons.txbasics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 레슨 "트랜잭션 기초"의 주장을 PostgreSQL 16에서 검증한다.
 * 계좌 1(kim) 잔액 50, 계좌 2(lee) 잔액 0에서 kim이 lee에게 100을 보낸다. kim의 잔액이 모자라 두 번째 UPDATE가 실패한다.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class TransactionBasicsTest {

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransferService transferService;

    @BeforeEach
    void setUp() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS tb_account (id bigint PRIMARY KEY, owner text NOT NULL, "
                + "balance int NOT NULL CHECK (balance >= 0))");
        jdbc.execute("DELETE FROM tb_account");
        jdbc.update("INSERT INTO tb_account VALUES (1, 'kim', 50), (2, 'lee', 0)");
    }

    private int balance(long id) {
        return jdbc.queryForObject("SELECT balance FROM tb_account WHERE id = ?", Integer.class, id);
    }

    private String balances() {
        return "kim " + balance(1) + ", lee " + balance(2) + " (합계 " + (balance(1) + balance(2)) + ")";
    }

    @Test
    @DisplayName("트랜잭션 없이(자동 커밋) 이체하다 두 번째 문장이 실패하면, 첫 문장만 반영되어 없던 돈 100이 생긴다")
    void autoCommitLeavesHalfDone() {
        assertThatThrownBy(() -> transferService.transferWithoutTransaction(1, 2, 100))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(balance(1)).isEqualTo(50);
        assertThat(balance(2)).isEqualTo(100);
        CiOutput.record("tx-basics", "autocommit", String.join("\n",
                "-- 처음: kim 50, lee 0 (합계 50)",
                "UPDATE tb_account SET balance = balance + 100 WHERE id = 2;  -- 성공, 바로 커밋",
                "UPDATE tb_account SET balance = balance - 100 WHERE id = 1;  -- 실패: CHECK (balance >= 0) 위반",
                "-- 결과: " + balances()));
    }

    @Test
    @DisplayName("한 트랜잭션으로 이체하다 두 번째 문장이 실패하면, 첫 문장까지 롤백되어 처음 그대로다")
    void transactionRollsBackAll() {
        assertThatThrownBy(() -> transferService.transfer(1, 2, 100))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(balance(1)).isEqualTo(50);
        assertThat(balance(2)).isZero();
        CiOutput.record("tx-basics", "transaction", String.join("\n",
                "-- 처음: kim 50, lee 0 (합계 50)",
                "BEGIN;",
                "UPDATE tb_account SET balance = balance + 100 WHERE id = 2;  -- 성공 (아직 커밋 전)",
                "UPDATE tb_account SET balance = balance - 100 WHERE id = 1;  -- 실패: CHECK (balance >= 0) 위반",
                "ROLLBACK;",
                "-- 결과: " + balances()));
    }

    @Test
    @DisplayName("잔액이 충분하면 한 트랜잭션의 두 문장이 함께 커밋되고, 합계는 그대로다")
    void transactionCommitsAll() {
        transferService.transfer(1, 2, 30);

        assertThat(balance(1)).isEqualTo(20);
        assertThat(balance(2)).isEqualTo(30);
    }

    @Test
    @DisplayName("커밋 전 변경은 다른 연결에서 보이지 않고, 커밋한 뒤에 보인다. 롤백하면 끝까지 보이지 않는다")
    void uncommittedChangesAreInvisible() throws SQLException {
        StringBuilder log = new StringBuilder();
        try (Connection a = dataSource.getConnection(); Connection b = dataSource.getConnection()) {
            a.setAutoCommit(false);
            exec(a, "UPDATE tb_account SET balance = 999 WHERE id = 1");
            log.append("연결 A: BEGIN; UPDATE ... balance = 999 (커밋 전)\n");

            int seenBefore = read(b);
            log.append("연결 B: SELECT balance → ").append(seenBefore).append('\n');
            assertThat(seenBefore).isEqualTo(50);

            a.commit();
            log.append("연결 A: COMMIT\n");
            int seenAfter = read(b);
            log.append("연결 B: SELECT balance → ").append(seenAfter).append('\n');
            assertThat(seenAfter).isEqualTo(999);

            exec(a, "UPDATE tb_account SET balance = 1 WHERE id = 1");
            log.append("연결 A: UPDATE ... balance = 1 (커밋 전)\n");
            a.rollback();
            log.append("연결 A: ROLLBACK\n");
            int seenAfterRollback = read(b);
            log.append("연결 B: SELECT balance → ").append(seenAfterRollback);
            assertThat(seenAfterRollback).isEqualTo(999);
        }
        CiOutput.record("tx-basics", "visibility", log.toString());
    }

    @Test
    @DisplayName("롤백은 INSERT·UPDATE·DELETE를 모두 되돌린다")
    void rollbackUndoesEverything() throws SQLException {
        try (Connection a = dataSource.getConnection()) {
            a.setAutoCommit(false);
            exec(a, "INSERT INTO tb_account VALUES (3, 'park', 10)");
            exec(a, "UPDATE tb_account SET balance = 0 WHERE id = 1");
            exec(a, "DELETE FROM tb_account WHERE id = 2");
            a.rollback();
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM tb_account", Integer.class)).isEqualTo(2);
        assertThat(balance(1)).isEqualTo(50);
    }

    @Test
    @DisplayName("JDBC 연결은 기본이 자동 커밋이다")
    void autoCommitIsDefault() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            assertThat(c.getAutoCommit()).isTrue();
        }
    }

    private static void exec(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate(sql);
        }
    }

    private static int read(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT balance FROM tb_account WHERE id = 1")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
