package lab.devlessons.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 레슨 "인덱스와 실행계획"의 주장을 PostgreSQL 16의 EXPLAIN으로 검증한다.
 * 주문 200,000건: member_id는 2만 명에 고르게, status는 99%가 PAID·1%가 CANCELED.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class IndexPlanTest {

    private static boolean loaded;

    @Autowired DataSource dataSource;

    @BeforeEach
    void setUp() throws SQLException {
        if (!loaded) {
            exec("drop table if exists idx_orders");
            exec("create table idx_orders (id bigint primary key, member_id int not null, status text not null, "
                    + "created_at timestamp not null, email text not null)");
            exec("insert into idx_orders select g, g % 20000, case when g % 100 = 0 then 'CANCELED' else 'PAID' end, "
                    + "timestamp '2026-01-01' + (g || ' minutes')::interval, 'user' || g || '@example.com' "
                    + "from generate_series(1, 200000) g");
            loaded = true;
        }
        for (String idx : List.of("idx_member", "idx_email", "idx_email_lower", "idx_email_pattern", "idx_status", "idx_status_created")) {
            exec("drop index if exists " + idx);
        }
        exec("analyze idx_orders");
    }

    @Test
    @DisplayName("인덱스가 없으면 member_id 조건 조회도 테이블 전체를 읽는다 (Seq Scan)")
    void noIndexSeqScan() throws SQLException {
        assertThat(plan("select * from idx_orders where member_id = 123")).contains("Seq Scan");
    }

    @Test
    @DisplayName("member_id에 인덱스를 만들면 인덱스로 찾는다 (Seq Scan이 사라짐)")
    void indexUsed() throws SQLException {
        exec("create index idx_member on idx_orders (member_id)");
        exec("analyze idx_orders");

        String plan = plan("select * from idx_orders where member_id = 123");
        assertThat(plan).contains("idx_member").doesNotContain("Seq Scan");
    }

    @Test
    @DisplayName("컬럼에 함수를 씌우면 그 컬럼의 인덱스를 못 쓰고, 같은 식으로 만든 함수 인덱스는 쓴다")
    void functionOnColumn() throws SQLException {
        exec("create index idx_email on idx_orders (email)");
        exec("analyze idx_orders");
        assertThat(plan("select * from idx_orders where lower(email) = 'user77@example.com'")).contains("Seq Scan");

        exec("create index idx_email_lower on idx_orders (lower(email))");
        exec("analyze idx_orders");
        assertThat(plan("select * from idx_orders where lower(email) = 'user77@example.com'"))
                .contains("idx_email_lower").doesNotContain("Seq Scan");
    }

    @Test
    @DisplayName("LIKE '접두어%'는 패턴용 인덱스를 쓰고, LIKE '%접미어'는 인덱스를 못 쓴다")
    void likePrefixVsSuffix() throws SQLException {
        exec("create index idx_email_pattern on idx_orders (email text_pattern_ops)");
        exec("analyze idx_orders");

        assertThat(plan("select * from idx_orders where email like 'user12345@%'"))
                .contains("idx_email_pattern").doesNotContain("Seq Scan");
        assertThat(plan("select * from idx_orders where email like '%345@example.com'")).contains("Seq Scan");
    }

    @Test
    @DisplayName("기본 정렬 규칙(collation)이 C가 아니면 일반 B-tree 인덱스로는 LIKE '접두어%'를 못 쓴다")
    void plainIndexCannotLikeUnderNonCCollation() throws SQLException {
        String collation = query("select datcollate from pg_database where datname = current_database()");
        exec("create index idx_email on idx_orders (email)");
        exec("analyze idx_orders");

        String plan = plan("select * from idx_orders where email like 'user12345@%'");
        if (!"C".equals(collation) && !"POSIX".equals(collation)) {
            assertThat(plan).contains("Seq Scan");
        }
    }

    @Test
    @DisplayName("선택도: 1%뿐인 CANCELED는 인덱스를 쓰고, 99%인 PAID는 인덱스가 있어도 전체를 읽는다")
    void selectivity() throws SQLException {
        exec("create index idx_status on idx_orders (status)");
        exec("analyze idx_orders");

        assertThat(plan("select * from idx_orders where status = 'CANCELED'")).contains("idx_status").doesNotContain("Seq Scan");
        assertThat(plan("select * from idx_orders where status = 'PAID'")).contains("Seq Scan");
    }

    @Test
    @DisplayName("복합 인덱스 (status, created_at): 선두 컬럼 조건 + 범위는 인덱스, 선두 컬럼 없이 넓은 범위만 주면 전체를 읽는다")
    void compositeIndex() throws SQLException {
        exec("create index idx_status_created on idx_orders (status, created_at)");
        exec("analyze idx_orders");

        assertThat(plan("select * from idx_orders where status = 'CANCELED' and created_at > '2026-03-01'"))
                .contains("idx_status_created").doesNotContain("Seq Scan");
        assertThat(plan("select * from idx_orders where created_at > '2026-03-01'")).contains("Seq Scan");
    }

    @Test
    @DisplayName("필요한 컬럼이 모두 인덱스에 있으면 테이블을 읽지 않는다 (Index Only Scan, VACUUM 후)")
    void indexOnlyScan() throws SQLException {
        exec("create index idx_member on idx_orders (member_id)");
        exec("vacuum analyze idx_orders");

        assertThat(plan("select member_id from idx_orders where member_id between 100 and 110")).contains("Index Only Scan");
    }

    private String plan(String sql) throws SQLException {
        List<String> lines = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("explain " + sql)) {
            while (rs.next()) {
                lines.add(rs.getString(1));
            }
        }
        String plan = String.join("\n", lines);
        System.out.println("[plan] " + sql + "\n" + plan);
        return plan;
    }

    private String query(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private void exec(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }
}
