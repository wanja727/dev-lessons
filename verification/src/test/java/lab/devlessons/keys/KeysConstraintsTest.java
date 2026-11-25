package lab.devlessons.keys;

import static lab.devlessons.support.Jdbc.error;
import static lab.devlessons.support.Jdbc.exec;
import static lab.devlessons.support.Jdbc.number;
import static lab.devlessons.support.Jdbc.rows;
import static lab.devlessons.support.Jdbc.sqlState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import javax.sql.DataSource;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 레슨 "키와 제약 조건"(D1)의 주장을 PostgreSQL 16에서 검증한다.
 * 회원(kc_member)과 주문(kc_orders) 두 테이블. 제약을 걸고 빼며 잘못된 데이터가 들어가는지 본다.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class KeysConstraintsTest {

    private static final String LESSON = "keys-constraints";

    @Autowired DataSource ds;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        exec(ds,
                "drop table if exists kc_orders, kc_orders_cascade, kc_orders_setnull, kc_orders_nofk, kc_member cascade",
                "drop table if exists kc_signup, kc_nulls, kc_nulls_nd, kc_review",
                """
                create table kc_member (
                  id bigint generated always as identity primary key,
                  email text not null unique,
                  name text not null)""",
                """
                create table kc_orders (
                  id bigint generated always as identity primary key,
                  member_id bigint not null references kc_member(id),
                  amount int not null check (amount > 0))""",
                "insert into kc_member(email, name) values ('kim@a.com', 'kim'), ('lee@a.com', 'lee')");
    }

    @Test
    @DisplayName("제약 위반은 SQLState로 구분된다: 기본 키와 유니크 23505, 외래 키 23503, NOT NULL 23502, CHECK 23514")
    void sqlStates() {
        exec(ds, "create table kc_plain (id int primary key)", "insert into kc_plain values (1)");
        String pk = error(ds, "insert into kc_plain values (1)");
        String unique = error(ds, "insert into kc_member(email, name) values ('kim@a.com', 'kim2')");
        String fk = error(ds, "insert into kc_orders(member_id, amount) values (999, 10000)");
        String notNull = error(ds, "insert into kc_member(email) values ('park@a.com')");
        String check = error(ds, "insert into kc_orders(member_id, amount) values (1, 0)");
        exec(ds, "drop table kc_plain");

        assertThat(pk).startsWith("23505");
        assertThat(unique).startsWith("23505");
        assertThat(fk).startsWith("23503");
        assertThat(notNull).startsWith("23502");
        assertThat(check).startsWith("23514");
        CiOutput.record(LESSON, "states", String.join("\n",
                "기본 키 중복: " + pk,
                "이메일 중복: " + unique,
                "없는 회원의 주문: " + fk,
                "이름 없는 회원: " + notNull,
                "0원 주문: " + check));
    }

    @Test
    @DisplayName("identity 기본 키에 id를 직접 넣으면 막는다(428C9)")
    void identityAlways() {
        assertThat(sqlState(ds, "insert into kc_member(id, email, name) values (100, 'x@a.com', 'x')")).isEqualTo("428C9");
    }

    @Test
    @DisplayName("외래 키가 없으면 없는 회원 999의 주문이 그대로 들어간다")
    void orphanWithoutFk() {
        exec(ds, "create table kc_orders_nofk (id bigint generated always as identity primary key, member_id bigint not null, amount int not null)",
                "insert into kc_orders_nofk(member_id, amount) values (999, 10000)");
        long orphans = number(ds, "select count(*) from kc_orders_nofk o where not exists (select 1 from kc_member m where m.id = o.member_id)");

        assertThat(orphans).isEqualTo(1);
        assertThat(sqlState(ds, "insert into kc_orders(member_id, amount) values (999, 10000)")).isEqualTo("23503");
        CiOutput.record(LESSON, "orphan", String.join("\n",
                "외래 키 없음: 들어감. 주인 없는 주문 " + orphans + "건",
                "외래 키 있음: " + error(ds, "insert into kc_orders(member_id, amount) values (999, 10000)")));
    }

    @Test
    @DisplayName("부모 삭제: NO ACTION은 막고(23503), CASCADE는 자식까지 지우고, SET NULL은 자식의 외래 키를 NULL로 바꾼다")
    void onDelete() {
        exec(ds,
                "create table kc_orders_cascade (id int primary key, member_id bigint references kc_member(id) on delete cascade)",
                "create table kc_orders_setnull (id int primary key, member_id bigint references kc_member(id) on delete set null)",
                "insert into kc_orders(member_id, amount) values (1, 1000), (1, 2000)");

        String noAction = error(ds, "delete from kc_member where id = 1");
        exec(ds, "delete from kc_orders");
        exec(ds, "insert into kc_orders_cascade values (1, 1), (2, 1)", "insert into kc_orders_setnull values (1, 1), (2, 1)");
        // 두 자식 테이블이 같은 회원을 가리킨다. 회원을 지우면 두 규칙이 함께 적용된다
        exec(ds, "delete from kc_member where id = 1");
        long cascadeLeft = number(ds, "select count(*) from kc_orders_cascade");
        List<String> setNull = rows(ds, "select id, member_id from kc_orders_setnull order by id");

        assertThat(noAction).startsWith("23503");
        assertThat(cascadeLeft).isZero();
        assertThat(setNull).containsExactly("1 | NULL", "2 | NULL");
        CiOutput.record(LESSON, "on-delete", String.join("\n",
                "NO ACTION: " + noAction,
                "CASCADE: 남은 주문 " + cascadeLeft + "건",
                "SET NULL: 남은 주문 " + setNull.size() + "건, (id | member_id) " + setNull));
    }

    @Test
    @DisplayName("UNIQUE 열에 NULL은 여러 개 들어가고, NULLS NOT DISTINCT면 두 번째부터 실패한다")
    void uniqueAndNull() {
        exec(ds, "create table kc_nulls (email text unique)",
                "insert into kc_nulls values (null)", "insert into kc_nulls values (null)", "insert into kc_nulls values (null)",
                "create table kc_nulls_nd (email text unique nulls not distinct)", "insert into kc_nulls_nd values (null)");
        long nulls = number(ds, "select count(*) from kc_nulls");
        String second = error(ds, "insert into kc_nulls_nd values (null)");

        assertThat(nulls).isEqualTo(3);
        assertThat(second).startsWith("23505");
        CiOutput.record(LESSON, "unique-null", "unique: NULL 세 번 넣은 뒤 행 수 " + nulls
                + "\nunique nulls not distinct: 두 번째 NULL " + second);
    }

    @Test
    @DisplayName("복합 UNIQUE (member_id, post_id): (1,10), (1,11), (2,10)은 들어가고 다시 넣은 (1,10)만 실패한다")
    void compositeUnique() {
        exec(ds, "create table kc_review (member_id int, post_id int, unique (member_id, post_id))");
        List<String> results = new ArrayList<>();
        for (String v : List.of("(1, 10)", "(1, 11)", "(2, 10)", "(1, 10)")) {
            results.add(v + " " + sqlState(ds, "insert into kc_review values " + v));
        }
        assertThat(results).containsExactly("(1, 10) ok", "(1, 11) ok", "(2, 10) ok", "(1, 10) 23505");
    }

    @Test
    @DisplayName("롤백한 INSERT가 받은 identity 번호는 돌아오지 않아 빈 번호가 생긴다")
    void identityGap() throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            exec(c, "insert into kc_member(email, name) values ('park@a.com', 'park')");
            c.rollback();
        }
        exec(ds, "insert into kc_member(email, name) values ('choi@a.com', 'choi')");
        List<String> ids = rows(ds, "select id, name from kc_member order by id");

        assertThat(ids).containsExactly("1 | kim", "2 | lee", "4 | choi");
        CiOutput.record(LESSON, "gap", "(id | name)\n" + String.join("\n", ids));
    }

    @Test
    @DisplayName("UNIQUE 없이 두 요청이 동시에 '세고 0이면 넣기'를 하면 같은 이메일 회원이 2명, UNIQUE가 있으면 1명이고 한쪽은 23505")
    void checkThenInsertRace() throws Exception {
        exec(ds, "create table kc_signup (id bigint generated always as identity primary key, email text not null)");
        List<String> noUnique = race("kc_signup");
        long withoutUnique = number(ds, "select count(*) from kc_signup where email = 'kim@race.com'");

        exec(ds, "delete from kc_signup", "alter table kc_signup add constraint kc_signup_email_key unique (email)");
        List<String> withUnique = race("kc_signup");
        long withUniqueCount = number(ds, "select count(*) from kc_signup where email = 'kim@race.com'");

        assertThat(withoutUnique).isEqualTo(2);
        assertThat(withUniqueCount).isEqualTo(1);
        assertThat(withUnique).anyMatch(r -> r.contains("23505"));
        CiOutput.record(LESSON, "race", String.join("\n",
                "[UNIQUE 없음]", String.join("\n", noUnique), "회원 수: " + withoutUnique,
                "", "[UNIQUE 있음]", String.join("\n", withUnique), "회원 수: " + withUniqueCount));
    }

    /** 두 요청이 모두 센 뒤에 넣도록 맞춘다 */
    private List<String> race(String table) throws Exception {
        CyclicBarrier bothCounted = new CyclicBarrier(2);
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        Runnable signup = () -> {
            String who = Thread.currentThread().getName().endsWith("1") ? "요청 A" : "요청 B";
            try (Connection c = ds.getConnection()) {
                long count = number(c, "select count(*) from " + table + " where email = 'kim@race.com'");
                bothCounted.await();
                if (count == 0) {
                    exec(c, "insert into " + table + "(email) values ('kim@race.com')");
                    log.add(who + ": 센 값 " + count + ", 넣기 성공");
                }
            } catch (SQLException e) {
                log.add(who + ": 센 값 0, 넣기 실패 " + e.getSQLState());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
        Thread a = new Thread(signup, "signup-1");
        Thread b = new Thread(signup, "signup-2");
        a.start();
        b.start();
        a.join();
        b.join();
        Collections.sort(log);
        return log;
    }

    @Test
    @DisplayName("스프링 JdbcTemplate은 유니크 위반을 DuplicateKeyException, 외래 키 위반을 DataIntegrityViolationException으로 던진다")
    void springTranslation() {
        assertThatThrownBy(() -> jdbc.update("insert into kc_member(email, name) values (?, ?)", "kim@a.com", "kim"))
                .isInstanceOf(DuplicateKeyException.class);
        Throwable fk = org.assertj.core.api.Assertions.catchThrowable(
                () -> jdbc.update("insert into kc_orders(member_id, amount) values (?, ?)", 999, 10000));
        Throwable dup = org.assertj.core.api.Assertions.catchThrowable(
                () -> jdbc.update("insert into kc_member(email, name) values (?, ?)", "kim@a.com", "kim"));

        assertThat(fk).isInstanceOf(DataIntegrityViolationException.class).isNotInstanceOf(DuplicateKeyException.class);
        CiOutput.record(LESSON, "spring", String.join("\n",
                "이메일 중복: " + dup.getClass().getName(),
                "없는 회원의 주문: " + fk.getClass().getName(),
                "DuplicateKeyException은 DataIntegrityViolationException의 하위 클래스: "
                        + DataIntegrityViolationException.class.isAssignableFrom(DuplicateKeyException.class)));
    }
}
