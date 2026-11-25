package lab.devlessons.migration;

import static lab.devlessons.support.Jdbc.error;
import static lab.devlessons.support.Jdbc.exec;
import static lab.devlessons.support.Jdbc.number;
import static lab.devlessons.support.Jdbc.rows;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
 * 레슨 "스키마 변경과 마이그레이션"(D16)의 주장을 PostgreSQL 16에서 검증한다.
 * 회원 테이블 mg_member에 열을 더하고, 제약과 인덱스를 걸고, 이름을 바꾼다.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class SchemaMigrationTest {

    private static final String LESSON = "schema-migration";

    @Autowired DataSource ds;

    @BeforeEach
    void setUp() {
        exec(ds, "drop table if exists mg_member, mg_history cascade",
                "create table mg_member (id bigint primary key, name text not null, phone text)",
                "insert into mg_member select g, 'm' || g, '010-' || g from generate_series(1, 10000) g");
    }

    private CompletableFuture<Long> async(Connection c, String sql) {
        return CompletableFuture.supplyAsync(() -> {
            long t = System.nanoTime();
            try {
                exec(c, sql);
                return (System.nanoTime() - t) / 1_000_000;
            } catch (SQLException e) {
                throw new IllegalStateException(e.getSQLState() + " " + e.getMessage().lines().findFirst().orElse(""), e);
            }
        });
    }

    @Test
    @DisplayName("열린 조회 트랜잭션 뒤에서 ALTER가 기다리고, ALTER 뒤에 온 평범한 SELECT도 기다린다. 앞 트랜잭션이 끝나면 차례로 풀린다")
    void lockQueue() throws Exception {
        List<String> log = new ArrayList<>();
        try (Connection c1 = ds.getConnection(); Connection c2 = ds.getConnection(); Connection c3 = ds.getConnection()) {
            c1.setAutoCommit(false);
            number(c1, "select count(*) from mg_member");
            log.add("연결 1: begin; select count(*) (커밋 전)");
            CompletableFuture<Long> alter = async(c2, "alter table mg_member add column email text");
            Thread.sleep(300);
            CompletableFuture<Long> select = async(c3, "select * from mg_member where id = 1");
            Thread.sleep(700);
            List<String> waiting = rows(ds, "select mode, granted from pg_locks where relation = 'mg_member'::regclass "
                    + "and not granted order by mode");
            log.add("연결 2: alter table ... add column → " + (alter.isDone() ? "끝남" : "기다림"));
            log.add("연결 3: select ... where id = 1 → " + (select.isDone() ? "끝남" : "기다림"));
            log.add("pg_locks에서 기다리는 락 (mode | granted): " + waiting);
            assertThat(alter).isNotDone();
            assertThat(select).isNotDone();
            c1.commit();
            log.add("연결 1: commit (약 1초 뒤)");
            log.add("연결 2: ALTER 끝 (" + alter.get(5, TimeUnit.SECONDS) + "ms 걸림)");
            log.add("연결 3: SELECT 끝 (" + select.get(5, TimeUnit.SECONDS) + "ms 걸림)");
            assertThat(waiting).contains("AccessExclusiveLock | false", "AccessShareLock | false");
        }
        CiOutput.record(LESSON, "queue", String.join("\n", log));
    }

    @Test
    @DisplayName("lock_timeout을 건 ALTER는 그 시간 뒤 55P03으로 실패하고, 뒤에서 막혔던 SELECT가 풀린다")
    void lockTimeoutForDdl() throws Exception {
        List<String> log = new ArrayList<>();
        try (Connection c1 = ds.getConnection(); Connection c2 = ds.getConnection(); Connection c3 = ds.getConnection()) {
            c1.setAutoCommit(false);
            number(c1, "select count(*) from mg_member");
            exec(c2, "set lock_timeout = '1s'");
            long t = System.nanoTime();
            CompletableFuture<String> alter = CompletableFuture.supplyAsync(() -> {
                try {
                    exec(c2, "alter table mg_member add column email text");
                    return "성공";
                } catch (SQLException e) {
                    return e.getSQLState() + " " + e.getMessage().lines().findFirst().orElse("");
                }
            });
            Thread.sleep(200);
            CompletableFuture<Long> select = async(c3, "select * from mg_member where id = 1");
            String alterResult = alter.get(5, TimeUnit.SECONDS);
            long alterMs = (System.nanoTime() - t) / 1_000_000;
            long selectMs = select.get(5, TimeUnit.SECONDS);
            c1.commit();
            exec(c2, "reset lock_timeout");
            log.add("연결 1: begin; select count(*) (커밋 전)");
            log.add("연결 2: set lock_timeout = '1s'; alter table ... → " + alterResult + " (" + alterMs + "ms)");
            log.add("연결 3: select → ALTER가 물러난 뒤 결과 받음 (" + selectMs + "ms)");
            assertThat(alterResult).startsWith("55P03");
            assertThat(selectMs).isLessThan(2000);
        }
        CiOutput.record(LESSON, "timeout", String.join("\n", log));
    }

    @Test
    @DisplayName("상수 기본값의 열 추가는 테이블 파일(relfilenode)이 그대로이고, 열 타입 바꾸기는 테이블을 다시 써서 바뀐다")
    void rewriteOrNot() {
        String q = "select relfilenode from pg_class where relname = 'mg_member'";
        String before = rows(ds, q).get(0);
        exec(ds, "alter table mg_member add column grade int not null default 0");
        String afterAdd = rows(ds, q).get(0);
        exec(ds, "alter table mg_member alter column grade type bigint");
        String afterType = rows(ds, q).get(0);

        assertThat(afterAdd).isEqualTo(before);
        assertThat(afterType).isNotEqualTo(afterAdd);
        CiOutput.record(LESSON, "rewrite", "relfilenode (테이블 파일 번호)\n처음: " + before
                + "\nadd column grade int not null default 0 뒤: " + afterAdd + " (그대로)"
                + "\nalter column grade type bigint 뒤: " + afterType + " (다시 씀)");
    }

    @Test
    @DisplayName("NOT VALID 제약은 기존 위반 행이 있어도 걸리고 새 위반 행은 막는다. VALIDATE는 기존 위반 행을 고친 뒤에야 성공한다")
    void notValid() {
        exec(ds, "update mg_member set phone = '' where id = 7");
        List<String> log = new ArrayList<>();
        log.add("기존 행 하나의 phone이 '' (위반 행)");
        log.add("add constraint ... check (phone <> '') [바로 검사] → "
                + error(ds, "alter table mg_member add constraint phone_not_blank check (phone <> '')"));
        log.add("add constraint ... not valid → "
                + error(ds, "alter table mg_member add constraint phone_not_blank check (phone <> '') not valid"));
        String newBad = error(ds, "insert into mg_member values (20001, 'x', '')");
        log.add("새 위반 행 insert → " + newBad);
        String validate1 = error(ds, "alter table mg_member validate constraint phone_not_blank");
        log.add("validate constraint → " + validate1);
        exec(ds, "update mg_member set phone = '010-7' where id = 7");
        String validate2 = error(ds, "alter table mg_member validate constraint phone_not_blank");
        log.add("위반 행을 고친 뒤 validate constraint → " + validate2);

        assertThat(newBad).startsWith("23514");
        assertThat(validate1).startsWith("23514");
        assertThat(validate2).isEqualTo("ok");
        CiOutput.record(LESSON, "not-valid", String.join("\n", log));
    }

    @Test
    @DisplayName("create index concurrently는 트랜잭션 블록 안에서 25001 오류이고, 밖에서는 된다")
    void concurrently() throws SQLException {
        String inTx;
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                exec(c, "create index concurrently mg_member_phone on mg_member (phone)");
                inTx = "ok";
            } catch (SQLException e) {
                inTx = e.getSQLState() + " " + e.getMessage().lines().findFirst().orElse("");
            }
            c.rollback();
        }
        String outside = error(ds, "create index concurrently mg_member_phone on mg_member (phone)");
        assertThat(inTx).startsWith("25001");
        assertThat(outside).isEqualTo("ok");
        CiOutput.record(LESSON, "concurrently", "[begin 안에서]\n" + inTx + "\n\n[트랜잭션 밖에서]\n" + outside);
    }

    @Test
    @DisplayName("열 이름을 바꾸면 옛 이름을 쓰는 쿼리는 42703 오류다")
    void renameBreaksOldApp() {
        exec(ds, "alter table mg_member rename column phone to phone_number");
        String oldApp = error(ds, "select phone from mg_member where id = 1");
        assertThat(oldApp).startsWith("42703");
        CiOutput.record(LESSON, "rename", "alter table mg_member rename column phone to phone_number\n"
                + "옛 앱의 select phone ... → " + oldApp);
    }

    @Test
    @DisplayName("버전 기록 테이블이 있으면 이미 적용한 마이그레이션은 건너뛰고 새 것만 적용하며, 적용한 파일을 고치면 체크섬이 달라 멈춘다")
    void miniMigrator() {
        exec(ds, "create table mg_history (version int primary key, description text, checksum int not null, installed_on timestamp default now())");
        Map<Integer, String> files = new LinkedHashMap<>();
        files.put(1, "create table mg_shop (id bigint primary key)");
        files.put(2, "alter table mg_shop add column name text");
        List<String> log = new ArrayList<>();
        log.add("[첫 실행] " + migrate(files));
        log.add("[다시 실행] " + migrate(files));
        files.put(3, "alter table mg_shop add column phone text");
        log.add("[V3를 더하고 실행] " + migrate(files));
        files.put(2, "alter table mg_shop add column name varchar(50)");
        log.add("[적용한 V2를 고치고 실행] " + migrate(files));
        log.add("기록 테이블 (version | description):\n" + String.join("\n",
                rows(ds, "select version, description from mg_history order by version")));
        exec(ds, "drop table if exists mg_shop");

        assertThat(log.get(0)).contains("V1 적용", "V2 적용");
        assertThat(log.get(1)).contains("적용할 것 없음");
        assertThat(log.get(2)).contains("V3 적용").doesNotContain("V1 적용");
        assertThat(log.get(3)).contains("체크섬");
        CiOutput.record(LESSON, "migrator", String.join("\n", log));
    }

    /** Flyway의 생각만 흉내 낸 작은 실행기: 기록에 없는 버전만 순서대로 적용하고, 기록된 파일의 체크섬이 다르면 멈춘다 */
    private String migrate(Map<Integer, String> files) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<Integer, String> f : files.entrySet()) {
            List<String> rec = rows(ds, "select checksum from mg_history where version = " + f.getKey());
            int checksum = f.getValue().hashCode();
            if (!rec.isEmpty()) {
                if (Integer.parseInt(rec.get(0)) != checksum) {
                    out.add("V" + f.getKey() + " 체크섬이 기록과 달라 멈춤");
                    return String.join(", ", out);
                }
                continue;
            }
            exec(ds, f.getValue(), "insert into mg_history(version, description, checksum) values ("
                    + f.getKey() + ", 'V" + f.getKey() + "', " + checksum + ")");
            out.add("V" + f.getKey() + " 적용");
        }
        return out.isEmpty() ? "적용할 것 없음" : String.join(", ", out);
    }
}
