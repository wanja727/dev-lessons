package lab.devlessons.enumlesson;

import static org.assertj.core.api.Assertions.assertThat;

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
 * 레슨 "enum"(J8)의 실무 구역: @Enumerated가 없으면 숫자(ordinal)가, STRING이면 이름이 저장된다.
 * 숫자로 저장한 행을 상수를 끼워 넣은 enum으로 읽으면 다른 상태가 된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
class EnumJpaTest {

    @Autowired TestEntityManager em;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("@Enumerated가 없으면 열에 숫자가, EnumType.STRING이면 이름이 저장된다")
    void storedValue() {
        Long ordinalId = em.persistAndFlush(new OrdinalOrder(OrderStatus.SHIPPED)).getId();
        Long stringId = em.persistAndFlush(new StringOrder(OrderStatus.SHIPPED)).getId();
        em.clear();

        Object ordinalColumn = jdbc.queryForObject("select status from enum_order where id = ?", Object.class, ordinalId);
        Object stringColumn = jdbc.queryForObject("select status from enum_order_str where id = ?", Object.class, stringId);

        assertThat(String.valueOf(ordinalColumn)).isEqualTo("2");
        assertThat(String.valueOf(stringColumn)).isEqualTo("SHIPPED");
        CiOutput.record("enum", "jpa-stored", """
                SHIPPED를 저장한 뒤 열의 값
                @Enumerated 없음        → %s
                @Enumerated(STRING)     → %s""".formatted(ordinalColumn, stringColumn));
    }

    @Test
    @DisplayName("숫자로 저장한 SHIPPED를, PREPARING을 끼워 넣은 enum으로 읽으면 PREPARING이 된다")
    void insertedConstantChangesMeaning() {
        Long id = em.persistAndFlush(new OrdinalOrder(OrderStatus.SHIPPED)).getId();
        em.clear();

        OrderStatusV2 readBack = em.find(OrdinalOrderV2.class, id).getStatus();
        Object column = jdbc.queryForObject("select status from enum_order where id = ?", Object.class, id);

        assertThat(readBack).isEqualTo(OrderStatusV2.PREPARING);
        CiOutput.record("enum", "jpa-shift", """
                저장할 때의 상태 → SHIPPED
                열의 값          → %s
                PREPARING을 끼워 넣은 enum으로 읽은 상태 → %s""".formatted(column, readBack));
    }
}
