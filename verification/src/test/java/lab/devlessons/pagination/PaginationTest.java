package lab.devlessons.pagination;

import static lab.devlessons.support.Jdbc.exec;
import static lab.devlessons.support.Jdbc.plan;
import static lab.devlessons.support.Jdbc.rows;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 레슨 "페이지네이션"(D8)의 주장을 PostgreSQL 16에서 검증한다.
 * 게시글 100만 건 pg_post(id, title, created_at)와 인덱스 (created_at, id). 최신순으로 20개씩 읽는다.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class PaginationTest {

    private static final String LESSON = "pagination";
    private static boolean loaded;

    @Autowired DataSource ds;

    @BeforeEach
    void setUp() {
        if (!loaded) {
            exec(ds, "drop table if exists pg_post",
                    "create table pg_post (id bigint primary key, title text not null, created_at timestamp not null)",
                    "insert into pg_post select g, '글 ' || g, timestamp '2024-01-01' + (g || ' seconds')::interval "
                            + "from generate_series(1, 1000000) g",
                    "create index pg_post_created_id on pg_post (created_at, id)",
                    "vacuum analyze pg_post");
            loaded = true;
        }
    }

    private static final String OFFSET = "select id, title from pg_post order by created_at desc, id desc limit 20 offset %d";

    @Test
    @DisplayName("1페이지는 인덱스에서 20행을 읽고, 끝 페이지는 앞의 99만 9980행을 읽고 버린다. 끝 페이지가 수백 배 이상 느리다")
    void offsetGetsSlower() {
        // 데워 두기
        rows(ds, String.format(OFFSET, 999980));
        String first = plan(ds, "explain (analyze, costs off) " + String.format(OFFSET, 0));
        String last = plan(ds, "explain (analyze, costs off) " + String.format(OFFSET, 999980));

        assertThat(scanRows(first)).isEqualTo(20);
        assertThat(scanRows(last)).isEqualTo(1000000);
        assertThat(execMs(last)).isGreaterThan(execMs(first) * 100);
        CiOutput.record(LESSON, "first-page", first);
        CiOutput.record(LESSON, "last-page", last);
    }

    @Test
    @DisplayName("페이지 번호가 커질수록 OFFSET 방식의 시간이 늘고, 커서 방식은 그대로다")
    void timingByPage() {
        List<String> lines = new ArrayList<>();
        lines.add("페이지 | OFFSET 방식 | 커서 방식");
        for (int page : new int[] {1, 1000, 10000, 50000}) {
            long offset = (long) (page - 1) * 20;
            rows(ds, String.format(OFFSET, offset));
            double off = execMs(plan(ds, "explain (analyze, costs off) " + String.format(OFFSET, offset)));
            // 커서: 앞 페이지의 마지막 글(id = 1000000 - offset + 1)보다 작은 것
            long lastId = 1000000 - offset + 1;
            String cursor = "select id, title from pg_post where (created_at, id) < "
                    + "(timestamp '2024-01-01' + (" + lastId + " || ' seconds')::interval, " + lastId + ") "
                    + "order by created_at desc, id desc limit 20";
            rows(ds, cursor);
            double cur = execMs(plan(ds, "explain (analyze, costs off) " + cursor));
            lines.add(page + " | " + String.format("%.2fms", off) + " | " + String.format("%.2fms", cur));
        }
        CiOutput.record(LESSON, "timing", String.join("\n", lines));
    }

    @Test
    @DisplayName("커서 방식은 끝 근처에서도 Index Scan이 20행만 읽는다")
    void cursorReadsTwenty() {
        String p = plan(ds, "explain (analyze, costs off) select id, title from pg_post where (created_at, id) < "
                + "(timestamp '2024-01-01' + interval '21 seconds', 21) order by created_at desc, id desc limit 20");
        assertThat(scanRows(p)).isEqualTo(20);
        CiOutput.record(LESSON, "cursor-plan", p);
    }

    @Test
    @DisplayName("count(*)는 100만 행을 훑는다")
    void countCost() {
        String p = plan(ds, "explain (analyze, costs off) select count(*) from pg_post");
        assertThat(p).containsAnyOf("Seq Scan", "Index Only Scan");
        CiOutput.record(LESSON, "count", p);
    }

    @Test
    @DisplayName("1페이지를 읽은 뒤 새 글 3개가 올라오면, OFFSET의 2페이지는 1페이지 끝 글 3개를 또 보여 주고 커서는 겹치지 않는다")
    void insertBetweenPages() {
        exec(ds, "drop table if exists pg_small", "create table pg_small (id bigint primary key, created_at timestamp not null)",
                "insert into pg_small select g, timestamp '2026-01-01' + (g || ' minutes')::interval from generate_series(1, 100) g");
        List<String> page1 = ids("select id from pg_small order by created_at desc, id desc limit 20 offset 0");
        String lastOfPage1 = page1.get(page1.size() - 1);
        exec(ds, "insert into pg_small select g, timestamp '2026-01-01' + (g || ' minutes')::interval from generate_series(101, 103) g");
        List<String> offsetPage2 = ids("select id from pg_small order by created_at desc, id desc limit 20 offset 20");
        List<String> cursorPage2 = ids("select id from pg_small where (created_at, id) < "
                + "((select created_at from pg_small where id = " + lastOfPage1 + "), " + lastOfPage1 + ") "
                + "order by created_at desc, id desc limit 20");

        Set<String> dupOffset = new HashSet<>(offsetPage2);
        dupOffset.retainAll(page1);
        Set<String> dupCursor = new HashSet<>(cursorPage2);
        dupCursor.retainAll(page1);
        assertThat(dupOffset).containsExactlyInAnyOrder("83", "82", "81");
        assertThat(dupCursor).isEmpty();
        CiOutput.record(LESSON, "insert", String.join("\n",
                "1페이지: " + page1,
                "(새 글 101, 102, 103이 올라옴)",
                "OFFSET 2페이지: " + offsetPage2,
                "  → 1페이지와 겹친 글: " + sorted(dupOffset),
                "커서 2페이지: " + cursorPage2,
                "  → 1페이지와 겹친 글: " + sorted(dupCursor)));
    }

    @Test
    @DisplayName("1페이지를 읽은 뒤 그 안의 글 하나가 지워지면, OFFSET의 2페이지는 원래 2페이지의 첫 글을 빠뜨린다")
    void deleteBetweenPages() {
        exec(ds, "drop table if exists pg_small", "create table pg_small (id bigint primary key, created_at timestamp not null)",
                "insert into pg_small select g, timestamp '2026-01-01' + (g || ' minutes')::interval from generate_series(1, 100) g");
        List<String> page1 = ids("select id from pg_small order by created_at desc, id desc limit 20 offset 0");
        exec(ds, "delete from pg_small where id = 90");
        List<String> page2 = ids("select id from pg_small order by created_at desc, id desc limit 20 offset 20");

        assertThat(page1).endsWith("81");
        assertThat(page2).doesNotContain("80").startsWith("79");
        CiOutput.record(LESSON, "delete", "1페이지: " + page1 + "\n(1페이지의 글 90이 지워짐)\nOFFSET 2페이지: " + page2
                + "\n  → 원래 2페이지의 첫 글 80은 어느 페이지에도 나오지 않는다");
    }

    @Test
    @DisplayName("정렬 기준이 모두 같은 값이면, id 없이 order by created_at으로 이어 읽은 페이지 사이에 중복이나 누락이 생길 수 있다")
    void tiesWithoutId() {
        exec(ds, "drop table if exists pg_tie", "create table pg_tie (id bigint primary key, created_at timestamp not null, body text)",
                "insert into pg_tie select g, timestamp '2026-01-01', repeat('x', 200) from generate_series(1, 5000) g",
                "analyze pg_tie");
        Set<String> seen = new HashSet<>();
        int total = 0;
        for (int page = 0; page < 250; page++) {
            List<String> ids = ids("select id from pg_tie order by created_at desc limit 20 offset " + page * 20);
            seen.addAll(ids);
            total += ids.size();
        }
        Set<String> seenWithId = new HashSet<>();
        for (int page = 0; page < 250; page++) {
            seenWithId.addAll(ids("select id from pg_tie order by created_at desc, id desc limit 20 offset " + page * 20));
        }

        assertThat(seenWithId).hasSize(5000);
        CiOutput.record(LESSON, "ties", String.join("\n",
                "created_at이 모두 같은 글 5000개를 20개씩 250페이지로 이어 읽음",
                "[order by created_at] 읽은 행 " + total + ", 서로 다른 글 " + seen.size() + " (빠진 글 " + (5000 - seen.size()) + ")",
                "[order by created_at, id] 서로 다른 글 " + seenWithId.size()));
    }

    private List<String> ids(String sql) {
        return rows(ds, sql);
    }

    private static List<String> sorted(Set<String> s) {
        List<String> l = new ArrayList<>(s);
        l.sort((a, b) -> Long.compare(Long.parseLong(b), Long.parseLong(a)));
        return l;
    }

    /** 실행계획에서 Index Scan 노드의 actual rows */
    private static long scanRows(String plan) {
        Matcher m = Pattern.compile("Index Scan.*?actual time=[\\d.]+\\.\\.[\\d.]+ rows=(\\d+)").matcher(plan);
        return m.find() ? Long.parseLong(m.group(1)) : -1;
    }

    private static double execMs(String plan) {
        Matcher m = Pattern.compile("Execution Time: ([\\d.]+) ms").matcher(plan);
        return m.find() ? Double.parseDouble(m.group(1)) : -1;
    }
}
