package lab.devlessons.numberlimits;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 레슨 "숫자 타입의 한계"(J19)의 실무 구역: NUMERIC(19, 2) 열에 저장한 금액을 다시 읽으면 자릿수가 2가 된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
class NumberLimitsJpaTest {

    @Autowired TestEntityManager em;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("10000을 저장하고 다시 읽으면 10000.00이 되어 equals는 false, compareTo는 0이다")
    void scaleAfterRoundTrip() {
        BigDecimal requested = new BigDecimal("10000");
        Long id = em.persistAndFlush(new PaymentRecord(requested)).getId();
        em.clear();

        BigDecimal stored = em.find(PaymentRecord.class, id).getAmount();
        String columnType = jdbc.queryForObject("""
                select data_type || '(' || numeric_precision || ', ' || numeric_scale || ')'
                from information_schema.columns
                where table_name = 'nl_payment' and column_name = 'amount'""", String.class);

        assertThat(stored.scale()).isEqualTo(2);
        assertThat(stored.toString()).isEqualTo("10000.00");
        assertThat(stored.equals(requested)).isFalse();
        assertThat(stored.compareTo(requested)).isZero();
        CiOutput.record("number-limits", "jpa-scale", """
                열의 타입 → %s
                요청의 금액   → %s
                DB에서 읽은 금액 → %s

                stored.equals(requested)        → %s
                stored.compareTo(requested) == 0 → %s""".formatted(columnType, requested, stored,
                stored.equals(requested), stored.compareTo(requested) == 0));
    }
}
