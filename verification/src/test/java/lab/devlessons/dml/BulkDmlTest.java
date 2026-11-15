package lab.devlessons.dml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 레슨 "대량 DML 안전하게 하기"의 주장을 PostgreSQL에서 검증한다.
 * 주문 10,000건 중 홀수 id 5,000건이 'PENDING'이고, 이를 'EXPIRED'로 바꾸는 작업을 가정한다.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class BulkDmlTest {

    private static final String TARGET = "status = 'PENDING'";
    private static final int TARGET_COUNT = 5_000;

    @Autowired DataSource dataSource;

    @BeforeEach
    void setUp() throws SQLException {
        exec("drop table if exists dml_orders_backup");
        exec("drop table if exists dml_orders");
        exec("create table dml_orders (id int primary key, status text not null)");
        exec("insert into dml_orders select g, case when g % 2 = 1 then 'PENDING' else 'PAID' end from generate_series(1, 10000) g");
    }

    @Test
    @DisplayName("1. 백업 테이블: 대상 행을 그대로 복사해 두고, 백업 건수가 대상 건수와 같은지 확인한다")
    void backupMatchesTarget() throws SQLException {
        int before = count("select count(*) from dml_orders where " + TARGET);

        exec("create table dml_orders_backup as select * from dml_orders where " + TARGET);

        assertThat(before).isEqualTo(TARGET_COUNT);
        assertThat(count("select count(*) from dml_orders_backup")).isEqualTo(before);
    }

    @Test
    @DisplayName("2. 전후 건수 비교: UPDATE가 바꾼 건수 = 사전에 센 대상 건수, 실행 후 대상 조건은 0건")
    void beforeAfterCounts() throws SQLException {
        int before = count("select count(*) from dml_orders where " + TARGET);

        int updated = update("update dml_orders set status = 'EXPIRED' where " + TARGET);

        assertThat(updated).isEqualTo(before);
        assertThat(count("select count(*) from dml_orders where " + TARGET)).isZero();
        assertThat(count("select count(*) from dml_orders where status = 'EXPIRED'")).isEqualTo(before);
    }

    @Test
    @DisplayName("3. 한 트랜잭션으로 몰아서 하다 끝에서 실패하면 전부 롤백된다 (진행한 것도 0)")
    void singleTransactionRollsBackEverything() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                s.executeUpdate("update dml_orders set status = 'EXPIRED' where " + TARGET);
                assertThatThrownBy(() -> s.executeUpdate("update dml_orders set id = 1 where id = 2")) // 마지막 단계에서 오류
                        .isInstanceOf(SQLException.class);
            }
            c.rollback();
        }
        assertThat(count("select count(*) from dml_orders where status = 'EXPIRED'")).isZero();
    }

    @Test
    @DisplayName("4. 분할 커밋: id 1,000개 범위씩 커밋하다 네 번째 묶음에서 실패하면 앞의 세 묶음(1,500건)은 남는다")
    void splitCommitKeepsEarlierBatches() throws SQLException {
        int done = splitUpdate(1_000, 4);

        assertThat(done).isEqualTo(1_500); // id 1~3000 중 홀수
        assertThat(count("select count(*) from dml_orders where status = 'EXPIRED'")).isEqualTo(1_500);
    }

    @Test
    @DisplayName("5. 대상 조건(status = 'PENDING')을 WHERE에 두면 분할 작업을 처음부터 다시 돌려도 안전하다 (재실행 가능)")
    void rerunIsSafe() throws SQLException {
        splitUpdate(1_000, 4);           // 중간에 실패
        int secondRun = splitUpdate(1_000, 0); // 처음부터 다시

        assertThat(secondRun).isEqualTo(TARGET_COUNT - 1_500); // 이미 바뀐 행은 조건에 안 걸린다
        assertThat(count("select count(*) from dml_orders where status = 'EXPIRED'")).isEqualTo(TARGET_COUNT);
        assertThat(count("select count(*) from dml_orders where " + TARGET)).isZero();
    }

    @Test
    @DisplayName("6. 원복 스크립트: 백업 테이블과 id로 조인해 되돌리면 원래 값과 모두 같아진다")
    void restoreFromBackup() throws SQLException {
        exec("create table dml_orders_backup as select * from dml_orders where " + TARGET);
        update("update dml_orders set status = 'EXPIRED' where " + TARGET);

        int restored = update("update dml_orders o set status = b.status from dml_orders_backup b where o.id = b.id");

        assertThat(restored).isEqualTo(TARGET_COUNT);
        assertThat(count("select count(*) from dml_orders o join dml_orders_backup b on o.id = b.id where o.status <> b.status")).isZero();
        assertThat(count("select count(*) from dml_orders where " + TARGET)).isEqualTo(TARGET_COUNT);
    }

    @Test
    @DisplayName("7. 커밋 전 대량 UPDATE는 대상 행을 잠근다: 그동안 다른 요청의 같은 행 수정은 기다리다 lock_timeout으로 실패")
    void longTransactionBlocksOthers() throws SQLException {
        try (Connection big = dataSource.getConnection(); Connection user = dataSource.getConnection()) {
            big.setAutoCommit(false);
            try (Statement s = big.createStatement()) {
                s.executeUpdate("update dml_orders set status = 'EXPIRED' where " + TARGET); // 커밋 전
            }

            user.setAutoCommit(false);
            try (Statement s = user.createStatement()) {
                s.execute("set local lock_timeout = '300ms'"); // 이 트랜잭션에서만 (풀의 커넥션에 남지 않게)
                assertThatThrownBy(() -> s.executeUpdate("update dml_orders set status = 'PAID' where id = 1"))
                        .isInstanceOf(SQLException.class)
                        .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("55P03")); // lock_not_available
            }
            user.rollback();
            big.rollback();
        }
    }

    /**
     * id 범위 batchSize씩 나눠 UPDATE하고 매번 커밋한다. failAtBatch번째 묶음(1부터)에서 예외를 낸다 (0이면 실패 없음).
     * @return 이번 실행에서 바꾼 건수
     */
    private int splitUpdate(int batchSize, int failAtBatch) throws SQLException {
        int changed = 0;
        int batch = 0;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "update dml_orders set status = 'EXPIRED' where id between ? and ? and " + TARGET)) {
            c.setAutoCommit(false);
            for (int from = 1; from <= 10_000; from += batchSize) {
                batch++;
                if (batch == failAtBatch) {
                    c.rollback();
                    return changed; // 이 묶음에서 장애가 났다고 가정
                }
                ps.setInt(1, from);
                ps.setInt(2, from + batchSize - 1);
                changed += ps.executeUpdate();
                c.commit();
            }
        }
        return changed;
    }

    private void exec(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private int update(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            return s.executeUpdate(sql);
        }
    }

    private int count(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
