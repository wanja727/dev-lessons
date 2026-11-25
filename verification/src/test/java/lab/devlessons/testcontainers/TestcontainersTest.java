package lab.devlessons.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 레슨 "통합 테스트와 Testcontainers"(T4). 같은 쿼리를 H2와 PostgreSQL 컨테이너에서 돌리고,
 * 컨테이너가 붙는 포트, 몇 번 뜨는지, 데이터가 다음 테스트로 새는지를 본다.
 * 안쪽 테스트 클래스는 EngineTestKit으로 이 테스트 안에서만 돌고, 평소 빌드에서는 건너뛴다.
 */
class TestcontainersTest {

    static final String LESSON = "testcontainers";
    static final String IMAGE = "postgres:16-alpine";

    static PostgreSQLContainer<?> postgres;
    static long startMillis;

    @BeforeAll
    static void start() {
        postgres = new PostgreSQLContainer<>(IMAGE);
        long t = System.nanoTime();
        postgres.start();
        startMillis = (System.nanoTime() - t) / 1_000_000;
    }

    @AfterAll
    static void stop() {
        postgres.stop();
    }

    static JdbcTemplate jdbc(String url, String user, String password) {
        return new JdbcTemplate(new DriverManagerDataSource(url, user, password));
    }

    static JdbcTemplate pg() {
        return jdbc(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    static JdbcTemplate h2(String name, String mode) {
        String url = "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1" + (mode == null ? "" : ";MODE=" + mode);
        return jdbc(url, "sa", "");
    }

    static final String UPSERT = """
            INSERT INTO stock(product_id, qty)
            VALUES (?, ?)
            ON CONFLICT (product_id)
            DO UPDATE SET qty = stock.qty + EXCLUDED.qty""";

    /** 재고 테이블을 만들고 upsert를 두 번 돌려 수량을 읽는다. 실패하면 오류의 첫 줄 */
    static String upsertResult(JdbcTemplate db) {
        db.execute("DROP TABLE IF EXISTS stock");
        db.execute("CREATE TABLE stock(product_id BIGINT PRIMARY KEY, qty INT NOT NULL)");
        try {
            db.update(UPSERT, 1L, 5);
            db.update(UPSERT, 1L, 3);
            return "성공, 수량 " + db.queryForObject("SELECT qty FROM stock WHERE product_id = 1", Integer.class);
        } catch (Exception e) {
            return "실패: " + firstLine(rootMessage(e));
        }
    }

    /** 쿠폰 테이블을 만들고 쿼리 하나를 돌린다. 결과 값이나 오류의 첫 줄 */
    static String couponQuery(JdbcTemplate db, String sql) {
        db.execute("DROP TABLE IF EXISTS coupon");
        db.execute("CREATE TABLE coupon(id BIGINT PRIMARY KEY, code VARCHAR(20) NOT NULL,"
                + " created_at TIMESTAMP NOT NULL)");
        db.update("INSERT INTO coupon VALUES (1, '100', TIMESTAMP '2026-11-25 10:00:00'),"
                + " (2, 'A7', TIMESTAMP '2026-11-25 11:00:00')");
        try {
            Object v = db.queryForObject(sql, Object.class);
            return "성공, " + v;
        } catch (Exception e) {
            return "실패: " + firstLine(rootMessage(e));
        }
    }

    static final String VARCHAR_EQ_INT = "SELECT count(*) FROM coupon WHERE id = 1 AND code = 100";
    static final String DATEADD = "SELECT DATEADD('DAY', 7, created_at) FROM coupon WHERE id = 1";

    @Test
    @DisplayName("ON CONFLICT upsert는 H2에서 실패하고 PostgreSQL에서는 수량 8이 된다. H2의 DATEADD는 H2에서만 통과한다")
    void h2IsNotPostgres() {
        String h2Upsert = upsertResult(h2("t4a", null));
        String h2PgUpsert = upsertResult(h2("t4b", "PostgreSQL"));
        String pgUpsert = upsertResult(pg());

        String h2Eq = couponQuery(h2("t4c", null), VARCHAR_EQ_INT);
        String pgEq = couponQuery(pg(), VARCHAR_EQ_INT);
        String h2Date = couponQuery(h2("t4d", null), DATEADD);
        String h2PgDate = couponQuery(h2("t4e", "PostgreSQL"), DATEADD);
        String pgDate = couponQuery(pg(), DATEADD);

        assertThat(h2Upsert).startsWith("실패");
        assertThat(pgUpsert).isEqualTo("성공, 수량 8");
        assertThat(h2Eq).isEqualTo("성공, 1");
        assertThat(pgEq).startsWith("실패");
        assertThat(h2Date).startsWith("성공");
        assertThat(pgDate).startsWith("실패");
        CiOutput.record(LESSON, "upsert", """
                H2 2.x (기본 모드)
                  → %s
                H2 2.x (MODE=PostgreSQL)
                  → %s
                PostgreSQL 16 (컨테이너)
                  → %s""".formatted(h2Upsert, h2PgUpsert, pgUpsert));
        CiOutput.record(LESSON, "h2-only", """
                %s
                  H2 2.x (기본 모드) → %s
                  PostgreSQL 16 → %s

                %s
                  H2 2.x (기본 모드) → %s
                  H2 2.x (MODE=PostgreSQL) → %s
                  PostgreSQL 16 → %s""".formatted(VARCHAR_EQ_INT, h2Eq, pgEq,
                DATEADD, h2Date, h2PgDate, pgDate));
    }

    @Test
    @DisplayName("컨테이너의 5432 포트는 호스트의 임의 포트로 열리고, 띄우는 데 몇 초가 걸린다")
    void containerUsesRandomHostPort() {
        int mapped = postgres.getMappedPort(5432);
        String version = pg().queryForObject("SHOW server_version", String.class);

        assertThat(mapped).isNotEqualTo(5432);
        assertThat(version).startsWith("16");
        CiOutput.record(LESSON, "port", """
                postgres.getJdbcUrl()
                  → %s
                postgres.getMappedPort(5432) → %d
                SHOW server_version → %s
                컨테이너를 띄우는 데 걸린 시간 → %.1f초""".formatted(
                postgres.getJdbcUrl().replaceAll("\\?.*", ""), mapped, version.split(" ")[0],
                startMillis / 1000.0));
    }

    // ── 질문 3: 컨테이너는 언제 몇 번 뜨나 ──

    static final List<String> IDS = Collections.synchronizedList(new ArrayList<>());

    @Testcontainers
    @EnabledIfSystemProperty(named = "t4.inner", matches = "true")
    static class PerMethodContainer {
        @Container
        PostgreSQLContainer<?> db = new PostgreSQLContainer<>(IMAGE);

        @Test void a() { IDS.add("인스턴스 a " + db.getContainerId()); }
        @Test void b() { IDS.add("인스턴스 b " + db.getContainerId()); }
        @Test void c() { IDS.add("인스턴스 c " + db.getContainerId()); }
    }

    @Testcontainers
    @EnabledIfSystemProperty(named = "t4.inner", matches = "true")
    static class SharedContainer {
        @Container
        static PostgreSQLContainer<?> db = new PostgreSQLContainer<>(IMAGE);

        @Test void a() { IDS.add("static a " + db.getContainerId()); }
        @Test void b() { IDS.add("static b " + db.getContainerId()); }
        @Test void c() { IDS.add("static c " + db.getContainerId()); }
    }

    static EngineExecutionResults runInner(Class<?>... classes) {
        System.setProperty("t4.inner", "true");
        try {
            var request = EngineTestKit.engine("junit-jupiter");
            for (Class<?> c : classes) {
                request = request.selectors(selectClass(c));
            }
            return request.execute();
        } finally {
            System.clearProperty("t4.inner");
        }
    }

    @Test
    @DisplayName("인스턴스 필드 @Container는 테스트 메서드마다, static 필드는 클래스에 한 번 컨테이너를 띄운다")
    void containerLifecycleFollowsField() {
        IDS.clear();
        long t1 = System.nanoTime();
        runInner(PerMethodContainer.class).testEvents().assertStatistics(s -> s.succeeded(3));
        long perMethodMs = (System.nanoTime() - t1) / 1_000_000;
        long t2 = System.nanoTime();
        runInner(SharedContainer.class).testEvents().assertStatistics(s -> s.succeeded(3));
        long sharedMs = (System.nanoTime() - t2) / 1_000_000;

        Set<String> perMethod = new LinkedHashSet<>();
        Set<String> shared = new LinkedHashSet<>();
        List<String> lines = new ArrayList<>();
        for (String line : List.copyOf(IDS)) {
            String[] p = line.split(" ");
            (p[0].equals("인스턴스") ? perMethod : shared).add(p[2]);
            lines.add("%s 필드, 테스트 %s → 컨테이너 %s".formatted(p[0], p[1], p[2].substring(0, 12)));
        }
        assertThat(perMethod).hasSize(3);
        assertThat(shared).hasSize(1);
        CiOutput.record(LESSON, "lifecycle", String.join("\n", lines) + """


                인스턴스 필드: 서로 다른 컨테이너 %d개, 테스트 셋에 %.1f초
                static 필드: 서로 다른 컨테이너 %d개, 테스트 셋에 %.1f초""".formatted(
                perMethod.size(), perMethodMs / 1000.0, shared.size(), sharedMs / 1000.0));
    }

    // ── 질문 4: 데이터가 다음 테스트로 새나 ──

    static final List<String> LEAK = Collections.synchronizedList(new ArrayList<>());

    static void createStock() {
        pg().execute("CREATE TABLE IF NOT EXISTS stock_leak(product_id BIGINT PRIMARY KEY, qty INT)");
    }

    /** 넣는 테스트가 먼저, 세는 테스트가 나중 */
    @EnabledIfSystemProperty(named = "t4.inner", matches = "true")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    static class LeakInsertFirst {
        @Test @Order(1)
        void 상품을_넣는다() {
            createStock();
            pg().update("INSERT INTO stock_leak VALUES (1, 5)");
        }

        @Test @Order(2)
        void 재고가_없으면_0개() {
            createStock();
            assertThat(pg().queryForObject("SELECT count(*) FROM stock_leak", Integer.class)).isZero();
        }
    }

    /** 세는 테스트가 먼저 */
    @EnabledIfSystemProperty(named = "t4.inner", matches = "true")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    static class LeakCountFirst {
        @Test @Order(1)
        void 재고가_없으면_0개() {
            createStock();
            assertThat(pg().queryForObject("SELECT count(*) FROM stock_leak", Integer.class)).isZero();
        }

        @Test @Order(2)
        void 상품을_넣는다() {
            createStock();
            pg().update("INSERT INTO stock_leak VALUES (1, 5)");
        }
    }

    /** 넣는 테스트가 먼저지만, 테스트마다 끝나면 비운다 */
    @EnabledIfSystemProperty(named = "t4.inner", matches = "true")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    static class TruncateAfterEach {
        @AfterEach
        void truncate() {
            pg().execute("TRUNCATE stock_leak");
        }

        @Test @Order(1)
        void 상품을_넣는다() {
            createStock();
            pg().update("INSERT INTO stock_leak VALUES (1, 5)");
        }

        @Test @Order(2)
        void 재고가_없으면_0개() {
            createStock();
            assertThat(pg().queryForObject("SELECT count(*) FROM stock_leak", Integer.class)).isZero();
        }
    }

    static String outcome(Class<?> c) {
        createStock();
        pg().execute("TRUNCATE stock_leak");
        var events = runInner(c).testEvents();
        long ok = events.succeeded().count();
        long failed = events.failed().count();
        String failedName = events.failed().stream()
                .map(e -> e.getTestDescriptor().getDisplayName().replace("()", ""))
                .findFirst().orElse("");
        return "통과 %d, 실패 %d%s".formatted(ok, failed, failed > 0 ? " (" + failedName + ")" : "");
    }

    @Test
    @DisplayName("트랜잭션 없이 같은 DB를 쓰면 순서에 따라 결과가 달라지고, 테스트마다 TRUNCATE하면 순서와 상관없이 통과한다")
    void dataLeaksBetweenTests() {
        String insertFirst = outcome(LeakInsertFirst.class);
        String countFirst = outcome(LeakCountFirst.class);
        String truncated = outcome(TruncateAfterEach.class);

        assertThat(insertFirst).startsWith("통과 1, 실패 1");
        assertThat(countFirst).isEqualTo("통과 2, 실패 0");
        assertThat(truncated).isEqualTo("통과 2, 실패 0");
        CiOutput.record(LESSON, "leak", """
                넣기 → 세기 순서
                  → %s
                세기 → 넣기 순서
                  → %s
                넣기 → 세기 순서, @AfterEach에서 TRUNCATE
                  → %s""".formatted(insertFirst, countFirst, truncated));
    }

    static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage();
    }

    static String firstLine(String s) {
        if (s == null) {
            return "";
        }
        String line = s.lines().findFirst().orElse(s).strip();
        int cut = line.indexOf("; SQL statement");
        return (cut > 0 ? line.substring(0, cut) : line).replace("\\000a", " ");
    }
}
