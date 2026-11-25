package lab.devlessons.normalization;

import static lab.devlessons.support.Jdbc.error;
import static lab.devlessons.support.Jdbc.exec;
import static lab.devlessons.support.Jdbc.number;
import static lab.devlessons.support.Jdbc.rows;
import static org.assertj.core.api.Assertions.assertThat;

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
 * 레슨 "정규화"(D4)의 주장을 PostgreSQL 16에서 검증한다.
 * 한 테이블에 다 담은 주문표(nm_order_sheet)와, 회원, 상품, 주문, 주문 항목으로 나눈 테이블을 비교한다.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class NormalizationTest {

    private static final String LESSON = "normalization";

    @Autowired DataSource ds;

    @BeforeEach
    void setUp() {
        exec(ds,
                "drop table if exists nm_order_sheet, nm_order_item, nm_orders, nm_product, nm_member, nm_post, nm_post_tag, nm_comment cascade",
                """
                create table nm_order_sheet (
                  order_id int, member_email text, member_name text,
                  product_name text, product_price int, qty int,
                  primary key (order_id, product_name))""",
                "insert into nm_order_sheet values "
                        + "(1, 'kim@a.com', 'kim', 'pen', 1000, 2), (1, 'kim@a.com', 'kim', 'note', 3000, 1), "
                        + "(2, 'kim@a.com', 'kim', 'pen', 1000, 5), (3, 'kim@a.com', 'kim', 'ink', 5000, 1), "
                        + "(4, 'lee@a.com', 'lee', 'pen', 1000, 1), (5, 'lee@a.com', 'lee', 'eraser', 500, 3)",
                "create table nm_member (id int primary key, email text not null unique, name text not null)",
                "create table nm_product (id int primary key, name text not null, price int not null)",
                "create table nm_orders (id int primary key, member_id int not null references nm_member(id))",
                "create table nm_order_item (order_id int references nm_orders(id), product_id int references nm_product(id), "
                        + "qty int not null, unit_price int not null, primary key (order_id, product_id))",
                "insert into nm_member values (1, 'kim@a.com', 'kim'), (2, 'lee@a.com', 'lee')",
                "insert into nm_product values (1, 'pen', 1000), (2, 'note', 3000), (3, 'ink', 5000), (4, 'eraser', 500)",
                "insert into nm_orders values (1, 1), (2, 1), (3, 1), (4, 2), (5, 2)",
                "insert into nm_order_item values (1, 1, 2, 1000), (1, 2, 1, 3000), (2, 1, 5, 1000), (3, 3, 1, 5000), "
                        + "(4, 1, 1, 1000), (5, 4, 3, 500)");
    }

    @Test
    @DisplayName("갱신 이상: 주문표에서 주문 1의 행만 고치면 kim의 이름이 2가지가 된다. 나눈 테이블은 1행을 고쳐 1가지다")
    void updateAnomaly() {
        exec(ds, "update nm_order_sheet set member_name = '김철수' where order_id = 1");
        List<String> sheetNames = rows(ds, "select distinct member_name from nm_order_sheet where member_email = 'kim@a.com' order by 1");

        exec(ds, "update nm_member set name = '김철수' where email = 'kim@a.com'");
        List<String> splitNames = rows(ds, "select distinct m.name from nm_orders o join nm_member m on m.id = o.member_id where m.email = 'kim@a.com'");

        assertThat(sheetNames).containsExactlyInAnyOrder("kim", "김철수");
        assertThat(splitNames).containsExactly("김철수");
        CiOutput.record(LESSON, "update", "[한 테이블] kim@a.com의 이름: " + sheetNames
                + "\n[나눈 테이블] kim@a.com의 이름: " + splitNames);
    }

    @Test
    @DisplayName("삽입 이상: 주문이 없는 상품은 주문표에 넣을 수 없다(기본 키의 order_id가 NULL, 23502)")
    void insertAnomaly() {
        String result = error(ds, "insert into nm_order_sheet(product_name, product_price) values ('stapler', 4000)");
        assertThat(result).startsWith("23502");
        CiOutput.record(LESSON, "insert", "주문 없이 상품 stapler 넣기:\n" + result
                + "\n\n나눈 테이블: " + error(ds, "insert into nm_product values (5, 'stapler', 4000)"));
    }

    @Test
    @DisplayName("삭제 이상: ink를 산 유일한 주문을 지우면 ink의 가격도 사라진다. 나눈 테이블은 상품이 남는다")
    void deleteAnomaly() {
        exec(ds, "delete from nm_order_sheet where order_id = 3");
        List<String> sheet = rows(ds, "select product_price from nm_order_sheet where product_name = 'ink'");
        exec(ds, "delete from nm_order_item where order_id = 3", "delete from nm_orders where id = 3");
        List<String> split = rows(ds, "select price from nm_product where name = 'ink'");

        assertThat(sheet).isEmpty();
        assertThat(split).containsExactly("5000");
        CiOutput.record(LESSON, "delete", "주문 3(ink를 산 유일한 주문)을 지운 뒤 ink의 가격\n[한 테이블] " + sheet
                + "\n[나눈 테이블] " + split);
    }

    @Test
    @DisplayName("제1정규형: 쉼표로 이은 태그에서 like '%java%'는 javascript 글도 찾고, 태그마다 한 행이면 정확하다")
    void firstNormalForm() {
        exec(ds, "create table nm_post (id int primary key, title text, tags text)",
                "insert into nm_post values (1, 'JPA 입문', 'java,spring'), (2, 'React 훅', 'javascript,react'), (3, '자바 21', 'java')",
                "create table nm_post_tag (post_id int references nm_post(id), tag text, primary key (post_id, tag))",
                "insert into nm_post_tag values (1, 'java'), (1, 'spring'), (2, 'javascript'), (2, 'react'), (3, 'java')");
        List<String> like = rows(ds, "select id, title from nm_post where tags like '%java%' order by id");
        List<String> exact = rows(ds, "select p.id, p.title from nm_post p join nm_post_tag t on t.post_id = p.id where t.tag = 'java' order by p.id");

        assertThat(like).containsExactly("1 | JPA 입문", "2 | React 훅", "3 | 자바 21");
        assertThat(exact).containsExactly("1 | JPA 입문", "3 | 자바 21");
        CiOutput.record(LESSON, "tags", "[tags like '%java%']\n" + String.join("\n", like)
                + "\n\n[post_tag의 tag = 'java']\n" + String.join("\n", exact));
    }

    @Test
    @DisplayName("상품 가격을 바꾸면 현재 가격으로 계산한 지난 주문 금액은 바뀌고, unit_price로 계산한 금액은 그대로다")
    void unitPriceIsDifferentFact() {
        String sqlCurrent = "select sum(i.qty * p.price) from nm_order_item i join nm_product p on p.id = i.product_id where i.order_id = 1";
        String sqlUnit = "select sum(i.qty * i.unit_price) from nm_order_item i where i.order_id = 1";
        long beforeCurrent = number(ds, sqlCurrent);
        long beforeUnit = number(ds, sqlUnit);
        exec(ds, "update nm_product set price = 1200 where name = 'pen'");
        long afterCurrent = number(ds, sqlCurrent);
        long afterUnit = number(ds, sqlUnit);

        assertThat(beforeCurrent).isEqualTo(5000);
        assertThat(afterCurrent).isEqualTo(5400);
        assertThat(afterUnit).isEqualTo(beforeUnit).isEqualTo(5000);
        CiOutput.record(LESSON, "unit-price", "주문 1(pen 2개, note 1개)의 금액. pen 가격을 1000 → 1200으로 바꾼 뒤\n"
                + "[product.price로 계산] " + beforeCurrent + " → " + afterCurrent
                + "\n[order_item.unit_price로 계산] " + beforeUnit + " → " + afterUnit);
    }

    @Test
    @DisplayName("반정규화한 comment_count는 수를 올리는 것을 빠뜨린 경로가 있으면 실제 댓글 수와 어긋난다")
    void denormalizedCountDrifts() {
        exec(ds, "create table nm_post (id int primary key, title text, tags text, comment_count int not null default 0)",
                "insert into nm_post(id, title) values (1, '첫 글')",
                "create table nm_comment (id int primary key, post_id int references nm_post(id), body text)");
        // 서비스의 댓글 달기: 둘 다 한다
        for (int i = 1; i <= 3; i++) {
            exec(ds, "insert into nm_comment values (" + i + ", 1, '좋아요')", "update nm_post set comment_count = comment_count + 1 where id = 1");
        }
        // 관리자 기능의 일괄 등록: INSERT만 한다
        exec(ds, "insert into nm_comment values (4, 1, '공지'), (5, 1, '공지')");
        long stored = number(ds, "select comment_count from nm_post where id = 1");
        long actual = number(ds, "select count(*) from nm_comment where post_id = 1");

        assertThat(stored).isEqualTo(3);
        assertThat(actual).isEqualTo(5);
        CiOutput.record(LESSON, "count-drift", "post.comment_count: " + stored + "\n실제 댓글 수 count(*): " + actual);
    }
}
