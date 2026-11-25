package lab.devlessons.inheritance;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.List;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import lab.devlessons.support.SqlCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 레슨 "상속 매핑과 값 타입"의 주장을 검증한다. 테이블 모양은 information_schema에서 읽는다. */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class InheritanceEmbeddedTest {

    private static final String LESSON = "inheritance-embedded";

    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    private String tables(String prefix) {
        List<String> names = jdbc.queryForList(
                "select table_name from information_schema.tables where table_name like ? order by table_name",
                String.class, prefix + "%");
        StringBuilder sb = new StringBuilder();
        for (String t : names) {
            if (t.endsWith("_seq")) {
                continue;
            }
            List<String> cols = jdbc.queryForList(
                    "select column_name from information_schema.columns where table_name = ? order by ordinal_position",
                    String.class, t);
            sb.append(t).append(": ").append(String.join(", ", cols)).append('\n');
        }
        return sb.toString().trim();
    }

    @Test
    @DisplayName("세 상속 전략이 만드는 테이블: SINGLE_TABLE은 하나(dtype), JOINED는 셋, TABLE_PER_CLASS는 자식마다 (출력 공개)")
    void tablesPerStrategy() {
        String single = tables("st_payment");
        String joined = tables("jo_");
        String perClass = tables("tp_");

        assertThat(single).contains("st_payment").contains("dtype").contains("card_number").contains("bank_code");
        assertThat(joined).contains("jo_payment").contains("jo_card").contains("jo_bank");
        assertThat(perClass).contains("tp_card").contains("tp_bank");
        CiOutput.record(LESSON, "tables", String.join("\n",
                "SINGLE_TABLE", single, "", "JOINED", joined, "", "TABLE_PER_CLASS", perClass));
    }

    @Test
    @DisplayName("카드 결제 하나를 저장하면 SINGLE_TABLE은 INSERT 1번, JOINED는 2번이다. SINGLE_TABLE의 카드 행은 bank_code가 NULL이다 (출력 공개)")
    void insertsPerStrategy() {
        tx.executeWithoutResult(s -> {
            SqlCapture.clear();
            em.persist(new StCard(10000, "1234"));
            em.flush();
        });
        List<String> single = SqlCapture.sqls().stream().filter(q -> q.startsWith("insert")).toList();
        tx.executeWithoutResult(s -> {
            SqlCapture.clear();
            em.persist(new JoCard(10000, "1234"));
            em.flush();
        });
        List<String> joined = SqlCapture.sqls().stream().filter(q -> q.startsWith("insert")).toList();
        Object bankCode = jdbc.queryForList("select bank_code from st_payment where dtype = 'StCard'").get(0).get("bank_code");

        assertThat(single).hasSize(1);
        assertThat(joined).hasSize(2);
        assertThat(bankCode).isNull();
        CiOutput.record(LESSON, "inserts", String.join("\n",
                "SINGLE_TABLE:", String.join("\n", single), "",
                "JOINED:", String.join("\n", joined), "",
                "SINGLE_TABLE 카드 행의 bank_code: " + bankCode));
    }

    @Test
    @DisplayName("부모 타입 JPQL: SINGLE_TABLE은 테이블 하나, JOINED는 조인, TABLE_PER_CLASS는 UNION (출력 공개)")
    void polymorphicQuery() {
        String single = queryOf("select p from StPayment p");
        String joined = queryOf("select p from JoPayment p");
        String perClass = queryOf("select p from TpPayment p");

        assertThat(single).doesNotContain("join").doesNotContain("union");
        assertThat(joined).contains("join");
        assertThat(perClass).contains("union");
        CiOutput.record(LESSON, "query", String.join("\n",
                "SINGLE_TABLE: " + single, "", "JOINED: " + joined, "", "TABLE_PER_CLASS: " + perClass));
    }

    private String queryOf(String jpql) {
        return tx.execute(s -> {
            SqlCapture.clear();
            em.createQuery(jpql).getResultList();
            return SqlCapture.sqls().get(0);
        });
    }

    @Test
    @DisplayName("@Embedded 값 타입의 컬럼은 엔티티의 테이블에 들어가고, 두 번 쓰면 @AttributeOverride로 이름을 바꾼다. record 값 타입이 저장되고 읽힌다 (출력 공개)")
    void embeddedColumnsAndRecord() {
        Long id = tx.execute(s -> {
            EmbMember kim = new EmbMember("kim",
                    new Address("서울", "세종대로 1", "04524"), new Address("판교", "판교역로 2", "13529"));
            em.persist(kim);
            return kim.getId();
        });
        Address home = tx.execute(s -> em.find(EmbMember.class, id).getHome());

        assertThat(home).isEqualTo(new Address("서울", "세종대로 1", "04524"));
        String cols = tables("emb_member");
        assertThat(cols).contains("city").contains("work_city");
        CiOutput.record(LESSON, "embedded", cols + "\n\n다시 읽은 home: " + home);
    }

    @Test
    @DisplayName("바꿀 수 있는 값 객체를 두 회원이 함께 가리키면 한쪽만 바꿔도 UPDATE가 2번이고 둘 다 바뀐다 (출력 공개)")
    void sharedMutableValueChangesBoth() {
        Long[] ids = tx.execute(s -> {
            MutableAddress addr = new MutableAddress("서울", "세종대로 1");
            MutableAddrMember kim = new MutableAddrMember("kim", addr);
            MutableAddrMember lee = new MutableAddrMember("lee", addr);
            em.persist(kim);
            em.persist(lee);
            em.flush();
            SqlCapture.clear();
            kim.getHome().setCity("부산");
            return new Long[] {kim.getId(), lee.getId()};
        });
        List<String> updates = SqlCapture.sqls().stream().filter(q -> q.startsWith("update")).toList();
        String leeCity = jdbc.queryForObject("select city from emb_mutable_member where id = ?", String.class, ids[1]);

        assertThat(updates).hasSize(2);
        assertThat(leeCity).isEqualTo("부산");
        CiOutput.record(LESSON, "shared", "kim.getHome().setCity(\"부산\") 뒤 커밋\n나간 UPDATE:\n"
                + String.join("\n", updates) + "\n\nDB에서 읽은 lee의 도시: " + leeCity);
    }
}
