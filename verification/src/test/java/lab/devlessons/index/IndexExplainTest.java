package lab.devlessons.index;

import static lab.devlessons.support.Jdbc.exec;
import static lab.devlessons.support.Jdbc.plan;
import static lab.devlessons.support.Jdbc.rows;
import static org.assertj.core.api.Assertions.assertThat;

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
 * 레슨 "인덱스와 실행계획"(D7)을 다시 쓰며 더한 주장과, 레슨에 붙일 실제 실행계획을 남긴다.
 * 예전 주장은 IndexPlanTest가 그대로 확인한다. 주문 20만 건: member_id는 2만 명에 고르게, status는 99%가 PAID.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class IndexExplainTest {

    private static final String LESSON = "index-explain";
    private static boolean loaded;

    @Autowired DataSource ds;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        if (!loaded) {
            exec(ds, "drop table if exists ix_orders",
                    "create table ix_orders (id bigint primary key, member_id int not null, status text not null, "
                            + "created_at timestamp not null, email text not null)",
                    "insert into ix_orders select g, g % 20000, case when g % 100 = 0 then 'CANCELED' else 'PAID' end, "
                            + "timestamp '2026-01-01' + (g || ' minutes')::interval, 'user' || g || '@example.com' "
                            + "from generate_series(1, 200000) g");
            loaded = true;
        }
        for (String idx : List.of("ix_member", "ix_email", "ix_email_lower", "ix_status", "ix_status_created", "ix_created_status", "ix_created")) {
            exec(ds, "drop index if exists " + idx);
        }
        exec(ds, "vacuum analyze ix_orders");
    }

    @Test
    @DisplayName("인덱스가 없으면 Seq Scan으로 20만 행을 읽고, member_id 인덱스를 만들면 인덱스로 10행만 찾는다 (EXPLAIN ANALYZE)")
    void seqScanVsIndex() {
        String before = plan(ds, "explain (analyze, timing off, costs off) select * from ix_orders where member_id = 123");
        exec(ds, "create index ix_member on ix_orders (member_id)", "analyze ix_orders");
        String after = plan(ds, "explain (analyze, timing off, costs off) select * from ix_orders where member_id = 123");
        String beforeTime = plan(ds, "explain (analyze, costs off) select * from ix_orders where member_id + 0 = 123");
        String afterTime = plan(ds, "explain (analyze, costs off) select * from ix_orders where member_id = 123");

        assertThat(before).contains("Seq Scan").contains("Rows Removed by Filter: 199990");
        assertThat(after).contains("ix_member").doesNotContain("Seq Scan");
        CiOutput.record(LESSON, "no-index", before);
        CiOutput.record(LESSON, "with-index", after);
        CiOutput.record(LESSON, "timing", "[인덱스 없이 읽을 때]\n" + lastLines(beforeTime) + "\n\n[인덱스로 찾을 때]\n" + lastLines(afterTime));
    }

    private static String lastLines(String plan) {
        return String.join("\n", plan.lines().filter(l -> l.contains("Scan") || l.contains("Execution Time")).toList());
    }

    @Test
    @DisplayName("lower(email)은 email 인덱스를 못 쓰고, 같은 식의 함수 인덱스는 쓴다")
    void functionIndex() {
        exec(ds, "create index ix_email on ix_orders (email)", "analyze ix_orders");
        String bad = plan(ds, "explain (costs off) select * from ix_orders where lower(email) = 'user77@example.com'");
        String plain = plan(ds, "explain (costs off) select * from ix_orders where email = 'user77@example.com'");
        exec(ds, "create index ix_email_lower on ix_orders (lower(email))", "analyze ix_orders");
        String fixed = plan(ds, "explain (costs off) select * from ix_orders where lower(email) = 'user77@example.com'");

        assertThat(bad).contains("Seq Scan");
        assertThat(plain).contains("ix_email");
        assertThat(fixed).contains("ix_email_lower");
        CiOutput.record(LESSON, "function", "[email = ...]\n" + plain + "\n\n[lower(email) = ...]\n" + bad
                + "\n\n[함수 인덱스를 만든 뒤 lower(email) = ...]\n" + fixed);
    }

    @Test
    @DisplayName("선택도: 1%인 CANCELED는 인덱스를, 99%인 PAID는 Seq Scan을 고른다")
    void selectivity() {
        exec(ds, "create index ix_status on ix_orders (status)", "analyze ix_orders");
        String canceled = plan(ds, "explain (costs off) select * from ix_orders where status = 'CANCELED'");
        String paid = plan(ds, "explain (costs off) select * from ix_orders where status = 'PAID'");

        assertThat(canceled).contains("ix_status");
        assertThat(paid).contains("Seq Scan");
        CiOutput.record(LESSON, "selectivity", "[status = 'PAID'] 99%\n" + paid + "\n\n[status = 'CANCELED'] 1%\n" + canceled);
    }

    @Test
    @DisplayName("날짜에 함수를 씌우면 created_at 인덱스를 못 쓰고, 범위 조건으로 바꾸면 쓴다")
    void dateRange() {
        exec(ds, "create index ix_created on ix_orders (created_at)", "analyze ix_orders");
        String fn = plan(ds, "explain (costs off) select * from ix_orders where date(created_at) = '2026-02-01'");
        String range = plan(ds, "explain (costs off) select * from ix_orders where created_at >= '2026-02-01' and created_at < '2026-02-02'");

        assertThat(fn).contains("Seq Scan");
        assertThat(range).contains("ix_created");
        CiOutput.record(LESSON, "date", "[date(created_at) = '2026-02-01']\n" + fn
                + "\n\n[created_at >= '2026-02-01' and created_at < '2026-02-02']\n" + range);
    }

    @Test
    @DisplayName("복합 인덱스 (status, created_at): 선두 열 조건이 있으면 쓰고, created_at의 넓은 범위만 주면 Seq Scan이다")
    void composite() {
        exec(ds, "create index ix_status_created on ix_orders (status, created_at)", "analyze ix_orders");
        String both = plan(ds, "explain (costs off) select * from ix_orders where status = 'CANCELED' and created_at > '2026-03-01'");
        String onlySecond = plan(ds, "explain (costs off) select * from ix_orders where created_at > '2026-03-01'");

        assertThat(both).contains("ix_status_created").doesNotContain("Seq Scan");
        assertThat(onlySecond).contains("Seq Scan");
        CiOutput.record(LESSON, "composite", "[status = 'CANCELED' and created_at > ...]\n" + both
                + "\n\n[created_at > ... 만]\n" + onlySecond);
    }

    @Test
    @DisplayName("복합 인덱스 순서와 같은 ORDER BY ... LIMIT은 Sort 노드가 없고, status 인덱스만 있으면 Sort가 생긴다")
    void orderByUsesIndex() {
        String sql = "explain (costs off) select * from ix_orders where status = 'CANCELED' order by created_at desc limit 20";
        exec(ds, "create index ix_status on ix_orders (status)", "analyze ix_orders");
        String withSort = plan(ds, sql);
        exec(ds, "create index ix_status_created on ix_orders (status, created_at)", "analyze ix_orders");
        String noSort = plan(ds, sql);

        assertThat(withSort).contains("Sort");
        assertThat(noSort).contains("ix_status_created").doesNotContain("Sort");
        CiOutput.record(LESSON, "order-by", "[인덱스 (status)]\n" + withSort + "\n\n[인덱스 (status, created_at)]\n" + noSort);
    }

    @Test
    @DisplayName("Index Only Scan: VACUUM 뒤 인덱스에 있는 열만 고르면 테이블을 읽지 않는다")
    void indexOnly() {
        exec(ds, "create index ix_member on ix_orders (member_id)", "vacuum analyze ix_orders");
        String p = plan(ds, "explain (costs off) select member_id from ix_orders where member_id between 100 and 110");
        assertThat(p).contains("Index Only Scan");
        CiOutput.record(LESSON, "index-only", p);
    }

    @Test
    @DisplayName("인덱스가 다섯 개면 같은 20만 행을 넣는 데 더 오래 걸리고, 인덱스도 공간을 쓴다")
    void writeCost() {
        exec(ds, "drop table if exists ix_write", "create table ix_write (id bigint primary key, member_id int, status text, created_at timestamp, email text)");
        long none = timeInsert();
        exec(ds, "truncate ix_write",
                "create index on ix_write (member_id)", "create index on ix_write (status)", "create index on ix_write (created_at)",
                "create index on ix_write (email)", "create index on ix_write (status, created_at)");
        long five = timeInsert();
        List<String> sizes = rows(ds, "select pg_size_pretty(pg_relation_size('ix_write')), pg_size_pretty(pg_indexes_size('ix_write'))");

        assertThat(five).isGreaterThan(none);
        CiOutput.record(LESSON, "write-cost", String.join("\n",
                "[기본 키 인덱스만] 20만 행 INSERT: " + none + "ms",
                "[인덱스 다섯 개 더] 20만 행 INSERT: " + five + "ms",
                "테이블 크기 | 인덱스 크기 합: " + sizes.get(0)));
    }

    private long timeInsert() {
        long t = System.nanoTime();
        exec(ds, "insert into ix_write select g, g % 20000, case when g % 100 = 0 then 'CANCELED' else 'PAID' end, "
                + "timestamp '2026-01-01' + (g || ' minutes')::interval, 'user' || g || '@example.com' from generate_series(1, 200000) g");
        return (System.nanoTime() - t) / 1_000_000;
    }

    @Test
    @DisplayName("전부 읽어 자바에서 거르면 20만 행을 옮기고, WHERE로 거르면 맞는 2000행만 옮긴다. 결과는 같다")
    void filterInJavaVsWhere() {
        exec(ds, "create index ix_status on ix_orders (status)", "analyze ix_orders");
        List<Map<String, Object>> all = jdbc.queryForList("select id, status from ix_orders");
        List<Object> inJava = all.stream().filter(r -> "CANCELED".equals(r.get("status"))).map(r -> r.get("id")).toList();
        List<Object> inDb = jdbc.queryForList("select id from ix_orders where status = 'CANCELED'", Object.class);

        assertThat(inJava).hasSameElementsAs(inDb).hasSize(2000);
        CiOutput.record(LESSON, "java-filter", "[findAll 뒤 stream().filter] DB에서 받은 행: " + all.size()
                + "\n[where status = 'CANCELED'] DB에서 받은 행: " + inDb.size()
                + "\n두 결과가 같은가: " + (inJava.size() == inDb.size() && inJava.containsAll(inDb)));
    }
}
