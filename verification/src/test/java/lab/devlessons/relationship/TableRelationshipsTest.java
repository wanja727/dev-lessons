package lab.devlessons.relationship;

import static lab.devlessons.support.Jdbc.error;
import static lab.devlessons.support.Jdbc.exec;
import static lab.devlessons.support.Jdbc.number;
import static lab.devlessons.support.Jdbc.rows;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.List;
import javax.sql.DataSource;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 레슨 "테이블 관계 설계"(D17)의 주장을 PostgreSQL 16에서 검증한다.
 * 회원(tr_member), 글(tr_post), 프로필(tr_profile), 스터디(tr_study)로 1:N, 1:1, N:M을 테이블로 옮긴다.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class TableRelationshipsTest {

    private static final String LESSON = "table-relationships";

    @Autowired DataSource ds;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        exec(ds,
                "drop table if exists tr_post, tr_member_bad, tr_profile_free, tr_profile, tr_profile_shared,"
                        + " tr_study_bad, tr_study_member, tr_study, tr_member cascade",
                "create table tr_member (id bigint primary key, name text not null)",
                "insert into tr_member values (1, 'kim'), (2, 'lee'), (3, 'park')");
    }

    @Test
    @DisplayName("1:N은 N 쪽(글)에 외래 키 하나를 두면 회원의 글과 글의 작성자를 둘 다 찾는다")
    void oneToManyFkOnManySide() {
        exec(ds,
                """
                create table tr_post (
                  id bigint primary key,
                  member_id bigint not null references tr_member(id),
                  title text not null)""",
                "insert into tr_post values (10, 1, '첫 글'), (11, 1, '둘째 글'), (12, 1, '셋째 글'), (13, 2, 'lee의 글')");
        List<String> kimPosts = rows(ds, "select id, title from tr_post where member_id = 1 order by id");
        List<String> writer = rows(ds,
                "select p.id, m.name from tr_post p join tr_member m on m.id = p.member_id where p.id = 12");

        assertThat(kimPosts).hasSize(3);
        assertThat(writer).containsExactly("12 | kim");
        CiOutput.record(LESSON, "one-to-many", String.join("\n",
                "[회원 1(kim)의 글] (id | title)", String.join("\n", kimPosts),
                "", "[글 12의 작성자] (id | name)", String.join("\n", writer)));
    }

    @Test
    @DisplayName("1 쪽(회원)에 글의 외래 키를 두면 글 셋을 가지려고 kim 행이 셋이 되고, 한 행만 고치면 이름이 어긋난다")
    void fkOnOneSideDuplicatesParent() {
        exec(ds,
                "create table tr_member_bad (id bigint primary key, name text not null, post_id bigint)",
                "insert into tr_member_bad values (1, 'kim', 10), (2, 'kim', 11), (3, 'kim', 12)",
                "update tr_member_bad set name = 'kim2' where id = 1");
        List<String> kim = rows(ds, "select id, name, post_id from tr_member_bad order by id");
        long names = number(ds, "select count(distinct name) from tr_member_bad");

        assertThat(kim).hasSize(3);
        assertThat(names).isEqualTo(2);
        CiOutput.record(LESSON, "fk-on-one", "이름을 바꾼 뒤 (id | name | post_id)\n" + String.join("\n", kim)
                + "\n한 사람의 이름이 " + names + "가지가 됐어요");
    }

    @Test
    @DisplayName("1:1은 외래 키에 UNIQUE가 있어야 한다: 없으면 프로필이 둘 들어가고, UNIQUE나 공유 기본 키면 23505")
    void oneToOneNeedsUnique() {
        exec(ds,
                "create table tr_profile_free (id bigint primary key, member_id bigint not null references tr_member(id), bio text)",
                "create table tr_profile (id bigint primary key, member_id bigint not null unique references tr_member(id), bio text)",
                "create table tr_profile_shared (member_id bigint primary key references tr_member(id), bio text)",
                "insert into tr_profile_free values (1, 1, 'a')",
                "insert into tr_profile values (1, 1, 'a')",
                "insert into tr_profile_shared values (1, 'a')");
        String free = error(ds, "insert into tr_profile_free values (2, 1, 'b')");
        long freeCount = number(ds, "select count(*) from tr_profile_free where member_id = 1");
        String unique = error(ds, "insert into tr_profile values (2, 1, 'b')");
        String shared = error(ds, "insert into tr_profile_shared values (1, 'b')");

        assertThat(free).isEqualTo("ok");
        assertThat(freeCount).isEqualTo(2);
        assertThat(unique).startsWith("23505");
        assertThat(shared).startsWith("23505");
        CiOutput.record(LESSON, "one-to-one", String.join("\n",
                "외래 키만: kim의 둘째 프로필 " + free + ", kim의 프로필 " + freeCount + "개",
                "외래 키 + UNIQUE: " + unique,
                "공유 기본 키: " + shared));
    }

    @Test
    @DisplayName("N:M을 외래 키 하나로 담으면 스터디 행이 회원 수만큼 늘고, 매핑 테이블이면 양쪽 방향을 다 찾는다")
    void manyToManyNeedsMappingTable() {
        exec(ds,
                "create table tr_study_bad (id bigint primary key, title text not null, member_id bigint references tr_member(id))",
                "insert into tr_study_bad values (1, '자바 스터디', 1), (2, '자바 스터디', 2), (3, 'SQL 스터디', 1)");
        List<String> bad = rows(ds, "select id, title, member_id from tr_study_bad order by id");

        exec(ds,
                "create table tr_study (id bigint primary key, title text not null)",
                "insert into tr_study values (1, '자바 스터디'), (2, 'SQL 스터디')",
                """
                create table tr_study_member (
                  member_id bigint references tr_member(id),
                  study_id bigint references tr_study(id),
                  primary key (member_id, study_id))""",
                "insert into tr_study_member values (1, 1), (2, 1), (1, 2)");
        List<String> kimStudies = rows(ds, """
                select s.title from tr_study_member sm
                  join tr_study s on s.id = sm.study_id
                 where sm.member_id = 1 order by s.id""");
        List<String> javaMembers = rows(ds, """
                select m.name from tr_study_member sm
                  join tr_member m on m.id = sm.member_id
                 where sm.study_id = 1 order by m.id""");

        assertThat(bad).hasSize(3);
        assertThat(kimStudies).containsExactly("자바 스터디", "SQL 스터디");
        assertThat(javaMembers).containsExactly("kim", "lee");
        CiOutput.record(LESSON, "many-to-many", String.join("\n",
                "[study에 member_id 하나] (id | title | member_id)", String.join("\n", bad),
                "", "[매핑 테이블] kim의 스터디: " + kimStudies,
                "[매핑 테이블] 자바 스터디의 회원: " + javaMembers));
    }

    @Test
    @DisplayName("JPA @OneToOne은 외래 키에 UNIQUE를 만들지만, 마이그레이션에 UNIQUE가 빠지면 프로필 둘이 저장되고 회원 조회가 실패한다")
    void oneToOneWithoutUniqueInMigration() {
        tx.executeWithoutResult(s -> {
            em.createNativeQuery("delete from rel_profile").executeUpdate();
            em.createNativeQuery("delete from rel_member").executeUpdate();
        });
        List<String> generated = rows(ds, """
                select conname, pg_get_constraintdef(oid) from pg_constraint
                 where conrelid = 'rel_profile'::regclass and contype = 'u'""");
        Long memberId = tx.execute(s -> {
            RelMember m = new RelMember("kim");
            em.persist(m);
            em.persist(new RelProfile(m, "첫 프로필"));
            return m.getId();
        });
        Throwable withUnique = catchThrowable(() -> tx.executeWithoutResult(s ->
                em.persist(new RelProfile(em.find(RelMember.class, memberId), "둘째 프로필"))));

        // 손으로 쓴 마이그레이션에 UNIQUE를 빠뜨린 상황
        exec(ds, "alter table rel_profile drop constraint rel_profile_member_id_key");
        try {
            tx.executeWithoutResult(s ->
                    em.persist(new RelProfile(em.find(RelMember.class, memberId), "둘째 프로필")));
            long profiles = number(ds, "select count(*) from rel_profile where member_id = " + memberId);
            Throwable load = catchThrowable(() -> tx.executeWithoutResult(s -> {
                em.clear();
                em.find(RelMember.class, memberId);
            }));

            assertThat(generated).containsExactly("rel_profile_member_id_key | UNIQUE (member_id)");
            assertThat(withUnique).isNotNull();
            assertThat(profiles).isEqualTo(2);
            assertThat(load).hasMessageContaining("Duplicate row");
            CiOutput.record(LESSON, "jpa-one-to-one", String.join("\n",
                    "[ddl-auto가 만든 제약] " + generated.get(0),
                    "UNIQUE 있음, 둘째 프로필 저장: " + rootMessage(withUnique),
                    "",
                    "UNIQUE 없음, 둘째 프로필 저장: 성공 (kim의 프로필 " + profiles + "개)",
                    "회원 kim 조회: " + load.getClass().getSimpleName() + ": " + load.getMessage()));
        } finally {
            tx.executeWithoutResult(s -> em.createNativeQuery("delete from rel_profile").executeUpdate());
            exec(ds, "alter table rel_profile add constraint rel_profile_member_id_key unique (member_id)");
        }
    }

    private static Throwable catchThrowable(Runnable r) {
        try {
            r.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null) {
            c = c.getCause();
        }
        return c.getMessage().lines().findFirst().orElse("");
    }
}
