package lab.devlessons.slowquery;

import static lab.devlessons.support.Jdbc.exec;
import static lab.devlessons.support.Jdbc.number;
import static lab.devlessons.support.Jdbc.plan;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 레슨 "느린 쿼리 고치기"(D14)의 주장을 PostgreSQL 16에서 검증한다.
 * 주문 100만 건 sq_orders. 통계, 메모리, 캐시, 인덱스를 하나씩 바꿔 가며 실행계획과 시간을 본다.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class SlowQueryTest {

    private static final String LESSON = "slow-query";

    @Autowired DataSource ds;
    @Autowired PostgreSQLContainer<?> postgres;

    private void load(String table) {
        exec(ds, "drop table if exists " + table,
                "create table " + table + " (id bigint primary key, member_id int not null, status text not null, "
                        + "amount int not null, created_at timestamp not null) with (autovacuum_enabled = false)",
                "analyze " + table,
                "insert into " + table + " select g, g % 20000, case when g % 10000 = 0 then 'CANCELED' else 'PAID' end, "
                        + "g % 100000, timestamp '2024-01-01' + (g || ' seconds')::interval from generate_series(1, 1000000) g");
    }

    @Test
    @DisplayName("log_min_duration_statement보다 오래 걸린 쿼리는 서버 로그에 duration과 함께 남고, 짧은 쿼리는 남지 않는다")
    void slowLog() throws SQLException {
        try (Connection c = ds.getConnection()) {
            exec(c, "set log_min_duration_statement = '200ms'");
            exec(c, "select pg_sleep(0.3), 'slow-query-lesson-a'");
            exec(c, "select pg_sleep(0.05), 'slow-query-lesson-b'");
        }
        String logs = postgres.getLogs();
        String line = logs.lines().filter(l -> l.contains("slow-query-lesson-a") && l.contains("duration")).findFirst().orElse("");
        assertThat(line).isNotEmpty();
        assertThat(logs).doesNotContain("slow-query-lesson-b");
        CiOutput.record(LESSON, "slow-log", line.replaceAll("^\\S+ \\S+ \\S+ \\[\\d+\\] ", ""));
    }

    @Test
    @DisplayName("빈 테이블 때의 통계로는 CANCELED 100건의 어림이 크게 틀리고, ANALYZE 뒤에는 가깝다")
    void staleStatistics() {
        load("sq_stale");
        String before = plan(ds, "explain (analyze, timing off, costs on) select * from sq_stale where status = 'CANCELED'");
        exec(ds, "analyze sq_stale");
        String after = plan(ds, "explain (analyze, timing off, costs on) select * from sq_stale where status = 'CANCELED'");

        long[] b = rows(before);
        long[] a = rows(after);
        assertThat(b[1]).isEqualTo(100);
        assertThat(ratio(b)).isGreaterThan(10);
        assertThat(ratio(a)).isLessThan(ratio(b) / 5);
        CiOutput.record(LESSON, "stale", "[ANALYZE 전] 어림 rows=" + b[0] + ", 실제 rows=" + b[1] + "\n" + firstLine(before)
                + "\n\n[ANALYZE 뒤] 어림 rows=" + a[0] + ", 실제 rows=" + a[1] + "\n" + firstLine(after));
    }

    private static long[] rows(String plan) {
        Matcher m = Pattern.compile("rows=(\\d+) width=\\d+\\) \\(actual rows=(\\d+)").matcher(plan);
        if (!m.find()) {
            throw new IllegalStateException(plan);
        }
        return new long[] {Long.parseLong(m.group(1)), Long.parseLong(m.group(2))};
    }

    private static double ratio(long[] r) {
        double est = Math.max(1, r[0]);
        double act = Math.max(1, r[1]);
        return Math.max(est / act, act / est);
    }

    private static String firstLine(String plan) {
        return plan.lines().findFirst().orElse("");
    }

    @Test
    @DisplayName("work_mem이 작으면 큰 정렬이 external merge Disk로, 키우면 quicksort Memory로 바뀐다")
    void workMemSort() throws SQLException {
        load("sq_sort");
        exec(ds, "analyze sq_sort");
        String small;
        String big;
        try (Connection c = ds.getConnection()) {
            exec(c, "set work_mem = '4MB'", "set max_parallel_workers_per_gather = 0");
            small = sortLine(plan(c, "explain (analyze, costs off) select id, amount from sq_sort order by amount desc, id"));
            exec(c, "set work_mem = '256MB'");
            big = sortLine(plan(c, "explain (analyze, costs off) select id, amount from sq_sort order by amount desc, id"));
            exec(c, "reset work_mem", "reset max_parallel_workers_per_gather");
        }
        assertThat(small).contains("external merge");
        assertThat(big).contains("quicksort");
        CiOutput.record(LESSON, "work-mem", "[work_mem = 4MB]\n" + small + "\n\n[work_mem = 256MB]\n" + big);
    }

    private static String sortLine(String plan) {
        return String.join("\n", plan.lines().filter(l -> l.contains("Sort Method") || l.contains("Execution Time"))
                .map(String::trim).toList());
    }

    @Test
    @DisplayName("BUFFERS는 캐시에서 읽은 페이지(hit)를 보여 준다. 방금 넣은 데이터는 이미 캐시에 있어 첫 실행부터 모두 hit다")
    void bufferCache() throws SQLException {
        load("sq_cache");
        exec(ds, "analyze sq_cache");
        String first;
        String second;
        try (Connection c = ds.getConnection()) {
            exec(c, "set max_parallel_workers_per_gather = 0");
            first = buffers(plan(c, "explain (analyze, buffers, costs off) select sum(amount) from sq_cache"));
            second = buffers(plan(c, "explain (analyze, buffers, costs off) select sum(amount) from sq_cache"));
            exec(c, "reset max_parallel_workers_per_gather");
        }
        CiOutput.record(LESSON, "buffers", "[첫 번째 실행]\n" + first + "\n\n[두 번째 실행]\n" + second);
        assertThat(first).contains("shared hit");
        assertThat(hit(second)).isGreaterThanOrEqualTo(hit(first));
    }

    private static String buffers(String plan) {
        return String.join("\n", plan.lines().filter(l -> l.contains("Buffers:") || l.contains("Execution Time"))
                .map(String::trim).limit(1).toList());
    }

    private static long hit(String s) {
        Matcher m = Pattern.compile("hit=(\\d+)").matcher(s);
        return m.find() ? Long.parseLong(m.group(1)) : 0;
    }

    @Test
    @DisplayName("회원별 최근 주문 5건: 인덱스 (member_id, created_at)를 만들면 같은 조건으로 다섯 번 잰 가운데 값이 크게 준다")
    void fixRecentOrders() {
        load("sq_fix");
        exec(ds, "analyze sq_fix");
        String sql = "select id, amount, created_at from sq_fix where member_id = 123 order by created_at desc limit 5";
        String beforePlan = plan(ds, "explain (analyze, costs off) " + sql);
        double before = median(sql);
        exec(ds, "create index sq_fix_member_created on sq_fix (member_id, created_at)", "analyze sq_fix");
        String afterPlan = plan(ds, "explain (analyze, costs off) " + sql);
        double after = median(sql);

        assertThat(beforePlan).contains("Seq Scan");
        assertThat(afterPlan).contains("sq_fix_member_created");
        assertThat(after).isLessThan(before / 10);
        CiOutput.record(LESSON, "fix", "[고치기 전] 다섯 번 잰 가운데 값 " + String.format("%.2fms", before) + "\n" + nodes(beforePlan)
                + "\n\n[인덱스 (member_id, created_at) 뒤] 가운데 값 " + String.format("%.2fms", after) + "\n" + nodes(afterPlan));
    }

    private static String nodes(String plan) {
        return String.join("\n", plan.lines().filter(l -> l.contains("Scan") || l.contains("Sort") && !l.contains("Sort Method"))
                .map(String::trim).toList());
    }

    private double median(String sql) {
        List<Double> times = new ArrayList<>();
        plan(ds, "explain analyze " + sql);
        for (int i = 0; i < 5; i++) {
            Matcher m = Pattern.compile("Execution Time: ([\\d.]+) ms").matcher(plan(ds, "explain analyze " + sql));
            m.find();
            times.add(Double.parseDouble(m.group(1)));
        }
        Collections.sort(times);
        return times.get(2);
    }

    @Test
    @DisplayName("begin 안에서 explain analyze로 UPDATE를 재고 rollback하면 값이 남지 않는다. analyze는 실제로 실행한다")
    void explainAnalyzeExecutes() throws SQLException {
        exec(ds, "drop table if exists sq_one", "create table sq_one (id int primary key, amount int)", "insert into sq_one values (1, 100)");
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            plan(c, "explain analyze update sq_one set amount = 0 where id = 1");
            long inside = number(c, "select amount from sq_one where id = 1");
            c.rollback();
            assertThat(inside).isZero();
        }
        long after = number(ds, "select amount from sq_one where id = 1");
        assertThat(after).isEqualTo(100);
        CiOutput.record(LESSON, "rollback", "begin; explain analyze update ... set amount = 0;\n같은 트랜잭션에서 읽은 amount: 0 (실제로 바뀜)\nrollback 뒤 amount: " + after);
    }
}
