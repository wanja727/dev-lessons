package lab.devlessons.nosql;

import static lab.devlessons.support.Jdbc.error;
import static lab.devlessons.support.Jdbc.exec;
import static lab.devlessons.support.Jdbc.number;
import static lab.devlessons.support.Jdbc.rows;
import static lab.devlessons.support.Jdbc.update;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
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

/**
 * 레슨 "관계형 DB와 NoSQL"(D12)의 주장을 PostgreSQL 16에서 검증한다.
 * 같은 주문 1만 건을 테이블 넷(관계형)과 jsonb 문서(문서 모델 흉내) 두 모양으로 담는다. 회원 kim(id 7)의 주문은 300건이다.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class RdbVsNosqlTest {

    private static final String LESSON = "rdb-vs-nosql";

    @Autowired DataSource ds;

    @BeforeEach
    void setUp() {
        exec(ds, "drop table if exists ns_order_item, ns_orders, ns_member, ns_order_doc cascade",
                "create table ns_member (id int primary key, name text not null)",
                "create table ns_orders (id int primary key, member_id int not null references ns_member(id))",
                "create table ns_order_item (order_id int references ns_orders(id), product text not null, "
                        + "qty int not null check (qty > 0))",
                "insert into ns_member select g, case when g = 7 then 'kim' else 'm' || g end from generate_series(1, 100) g",
                // 주문 1만 건: 1~300은 kim(7), 나머지는 다른 회원
                "insert into ns_orders select g, case when g <= 300 then 7 else 1 + g % 100 end from generate_series(1, 10000) g",
                "update ns_orders set member_id = 8 where member_id = 7 and id > 300",
                "insert into ns_order_item select g, 'pen', 2 from generate_series(1, 10000) g",
                "create table ns_order_doc (id bigint primary key, body jsonb not null)",
                "insert into ns_order_doc select o.id, jsonb_build_object('orderId', o.id, "
                        + "'member', jsonb_build_object('id', m.id, 'name', m.name), "
                        + "'items', jsonb_build_array(jsonb_build_object('product', i.product, 'qty', i.qty))) "
                        + "from ns_orders o join ns_member m on m.id = o.member_id join ns_order_item i on i.order_id = o.id");
    }

    @Test
    @DisplayName("문서 하나로 읽은 주문과 테이블 셋을 조인한 주문이 같은 내용이다")
    void sameOrder() {
        List<String> doc = rows(ds, "select body->>'orderId', body->'member'->>'name', body->'items'->0->>'product', "
                + "body->'items'->0->>'qty' from ns_order_doc where id = 1");
        List<String> rel = rows(ds, "select o.id, m.name, i.product, i.qty from ns_orders o join ns_member m on m.id = o.member_id "
                + "join ns_order_item i on i.order_id = o.id where o.id = 1");
        String body = rows(ds, "select body from ns_order_doc where id = 1").get(0);

        assertThat(doc).isEqualTo(rel);
        CiOutput.record(LESSON, "same", "[문서 한 행]\n" + body + "\n\n[조인한 결과] (id | name | product | qty)\n" + rel.get(0));
    }

    @Test
    @DisplayName("kim의 이름을 바꿀 때 문서 모델은 kim의 주문 문서 300개, 관계형은 회원 1행을 고친다")
    void renameCost() throws SQLException {
        int docs;
        int members;
        try (Connection c = ds.getConnection()) {
            docs = update(c, "update ns_order_doc set body = jsonb_set(body, '{member,name}', '\"kim2\"') "
                    + "where body->'member'->>'id' = '7'");
            members = update(c, "update ns_member set name = 'kim2' where id = 7");
        }
        assertThat(docs).isEqualTo(300);
        assertThat(members).isEqualTo(1);
        CiOutput.record(LESSON, "rename", "kim의 이름을 kim2로 바꿀 때 고친 행\n[문서 모델] 주문 문서 " + docs + "개\n[관계형] member " + members + "행");
    }

    @Test
    @DisplayName("문서 일부만 고치면 같은 회원의 이름이 둘이 된다")
    void partialRename() {
        exec(ds, "update ns_order_doc set body = jsonb_set(body, '{member,name}', '\"kim2\"') "
                + "where body->'member'->>'id' = '7' and id <= 100");
        List<String> names = rows(ds, "select distinct body->'member'->>'name' from ns_order_doc "
                + "where body->'member'->>'id' = '7' order by 1");
        assertThat(names).containsExactly("kim", "kim2");
        CiOutput.record(LESSON, "partial", "300개 가운데 100개만 고친 뒤 회원 7의 이름: " + names);
    }

    @Test
    @DisplayName("jsonb에는 수량을 문자열 \"two\"로 넣어도 들어가고, qty int check (qty > 0) 열은 막는다")
    void schemaCheck() {
        String doc = error(ds, "insert into ns_order_doc values (20001, "
                + "'{\"orderId\": 20001, \"member\": {\"id\": 1}, \"items\": [{\"product\": \"pen\", \"qty\": \"two\"}]}')");
        String rel = error(ds, "insert into ns_order_item values (1, 'pen', 'two')");
        String relZero = error(ds, "insert into ns_order_item values (1, 'pen', 0)");

        assertThat(doc).isEqualTo("ok");
        assertThat(rel).startsWith("22P02");
        assertThat(relZero).startsWith("23514");
        CiOutput.record(LESSON, "schema", String.join("\n",
                "[jsonb 문서에 qty \"two\"] " + doc,
                "[qty int 열에 'two'] " + rel,
                "[qty int check (qty > 0) 열에 0] " + relZero));
    }

    @Test
    @DisplayName("예제 데이터: 주문 문서 1만 개 가운데 kim의 문서는 300개다")
    void countByMember() {
        long before = number(ds, "select count(*) from ns_order_doc where body->'member'->>'name' = 'kim'");
        assertThat(before).isEqualTo(300);
    }
}
