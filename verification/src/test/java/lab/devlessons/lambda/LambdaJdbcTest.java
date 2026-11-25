package lab.devlessons.lambda;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 레슨 "람다와 함수형 인터페이스"(J12)의 실무 구역: JdbcTemplate.query에 넘긴 람다는 결과 행마다 한 번씩 불린다.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
class LambdaJdbcTest {

    @Autowired JdbcTemplate jdbc;

    record OrderRow(long id, int price) {
    }

    @Test
    @DisplayName("JdbcTemplate.query의 RowMapper 람다는 행마다 한 번씩 실행된다")
    void rowMapperCalledPerRow() {
        jdbc.execute("create table lambda_order (id bigint primary key, price int)");
        jdbc.update("insert into lambda_order values (1, 10000), (2, 20000), (3, 5000)");
        AtomicInteger calls = new AtomicInteger();

        List<OrderRow> rows = jdbc.query("select id, price from lambda_order order by id",
                (rs, rowNum) -> {
                    calls.incrementAndGet();
                    return new OrderRow(rs.getLong("id"), rs.getInt("price"));
                });

        assertThat(calls.get()).isEqualTo(3);
        assertThat(rows).hasSize(3);
        CiOutput.record("lambda", "jdbc", "행 3개를 조회\n람다가 불린 횟수 → " + calls.get() + "\n결과 → " + rows);
    }
}
