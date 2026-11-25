package lab.devlessons.aggregate;

import static lab.devlessons.support.Jdbc.error;
import static lab.devlessons.support.Jdbc.exec;
import static lab.devlessons.support.Jdbc.rows;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
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
 * 레슨 "집계와 서브쿼리"(D3)의 주장을 PostgreSQL 16에서 검증한다.
 * 회원 4명(kim, lee, park, choi), 주문 8건. kim만 취소한 적이 있고, 회원이 지워진 취소 주문 하나는 member_id가 NULL이다.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class AggregateSubqueryTest {

    private static final String LESSON = "aggregate-subquery";

    @Autowired DataSource ds;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        exec(ds,
                "drop table if exists ag_orders, ag_member cascade",
                "create table ag_member (id int primary key, name text not null)",
                "create table ag_orders (id int primary key, member_id int references ag_member(id), "
                        + "status text not null, amount int not null, coupon_id int)",
                "insert into ag_member values (1, 'kim'), (2, 'lee'), (3, 'park'), (4, 'choi')",
                "insert into ag_orders values "
                        + "(1, 1, 'PAID', 60000, 10), (2, 1, 'PAID', 70000, null), (3, 1, 'CANCELED', 5000, null), "
                        + "(4, 2, 'PAID', 30000, 11), (5, 2, 'PAID', 20000, null), (6, 3, 'PAID', 90000, null), "
                        + "(7, 4, 'PAID', 8000, 12), (8, null, 'CANCELED', 1000, null)");
    }

    @Test
    @DisplayName("group by member_id는 회원마다 한 행을 내고, 묶지 않은 열을 SELECT에 쓰면 42803 오류")
    void groupBy() {
        List<String> result = rows(ds, "select member_id, count(*), sum(amount) from ag_orders where status = 'PAID' "
                + "group by member_id order by member_id");
        String bad = error(ds, "select member_id, amount from ag_orders group by member_id");

        assertThat(result).containsExactly("1 | 2 | 130000", "2 | 2 | 50000", "3 | 1 | 90000", "4 | 1 | 8000");
        assertThat(bad).startsWith("42803");
        CiOutput.record(LESSON, "group-by", "(member_id | count | sum)\n" + String.join("\n", result)
                + "\n\n묶지 않은 amount를 SELECT에 쓰면:\n" + bad);
    }

    @Test
    @DisplayName("count(*)는 행을, count(coupon_id)는 NULL이 아닌 값만 센다. 맞는 행이 없으면 sum은 NULL이다")
    void countAndNull() {
        List<String> counts = rows(ds, "select count(*), count(coupon_id) from ag_orders");
        List<String> emptySum = rows(ds, "select sum(amount), coalesce(sum(amount), 0) from ag_orders where status = 'REFUNDED'");
        Long boxed = jdbc.queryForObject("select sum(amount) from ag_orders where status = 'REFUNDED'", Long.class);

        assertThat(counts).containsExactly("8 | 3");
        assertThat(emptySum).containsExactly("NULL | 0");
        assertThat(boxed).isNull();
        CiOutput.record(LESSON, "count-null", "count(*) | count(coupon_id)\n" + counts.get(0)
                + "\n\n맞는 행이 없을 때 sum | coalesce(sum, 0)\n" + emptySum.get(0)
                + "\nLong으로 받은 sum: " + boxed);
    }

    @Test
    @DisplayName("WHERE에 집계 함수를 쓰면 42803 오류이고 HAVING은 된다. SELECT 별칭은 HAVING에서 못 쓰고 ORDER BY에서는 쓴다")
    void whereVsHaving() {
        String inWhere = error(ds, "select member_id, sum(amount) from ag_orders where sum(amount) > 100000 group by member_id");
        List<String> having = rows(ds, "select member_id, sum(amount) from ag_orders where status = 'PAID' "
                + "group by member_id having sum(amount) > 100000");
        String aliasInHaving = error(ds, "select member_id, sum(amount) as total from ag_orders group by member_id having total > 100000");
        List<String> aliasInOrder = rows(ds, "select member_id, sum(amount) as total from ag_orders where status = 'PAID' "
                + "group by member_id order by total desc limit 1");

        assertThat(inWhere).startsWith("42803");
        assertThat(having).containsExactly("1 | 130000");
        assertThat(aliasInHaving).startsWith("42703");
        assertThat(aliasInOrder).containsExactly("1 | 130000");
        CiOutput.record(LESSON, "having", String.join("\n",
                "[WHERE sum(amount) > 100000]", inWhere, "",
                "[HAVING sum(amount) > 100000] (member_id | sum)", String.join("\n", having), "",
                "[HAVING total > 100000] 별칭을 쓰면", aliasInHaving));
    }

    @Test
    @DisplayName("NOT IN의 서브쿼리 결과에 NULL이 있으면 0행, NOT EXISTS는 3명. NULL을 빼면 NOT IN도 3명")
    void notInWithNull() {
        List<String> notIn = rows(ds, "select name from ag_member where id not in "
                + "(select member_id from ag_orders where status = 'CANCELED')");
        List<String> notExists = rows(ds, "select name from ag_member m where not exists "
                + "(select 1 from ag_orders o where o.member_id = m.id and o.status = 'CANCELED') order by id");
        List<String> notInWithoutNull = rows(ds, "select name from ag_member where id not in "
                + "(select member_id from ag_orders where status = 'CANCELED' and member_id is not null) order by id");

        assertThat(notIn).isEmpty();
        assertThat(notExists).containsExactly("lee", "park", "choi");
        assertThat(notInWithoutNull).containsExactly("lee", "park", "choi");
        CiOutput.record(LESSON, "not-in", String.join("\n",
                "[NOT IN] " + notIn.size() + "행 " + notIn,
                "[NOT EXISTS] " + notExists.size() + "행 " + notExists,
                "[NOT IN, 서브쿼리에서 NULL을 뺐을 때] " + notInWithoutNull.size() + "행 " + notInWithoutNull));
    }

    @Test
    @DisplayName("스칼라 서브쿼리는 회원마다 주문 수를 붙인다")
    void scalarSubquery() {
        assertThat(rows(ds, "select name, (select count(*) from ag_orders o where o.member_id = m.id) from ag_member m order by id"))
                .containsExactly("kim | 3", "lee | 2", "park | 1", "choi | 1");
    }

    @Test
    @DisplayName("'5만 원 넘게 주문한 적 있는 회원'을 조인으로 구하면 kim이 두 번 나오고 EXISTS는 한 번이다")
    void joinVsExists() {
        List<String> join = rows(ds, "select m.name from ag_member m join ag_orders o on o.member_id = m.id "
                + "where o.amount > 50000 order by m.id");
        List<String> exists = rows(ds, "select m.name from ag_member m where exists "
                + "(select 1 from ag_orders o where o.member_id = m.id and o.amount > 50000) order by m.id");

        assertThat(join).containsExactly("kim", "kim", "park");
        assertThat(exists).containsExactly("kim", "park");
        CiOutput.record(LESSON, "join-exists", "[조인] " + join + "\n[EXISTS] " + exists);
    }

    @Test
    @DisplayName("윈도 함수: row_number로 회원마다 가장 큰 주문 하나를 고른다(행을 줄이지 않고 순위를 붙인 뒤 거른다)")
    void window() {
        List<String> top = rows(ds, "select member_id, id, amount from ("
                + "select member_id, id, amount, row_number() over (partition by member_id order by amount desc) rn "
                + "from ag_orders where member_id is not null) t where rn = 1 order by member_id");
        assertThat(top).containsExactly("1 | 2 | 70000", "2 | 4 | 30000", "3 | 6 | 90000", "4 | 7 | 8000");
    }

    @Test
    @DisplayName("자바에서 모두 읽어 합친 결과와 GROUP BY 결과가 같고, 옮긴 행은 주문 수 대 회원 수다")
    void javaVsGroupBy() {
        exec(ds, "drop table if exists ag_big", "create table ag_big (id int primary key, member_id int not null, amount int not null)",
                "insert into ag_big select g, 1 + g % 1000, 1000 + g % 50000 from generate_series(1, 200000) g");

        List<Map<String, Object>> all = jdbc.queryForList("select member_id, amount from ag_big");
        Map<Integer, Long> inJava = all.stream().collect(Collectors.groupingBy(
                r -> (Integer) r.get("member_id"), TreeMap::new, Collectors.summingLong(r -> (Integer) r.get("amount"))));
        List<Map<String, Object>> grouped = jdbc.queryForList("select member_id, sum(amount) total from ag_big group by member_id");
        Map<Integer, Long> inDb = new TreeMap<>();
        grouped.forEach(r -> inDb.put((Integer) r.get("member_id"), ((Number) r.get("total")).longValue()));

        assertThat(inDb).isEqualTo(inJava);
        CiOutput.record(LESSON, "java-vs-db", String.join("\n",
                "[findAll 뒤 자바 groupingBy] DB에서 받은 행: " + all.size(),
                "[GROUP BY] DB에서 받은 행: " + grouped.size(),
                "두 결과가 같은가: " + inDb.equals(inJava)));
    }
}
