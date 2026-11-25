package lab.devlessons.join;

import static lab.devlessons.support.Jdbc.exec;
import static lab.devlessons.support.Jdbc.number;
import static lab.devlessons.support.Jdbc.rows;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 레슨 "조인"(D2)의 주장을 PostgreSQL 16에서 검증한다.
 * 회원 4명(kim, lee, park, choi), 주문 5건. choi는 주문이 없고, 주문 5는 member_id가 NULL이다.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class SqlJoinTest {

    private static final String LESSON = "sql-join";

    @Autowired DataSource ds;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        exec(ds,
                "drop table if exists jn_payment, jn_order_item, jn_orders, jn_member cascade",
                "create table jn_member (id int primary key, name text not null)",
                "create table jn_orders (id int primary key, member_id int references jn_member(id), status text not null, amount int not null)",
                "create table jn_order_item (id int primary key, order_id int not null references jn_orders(id), product text not null)",
                "create table jn_payment (id int primary key, order_id int not null references jn_orders(id), amount int not null)",
                "insert into jn_member values (1, 'kim'), (2, 'lee'), (3, 'park'), (4, 'choi')",
                "insert into jn_orders values (1, 1, 'PAID', 10000), (2, 1, 'CANCELED', 5000), (3, 2, 'PAID', 7000), "
                        + "(4, 3, 'PAID', 3000), (5, null, 'PAID', 2000)",
                "insert into jn_order_item values (1, 1, 'pen'), (2, 1, 'note'), (3, 1, 'ink')",
                "insert into jn_payment values (1, 1, 6000), (2, 1, 4000)");
    }

    private static final String INNER = "select m.name, o.id from jn_member m join jn_orders o on o.member_id = m.id order by o.id";
    private static final String LEFT = "select m.name, o.id from jn_member m left join jn_orders o on o.member_id = m.id order by m.id, o.id";
    private static final String FULL = "select m.name, o.id from jn_member m full join jn_orders o on o.member_id = m.id order by o.id nulls last";

    @Test
    @DisplayName("INNER JOIN은 짝이 있는 4행만, LEFT JOIN은 choi까지 5행, FULL OUTER JOIN은 회원 없는 주문까지 6행")
    void joinKinds() {
        List<String> inner = rows(ds, INNER);
        List<String> left = rows(ds, LEFT);
        List<String> full = rows(ds, FULL);

        assertThat(inner).containsExactly("kim | 1", "kim | 2", "lee | 3", "park | 4");
        assertThat(left).hasSize(5).contains("choi | NULL");
        assertThat(full).hasSize(6).contains("NULL | 5", "choi | NULL");
        CiOutput.record(LESSON, "inner", "(name | order id)\n" + String.join("\n", inner) + "\n→ " + inner.size() + "행");
        CiOutput.record(LESSON, "left", "(name | order id)\n" + String.join("\n", left) + "\n→ " + left.size() + "행");
        CiOutput.record(LESSON, "full", "(name | order id)\n" + String.join("\n", full) + "\n→ " + full.size() + "행");
    }

    @Test
    @DisplayName("주문이 없는 회원은 LEFT JOIN 뒤 오른쪽 키가 NULL인 행으로 찾는다")
    void noOrders() {
        assertThat(rows(ds, "select m.name from jn_member m left join jn_orders o on o.member_id = m.id where o.id is null"))
                .containsExactly("choi");
    }

    @Test
    @DisplayName("LEFT JOIN의 오른쪽 조건을 WHERE에 두면 choi가 빠져 INNER JOIN처럼 되고, ON에 두면 남는다")
    void leftJoinWhereVsOn() {
        List<String> where = rows(ds, "select m.name, o.id from jn_member m left join jn_orders o on o.member_id = m.id "
                + "where o.status = 'PAID' order by m.id");
        List<String> on = rows(ds, "select m.name, o.id from jn_member m left join jn_orders o on o.member_id = m.id "
                + "and o.status = 'PAID' order by m.id");

        assertThat(where).containsExactly("kim | 1", "lee | 3", "park | 4");
        assertThat(on).containsExactly("kim | 1", "lee | 3", "park | 4", "choi | NULL");
        CiOutput.record(LESSON, "where-on", "[조건을 WHERE에]\n" + String.join("\n", where)
                + "\n\n[조건을 ON에]\n" + String.join("\n", on));
    }

    @Test
    @DisplayName("주문 하나에 항목 3개와 결제 2건을 함께 조인하면 6행이 되고 결제 합계가 3배(30000)가 된다. 먼저 합치고 붙이면 10000")
    void rowsMultiply() {
        long joinedRows = number(ds, "select count(*) from jn_orders o join jn_order_item i on i.order_id = o.id "
                + "join jn_payment p on p.order_id = o.id where o.id = 1");
        List<String> wrong = rows(ds, "select o.id, sum(p.amount) from jn_orders o join jn_order_item i on i.order_id = o.id "
                + "join jn_payment p on p.order_id = o.id group by o.id");
        List<String> fixed = rows(ds, "select o.id, pay.total, (select count(*) from jn_order_item i where i.order_id = o.id) items "
                + "from jn_orders o join (select order_id, sum(amount) total from jn_payment group by order_id) pay "
                + "on pay.order_id = o.id");
        long real = number(ds, "select sum(amount) from jn_payment where order_id = 1");

        assertThat(joinedRows).isEqualTo(6);
        assertThat(wrong).containsExactly("1 | 30000");
        assertThat(fixed).containsExactly("1 | 10000 | 3");
        assertThat(real).isEqualTo(10000);
        CiOutput.record(LESSON, "multiply", String.join("\n",
                "조인한 행 수: " + joinedRows,
                "항목과 결제를 함께 조인한 합계 (order id | sum): " + wrong.get(0),
                "결제 테이블만 더한 실제 합계: " + real,
                "먼저 합친 뒤 붙인 결과 (order id | total | items): " + fixed.get(0)));
    }

    @Test
    @DisplayName("조인 조건 없이 붙이면 4 × 5 = 20행(CROSS JOIN)")
    void crossJoin() {
        assertThat(number(ds, "select count(*) from jn_member cross join jn_orders")).isEqualTo(20);
    }

    @Test
    @DisplayName("SELF JOIN으로 추천인 이름을 붙인다")
    void selfJoin() {
        exec(ds, "alter table jn_member add column referrer_id int references jn_member(id)",
                "update jn_member set referrer_id = 1 where id in (2, 3)");
        assertThat(rows(ds, "select m.name, r.name from jn_member m join jn_member r on r.id = m.referrer_id order by m.id"))
                .containsExactly("lee | kim", "park | kim");
    }

    @Test
    @DisplayName("주문마다 회원을 다시 읽으면 쿼리가 1 + 주문 수 번이고, 조인은 1번이다. 결과는 같다")
    void nPlusOne() {
        exec(ds, "drop table if exists jn_big_orders, jn_big_member",
                "create table jn_big_member (id int primary key, name text)",
                "create table jn_big_orders (id int primary key, member_id int references jn_big_member(id))",
                "insert into jn_big_member select g, 'm' || g from generate_series(1, 100) g",
                "insert into jn_big_orders select g, 1 + g % 100 from generate_series(1, 300) g");
        // 데워 두기
        jdbc.queryForList("select * from jn_big_orders");

        long t0 = System.nanoTime();
        int queries = 0;
        List<Map<String, Object>> orders = jdbc.queryForList("select id, member_id from jn_big_orders order by id");
        queries++;
        List<String> fromLoop = new ArrayList<>();
        for (Map<String, Object> o : orders) {
            String name = jdbc.queryForObject("select name from jn_big_member where id = ?", String.class, o.get("member_id"));
            queries++;
            fromLoop.add(o.get("id") + " " + name);
        }
        long loopMs = (System.nanoTime() - t0) / 1_000_000;

        long t1 = System.nanoTime();
        List<String> fromJoin = jdbc.query("select o.id, m.name from jn_big_orders o join jn_big_member m on m.id = o.member_id order by o.id",
                (rs, i) -> rs.getInt(1) + " " + rs.getString(2));
        long joinMs = (System.nanoTime() - t1) / 1_000_000;

        assertThat(queries).isEqualTo(301);
        assertThat(fromJoin).isEqualTo(fromLoop);
        CiOutput.record(LESSON, "n-plus-one", String.join("\n",
                "[주문마다 회원 조회] 쿼리 " + queries + "번, " + loopMs + "ms",
                "[조인 한 번] 쿼리 1번, " + joinMs + "ms",
                "두 결과가 같은가: " + fromJoin.equals(fromLoop)));
    }

    @Test
    @DisplayName("JdbcTemplate으로 조인 결과를 record로 옮기고, 회원별로 묶으면 회원 하나에 주문 리스트가 된다")
    void rowMapperAndGrouping() {
        record OrderRow(long id, int amount, String name) {
        }
        List<OrderRow> rows = jdbc.query("select o.id, o.amount, m.name from jn_orders o join jn_member m on m.id = o.member_id order by o.id",
                (rs, i) -> new OrderRow(rs.getLong("id"), rs.getInt("amount"), rs.getString("name")));
        Map<String, List<Long>> byMember = new LinkedHashMap<>();
        for (OrderRow r : rows) {
            byMember.computeIfAbsent(r.name(), k -> new ArrayList<>()).add(r.id());
        }

        assertThat(rows).hasSize(4);
        assertThat(byMember).containsEntry("kim", List.of(1L, 2L)).hasSize(3);
        CiOutput.record(LESSON, "row-mapper", String.join("\n", rows.stream().map(Object::toString).toList())
                + "\n\n회원별로 묶은 결과: " + byMember);
    }
}
