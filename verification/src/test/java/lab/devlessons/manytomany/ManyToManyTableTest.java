package lab.devlessons.manytomany;

import static lab.devlessons.support.Jdbc.error;
import static lab.devlessons.support.Jdbc.exec;
import static lab.devlessons.support.Jdbc.number;
import static lab.devlessons.support.Jdbc.plan;
import static lab.devlessons.support.Jdbc.rows;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import lab.devlessons.support.SqlCapture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 레슨 "다대다와 매핑 테이블"(D18)의 주장을 PostgreSQL 16에서 검증한다.
 * 회원(mt_member)과 스터디(mt_study)를 매핑 테이블로 잇고, 키와 인덱스와 삭제 규칙을 바꿔 가며 본다.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class ManyToManyTableTest {

    private static final String LESSON = "many-to-many-table";

    @Autowired DataSource ds;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        exec(ds,
                "drop table if exists mt_sm_nokey, mt_sm_pk, mt_sm_id, mt_attendance, mt_attendance_bad,"
                        + " mt_membership, mt_sm_cascade, mt_enrollment, mt_sm_setnull,"
                        + " mt_member, mt_study cascade",
                "create table mt_member (id bigint primary key, name text not null)",
                "create table mt_study (id bigint primary key, title text not null)",
                "insert into mt_member values (1, 'kim'), (2, 'lee'), (3, 'park')",
                "insert into mt_study values (1, '자바 스터디'), (2, 'SQL 스터디')");
    }

    @Test
    @DisplayName("키 없는 매핑 테이블에 kim이 자바 스터디에 두 번 들어가면 인원이 3명으로 세어지고, 기본 키가 있으면 23505")
    void duplicatePair() {
        exec(ds,
                """
                create table mt_sm_nokey (
                  member_id bigint not null references mt_member(id),
                  study_id bigint not null references mt_study(id))""",
                "insert into mt_sm_nokey values (1, 1), (1, 1), (2, 1)");
        long count = number(ds, "select count(*) from mt_sm_nokey where study_id = 1");
        List<String> names = rows(ds, """
                select m.name from mt_sm_nokey sm
                  join mt_member m on m.id = sm.member_id
                 where sm.study_id = 1 order by m.id""");

        exec(ds,
                """
                create table mt_sm_pk (
                  member_id bigint not null references mt_member(id),
                  study_id bigint not null references mt_study(id),
                  primary key (member_id, study_id))""",
                "insert into mt_sm_pk values (1, 1), (2, 1)");
        String second = error(ds, "insert into mt_sm_pk values (1, 1)");

        assertThat(count).isEqualTo(3);
        assertThat(names).containsExactly("kim", "kim", "lee");
        assertThat(second).startsWith("23505");
        CiOutput.record(LESSON, "dup", String.join("\n",
                "[키 없음] 자바 스터디 인원 count(*): " + count,
                "[키 없음] 자바 스터디 회원 목록: " + names,
                "",
                "[primary key (member_id, study_id)] kim을 한 번 더: " + second));
    }

    @Test
    @DisplayName("따로 id를 둔 매핑 테이블은 UNIQUE가 없으면 같은 쌍이 들어가고, UNIQUE를 걸면 막지만 인덱스가 둘이 된다")
    void surrogateIdNeedsUnique() {
        exec(ds,
                """
                create table mt_sm_id (
                  id bigint generated always as identity primary key,
                  member_id bigint not null references mt_member(id),
                  study_id bigint not null references mt_study(id))""",
                "insert into mt_sm_id(member_id, study_id) values (1, 1)");
        String withoutUnique = error(ds, "insert into mt_sm_id(member_id, study_id) values (1, 1)");
        long rowsWithout = number(ds, "select count(*) from mt_sm_id where member_id = 1 and study_id = 1");

        exec(ds, "delete from mt_sm_id where id = 2",
                "alter table mt_sm_id add constraint mt_sm_id_pair unique (member_id, study_id)");
        String withUnique = error(ds, "insert into mt_sm_id(member_id, study_id) values (1, 1)");

        exec(ds, """
                create table mt_sm_pk (
                  member_id bigint not null references mt_member(id),
                  study_id bigint not null references mt_study(id),
                  primary key (member_id, study_id))""");
        List<String> compositeIdx = rows(ds, "select indexdef from pg_indexes where tablename = 'mt_sm_pk' order by indexname");
        List<String> surrogateIdx = rows(ds, "select indexdef from pg_indexes where tablename = 'mt_sm_id' order by indexname");

        assertThat(withoutUnique).isEqualTo("ok");
        assertThat(rowsWithout).isEqualTo(2);
        assertThat(withUnique).startsWith("23505");
        assertThat(compositeIdx).hasSize(1);
        assertThat(surrogateIdx).hasSize(2);
        CiOutput.record(LESSON, "surrogate", String.join("\n",
                "[id만 기본 키] kim을 자바 스터디에 한 번 더: " + withoutUnique + " (같은 쌍 " + rowsWithout + "행)",
                "[id + unique (member_id, study_id)] 한 번 더: " + withUnique,
                "",
                "[복합 기본 키 테이블의 인덱스]", short_(compositeIdx),
                "[id + unique 테이블의 인덱스]", short_(surrogateIdx)));
    }

    @Test
    @DisplayName("복합 기본 키를 가리키는 외래 키는 두 열이어야 한다: 한 열만 가리키면 42830")
    void compositeKeyReference() {
        exec(ds, """
                create table mt_sm_pk (
                  member_id bigint not null references mt_member(id),
                  study_id bigint not null references mt_study(id),
                  primary key (member_id, study_id))""");
        String twoColumns = error(ds, """
                create table mt_attendance (
                  id bigint primary key,
                  member_id bigint not null,
                  study_id bigint not null,
                  attended_on date not null,
                  foreign key (member_id, study_id)
                    references mt_sm_pk(member_id, study_id))""");
        String oneColumn = error(ds, """
                create table mt_attendance_bad (
                  id bigint primary key,
                  member_id bigint not null references mt_sm_pk(member_id))""");

        assertThat(twoColumns).isEqualTo("ok");
        assertThat(oneColumn).startsWith("42830");
        CiOutput.record(LESSON, "reference", String.join("\n",
                "foreign key (member_id, study_id): " + twoColumns,
                "member_id 한 열만: " + oneColumn));
    }

    @Test
    @DisplayName("기본 키 (member_id, study_id)는 member_id로 찾을 때만 쓰이고, study_id로 찾으면 Seq Scan이다. (study_id, member_id) 인덱스를 더하면 Index Only Scan")
    void bothDirectionIndexes() {
        exec(ds,
                "drop table if exists mt_big_sm, mt_big_member, mt_big_study",
                "create table mt_big_member (id bigint primary key)",
                "create table mt_big_study (id bigint primary key)",
                "insert into mt_big_member select g from generate_series(1, 20000) g",
                "insert into mt_big_study select g from generate_series(1, 2000) g",
                """
                create table mt_big_sm (
                  member_id bigint not null references mt_big_member(id),
                  study_id bigint not null references mt_big_study(id),
                  primary key (member_id, study_id))""",
                // 회원마다 스터디 5개, 스터디마다 회원 약 50명
                """
                insert into mt_big_sm
                select m, ((m * 7 + k * 131) % 2000) + 1
                  from generate_series(1, 20000) m, generate_series(1, 5) k
                on conflict do nothing""",
                "vacuum analyze mt_big_sm");
        long total = number(ds, "select count(*) from mt_big_sm");
        String byMember = plan(ds, "explain (costs off) select study_id from mt_big_sm where member_id = 42");
        String byStudy = plan(ds, "explain (costs off) select member_id from mt_big_sm where study_id = 42");

        exec(ds, "create index mt_big_sm_study_idx on mt_big_sm (study_id, member_id)", "vacuum analyze mt_big_sm");
        String byStudyAfter = plan(ds, "explain (costs off) select member_id from mt_big_sm where study_id = 42");
        exec(ds, "drop table mt_big_sm, mt_big_member, mt_big_study");

        assertThat(byMember).contains("mt_big_sm_pkey");
        assertThat(byStudy).contains("Seq Scan on mt_big_sm");
        assertThat(byStudyAfter).contains("mt_big_sm_study_idx").doesNotContain("Seq Scan");
        CiOutput.record(LESSON, "explain", String.join("\n",
                "매핑 행 " + total + "개, 기본 키 (member_id, study_id)",
                "",
                "[회원 42의 스터디: where member_id = 42]", byMember,
                "",
                "[스터디 42의 회원: where study_id = 42]", byStudy,
                "",
                "[(study_id, member_id) 인덱스를 더한 뒤]", byStudyAfter));
    }

    @Test
    @DisplayName("탈퇴 기록을 남기면 같은 쌍이 다시 생긴다: 복합 기본 키는 재가입을 막고, 부분 유니크 인덱스는 활동 중인 행만 하나로 막는다")
    void rejoinHistory() {
        exec(ds,
                """
                create table mt_sm_pk (
                  member_id bigint not null references mt_member(id),
                  study_id bigint not null references mt_study(id),
                  joined_at date not null,
                  left_at date,
                  primary key (member_id, study_id))""",
                "insert into mt_sm_pk values (1, 1, '2026-01-05', '2026-03-02')");
        String rejoinPk = error(ds, "insert into mt_sm_pk values (1, 1, '2026-06-01', null)");

        exec(ds,
                """
                create table mt_membership (
                  id bigint generated always as identity primary key,
                  member_id bigint not null references mt_member(id),
                  study_id bigint not null references mt_study(id),
                  role text not null check (role in ('LEADER', 'MEMBER')),
                  joined_at date not null,
                  left_at date)""",
                """
                create unique index mt_membership_active
                  on mt_membership (member_id, study_id)
                  where left_at is null""",
                "insert into mt_membership(member_id, study_id, role, joined_at) values (1, 1, 'MEMBER', '2026-01-05')");
        String twoActive = error(ds,
                "insert into mt_membership(member_id, study_id, role, joined_at) values (1, 1, 'MEMBER', '2026-01-06')");
        exec(ds, "update mt_membership set left_at = '2026-03-02' where member_id = 1 and study_id = 1");
        String rejoin = error(ds,
                "insert into mt_membership(member_id, study_id, role, joined_at) values (1, 1, 'LEADER', '2026-06-01')");
        List<String> history = rows(ds,
                "select member_id, study_id, role, joined_at, left_at from mt_membership order by joined_at");

        assertThat(rejoinPk).startsWith("23505");
        assertThat(twoActive).startsWith("23505");
        assertThat(rejoin).isEqualTo("ok");
        assertThat(history).hasSize(2);
        CiOutput.record(LESSON, "rejoin", String.join("\n",
                "[복합 기본 키] 탈퇴한 kim이 다시 가입: " + rejoinPk,
                "",
                "[id + 부분 유니크 인덱스 where left_at is null]",
                "활동 중에 한 번 더 가입: " + twoActive,
                "탈퇴 처리 뒤 다시 가입: " + rejoin,
                "(member_id | study_id | role | joined_at | left_at)", String.join("\n", history)));
    }

    @Test
    @DisplayName("스터디를 지우면 CASCADE 매핑 행은 함께 지워지고 회원은 남는다. NO ACTION 수강 기록은 막고(23503), 키 열의 SET NULL은 23502로 실패한다")
    void onDelete() {
        exec(ds,
                """
                create table mt_sm_cascade (
                  member_id bigint not null references mt_member(id) on delete cascade,
                  study_id bigint not null references mt_study(id) on delete cascade,
                  primary key (member_id, study_id))""",
                "insert into mt_sm_cascade values (1, 1), (2, 1), (1, 2)",
                "delete from mt_study where id = 1");
        List<String> left = rows(ds, "select member_id, study_id from mt_sm_cascade order by 1, 2");
        long members = number(ds, "select count(*) from mt_member");

        exec(ds,
                """
                create table mt_enrollment (
                  member_id bigint not null references mt_member(id),
                  study_id bigint not null references mt_study(id),
                  grade text,
                  primary key (member_id, study_id))""",
                "insert into mt_enrollment values (1, 2, 'A')");
        String restrict = error(ds, "delete from mt_study where id = 2");

        exec(ds,
                """
                create table mt_sm_setnull (
                  member_id bigint references mt_member(id) on delete set null,
                  study_id bigint references mt_study(id),
                  primary key (member_id, study_id))""",
                "delete from mt_enrollment", "delete from mt_sm_cascade",
                "insert into mt_sm_setnull values (3, 2)");
        String setNull = error(ds, "delete from mt_member where id = 3");

        assertThat(left).containsExactly("1 | 2");
        assertThat(members).isEqualTo(3);
        assertThat(restrict).startsWith("23503");
        assertThat(setNull).startsWith("23502");
        CiOutput.record(LESSON, "on-delete", String.join("\n",
                "[on delete cascade] 자바 스터디(1)를 지운 뒤",
                "남은 매핑 (member_id | study_id): " + left,
                "남은 회원: " + members + "명",
                "",
                "[기본(NO ACTION), 성적이 있는 수강 기록] SQL 스터디(2) 삭제: " + restrict,
                "",
                "[키 열에 on delete set null] park 삭제: " + setNull));
    }

    @Test
    @DisplayName("on conflict do nothing: 같은 쌍을 두 번 넣으면 첫 번째는 1행, 두 번째는 0행이고 오류가 없다")
    void onConflictDoNothing() {
        exec(ds, """
                create table mt_sm_pk (
                  member_id bigint not null references mt_member(id),
                  study_id bigint not null references mt_study(id),
                  primary key (member_id, study_id))""");
        String sql = """
                insert into mt_sm_pk(member_id, study_id)
                values (?, ?)
                on conflict (member_id, study_id) do nothing""";
        int first = jdbc.update(sql, 1L, 1L);
        int second = jdbc.update(sql, 1L, 1L);

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        CiOutput.record(LESSON, "on-conflict", "첫 번째 가입: " + first + "행\n두 번째 가입: " + second + "행 (오류 없음)");
    }

    @Test
    @DisplayName("@ManyToMany List는 매핑 테이블에 기본 키를 만들지 않아 같은 쌍이 들어가고, 하나를 빼면 전부 지우고 다시 넣는다. Set은 기본 키가 있고 한 행만 지운다")
    void manyToManyListVersusSet() {
        clearJpa();
        Long memberId = tx.execute(s -> {
            MtmStudy java = new MtmStudy("자바"), sql = new MtmStudy("SQL"), spring = new MtmStudy("스프링");
            em.persist(java);
            em.persist(sql);
            em.persist(spring);
            MtmMember kim = new MtmMember("kim");
            kim.getStudies().addAll(List.of(java, sql, spring));
            kim.getStudySet().addAll(List.of(java, sql, spring));
            em.persist(kim);
            return kim.getId();
        });
        List<String> listIdx = rows(ds, "select indexdef from pg_indexes where tablename = 'mtm_member_study_list'");
        List<String> setIdx = rows(ds, "select indexdef from pg_indexes where tablename = 'mtm_member_study_set'");

        SqlCapture.clear();
        tx.executeWithoutResult(s -> em.find(MtmMember.class, memberId).getStudies().remove(0));
        List<String> listSql = changes(SqlCapture.sqls());
        SqlCapture.clear();
        tx.executeWithoutResult(s -> {
            MtmMember kim = em.find(MtmMember.class, memberId);
            kim.getStudySet().removeIf(st -> st.getTitle().equals("자바"));
        });
        List<String> setSql = changes(SqlCapture.sqls());

        tx.executeWithoutResult(s -> {
            MtmMember kim = em.find(MtmMember.class, memberId);
            kim.getStudies().add(kim.getStudies().get(0));
        });
        long dupRows = number(ds, "select count(*) - count(distinct (member_id, study_id)) from mtm_member_study_list");

        assertThat(listIdx).isEmpty();
        assertThat(setIdx).hasSize(1).first().asString().contains("(member_id, study_id)");
        assertThat(listSql).hasSize(3);
        assertThat(listSql.get(0)).startsWith("delete from mtm_member_study_list where member_id=?");
        assertThat(setSql).containsExactly("delete from mtm_member_study_set where member_id=? and study_id=?");
        assertThat(dupRows).isEqualTo(1);
        CiOutput.record(LESSON, "list-vs-set", String.join("\n",
                "[List] 매핑 테이블의 인덱스: " + (listIdx.isEmpty() ? "없음 (기본 키 없음)" : String.join(", ", listIdx)),
                "[List] 스터디 셋 가운데 하나를 뺀 뒤 나간 SQL", String.join("\n", listSql),
                "[List] 같은 스터디를 한 번 더 add: 같은 쌍 " + dupRows + "행이 더 들어감",
                "",
                "[Set] 매핑 테이블의 인덱스: " + short_(setIdx),
                "[Set] 하나를 뺀 뒤 나간 SQL", String.join("\n", setSql)));
    }

    @Test
    @DisplayName("연결 엔티티: 같은 쌍을 두 번 저장하면 UNIQUE 위반, 하나를 빼면 그 행만 지운다")
    void connectionEntity() {
        clearJpa();
        Long[] ids = tx.execute(s -> {
            MtmStudy java = new MtmStudy("자바");
            MtmMember kim = new MtmMember("kim");
            em.persist(java);
            em.persist(kim);
            em.persist(new MtmStudyMember(kim, java, "LEADER", LocalDate.of(2026, 1, 5)));
            return new Long[] {kim.getId(), java.getId()};
        });
        Throwable dup = null;
        try {
            tx.executeWithoutResult(s -> em.persist(new MtmStudyMember(
                    em.find(MtmMember.class, ids[0]), em.find(MtmStudy.class, ids[1]), "MEMBER", LocalDate.of(2026, 1, 6))));
        } catch (RuntimeException e) {
            dup = e;
        }
        SqlCapture.clear();
        tx.executeWithoutResult(s -> {
            MtmStudyMember sm = em.createQuery(
                    "select sm from MtmStudyMember sm where sm.member.id = :m and sm.study.id = :s", MtmStudyMember.class)
                    .setParameter("m", ids[0]).setParameter("s", ids[1]).getSingleResult();
            em.remove(sm);
        });
        List<String> removeSql = changes(SqlCapture.sqls());

        assertThat(dup).isNotNull();
        assertThat(removeSql).containsExactly("delete from mtm_study_member where id=?");
        CiOutput.record(LESSON, "entity", String.join("\n",
                "같은 쌍을 한 번 더 저장: " + root(dup),
                "멤버십 하나를 지울 때 나간 SQL: " + String.join("\n", removeSql)));
    }

    private void clearJpa() {
        tx.executeWithoutResult(s -> {
            em.createNativeQuery("delete from mtm_study_member").executeUpdate();
            em.createNativeQuery("delete from mtm_member_study_list").executeUpdate();
            em.createNativeQuery("delete from mtm_member_study_set").executeUpdate();
            em.createNativeQuery("delete from mtm_member").executeUpdate();
            em.createNativeQuery("delete from mtm_study").executeUpdate();
        });
    }

    /** SELECT를 빼고 바꾸는 SQL만 남긴다 */
    private static List<String> changes(List<String> sqls) {
        return sqls.stream().filter(q -> !q.startsWith("select")).toList();
    }

    /** "CREATE UNIQUE INDEX x ON public.t USING btree (a, b)"를 "x (a, b)"로 줄인다 */
    private static String short_(List<String> defs) {
        return String.join("\n", defs.stream()
                .map(d -> d.replaceAll("CREATE (UNIQUE )?INDEX (\\S+) ON \\S+ USING btree (.*)", "$2 $3"
                        + (d.contains("UNIQUE") ? " UNIQUE" : "")))
                .toList());
    }

    private static String root(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null) {
            c = c.getCause();
        }
        return c.getMessage().lines().findFirst().orElse("");
    }
}
