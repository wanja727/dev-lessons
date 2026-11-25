package lab.devlessons.cascade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
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

/** 레슨 "cascade와 orphanRemoval"의 주장을 검증한다. 테스트에는 트랜잭션이 없고 TransactionTemplate으로 경계를 정한다. */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class CascadeOrphanTest {

    private static final String LESSON = "cascade-orphan";

    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from plain_item");
        jdbc.update("delete from plain_order");
        jdbc.update("delete from cas_item");
        jdbc.update("delete from cas_order");
        jdbc.update("delete from cas_member");
        jdbc.update("delete from cas_team");
    }

    private static List<String> sqls(String table) {
        return SqlCapture.sqls().stream().filter(s -> s.contains(table)).toList();
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    @DisplayName("cascade가 없으면 주문만 저장할 때 주문 상품 INSERT는 0번이고 예외도 없다 (출력 공개)")
    void noCascadeSavesOnlyParent() {
        tx.executeWithoutResult(s -> {
            PlainOrder order = new PlainOrder("A-1");
            order.addItem(new PlainItem("펜", 2));
            order.addItem(new PlainItem("잉크", 1));
            SqlCapture.clear();
            em.persist(order);
            em.flush();
        });
        List<String> sql = SqlCapture.sqls().stream().filter(q -> q.startsWith("insert")).toList();

        assertThat(count("plain_order")).isEqualTo(1);
        assertThat(count("plain_item")).isZero();
        CiOutput.record(LESSON, "no-cascade", "나간 INSERT:\n" + String.join("\n", sql)
                + "\n\nplain_item 행 수: " + count("plain_item"));
    }

    @Test
    @DisplayName("cascade ALL이면 주문을 저장할 때 주문 상품도 INSERT된다 (출력 공개)")
    void cascadePersistSavesChildren() {
        tx.executeWithoutResult(s -> {
            CasOrder order = new CasOrder("A-1");
            order.addItem(new CasItem("펜", 2));
            order.addItem(new CasItem("잉크", 1));
            SqlCapture.clear();
            em.persist(order);
            em.flush();
        });
        List<String> sql = SqlCapture.sqls().stream().filter(q -> q.startsWith("insert")).toList();

        assertThat(count("cas_item")).isEqualTo(2);
        CiOutput.record(LESSON, "cascade-persist", "나간 INSERT:\n" + String.join("\n", sql));
    }

    @Test
    @DisplayName("cascade가 없으면 상품이 남은 주문을 지울 때 외래 키 위반으로 실패한다 (출력 공개)")
    void noCascadeRemoveFails() {
        Long id = tx.execute(s -> {
            PlainOrder order = new PlainOrder("A-1");
            em.persist(order);
            PlainItem a = new PlainItem("펜", 2);
            PlainItem b = new PlainItem("잉크", 1);
            order.addItem(a);
            order.addItem(b);
            em.persist(a);
            em.persist(b);
            return order.getId();
        });

        Throwable thrown = catchThrowable(() -> tx.executeWithoutResult(s ->
                em.remove(em.find(PlainOrder.class, id))));
        Throwable root = thrown;
        while (root.getCause() != null) {
            root = root.getCause();
        }

        assertThat(thrown).isNotNull();
        assertThat(count("plain_order")).isEqualTo(1);
        String message = root.getMessage().split("\n")[0];
        CiOutput.record(LESSON, "no-cascade-remove", thrown.getClass().getSimpleName() + "\n원인: " + message);
    }

    @Test
    @DisplayName("cascade REMOVE면 주문 상품 DELETE가 상품 수만큼 먼저 나가고 주문 DELETE가 뒤에 나간다 (출력 공개)")
    void cascadeRemoveDeletesChildrenFirst() {
        Long id = tx.execute(s -> {
            CasOrder order = new CasOrder("A-1");
            order.addItem(new CasItem("펜", 2));
            order.addItem(new CasItem("잉크", 1));
            em.persist(order);
            return order.getId();
        });
        tx.executeWithoutResult(s -> {
            CasOrder order = em.find(CasOrder.class, id);
            SqlCapture.clear();
            em.remove(order);
            em.flush();
        });
        List<String> deletes = SqlCapture.sqls().stream().filter(q -> q.startsWith("delete")).toList();

        assertThat(deletes).hasSize(3);
        assertThat(deletes.get(0)).contains("cas_item");
        assertThat(deletes.get(2)).contains("cas_order");
        assertThat(count("cas_item")).isZero();
        CiOutput.record(LESSON, "cascade-remove", "나간 DELETE:\n" + String.join("\n", deletes));
    }

    @Test
    @DisplayName("orphanRemoval이 없으면 컬렉션에서 빼도 SQL이 없고, 있으면 뺀 상품의 DELETE가 나간다 (출력 공개)")
    void orphanRemoval() {
        Long plainId = tx.execute(s -> {
            PlainOrder order = new PlainOrder("A-1");
            em.persist(order);
            PlainItem a = new PlainItem("펜", 2);
            order.addItem(a);
            em.persist(a);
            return order.getId();
        });
        Long casId = tx.execute(s -> {
            CasOrder order = new CasOrder("A-2");
            order.addItem(new CasItem("펜", 2));
            order.addItem(new CasItem("잉크", 1));
            em.persist(order);
            return order.getId();
        });

        tx.executeWithoutResult(s -> {
            PlainOrder order = em.find(PlainOrder.class, plainId);
            order.getItems().remove(0);
            SqlCapture.clear();
            em.flush();
        });
        List<String> plainSql = SqlCapture.sqls();
        tx.executeWithoutResult(s -> {
            CasOrder order = em.find(CasOrder.class, casId);
            order.getItems().remove(0);
            SqlCapture.clear();
            em.flush();
        });
        List<String> casSql = SqlCapture.sqls();

        assertThat(plainSql).isEmpty();
        assertThat(count("plain_item")).isEqualTo(1);
        assertThat(casSql).hasSize(1).first().asString().startsWith("delete from cas_item");
        assertThat(count("cas_item")).isEqualTo(1);
        CiOutput.record(LESSON, "orphan", String.join("\n",
                "orphanRemoval 없음: 나간 SQL " + plainSql.size() + "개, plain_item 행 수 " + count("plain_item"),
                "orphanRemoval 있음: " + String.join(", ", casSql) + ", cas_item 행 수 " + count("cas_item")));
    }

    @Test
    @DisplayName("orphanRemoval인 컬렉션을 새 리스트로 통째로 바꾸면 flush할 때 예외가 난다 (출력 공개)")
    void replacingOrphanCollectionFails() {
        Long id = tx.execute(s -> {
            CasOrder order = new CasOrder("A-1");
            order.addItem(new CasItem("펜", 2));
            em.persist(order);
            return order.getId();
        });

        Throwable thrown = catchThrowable(() -> tx.executeWithoutResult(s -> {
            CasOrder order = em.find(CasOrder.class, id);
            order.replaceItems(new ArrayList<>());
            em.flush();
        }));
        Throwable root = thrown;
        while (root.getCause() != null) {
            root = root.getCause();
        }

        assertThat(root.getMessage()).contains("no longer referenced");
        CiOutput.record(LESSON, "replace", root.getClass().getSimpleName() + ": " + root.getMessage());
    }

    @Test
    @DisplayName("@ManyToOne에 cascade ALL을 걸면 회원 하나를 지울 때 팀까지 지우려다 외래 키 위반으로 실패한다 (출력 공개)")
    void cascadeOnManyToOneRemovesParent() {
        Long kimId = tx.execute(s -> {
            CasTeam team = new CasTeam("레드");
            CasMember kim = new CasMember("kim", team);
            em.persist(kim);
            em.persist(new CasMember("lee", team));
            return kim.getId();
        });

        Throwable thrown = catchThrowable(() -> tx.executeWithoutResult(s -> {
            em.remove(em.find(CasMember.class, kimId));
            SqlCapture.clear();
            em.flush();
        }));
        List<String> sql = SqlCapture.sqls().stream().filter(q -> q.startsWith("delete")).toList();

        assertThat(thrown).isNotNull();
        assertThat(sql).anyMatch(q -> q.contains("cas_team"));
        assertThat(count("cas_member")).isEqualTo(2);
        CiOutput.record(LESSON, "many-to-one", "kim을 지울 때 나간 DELETE:\n" + String.join("\n", sql)
                + "\n→ " + thrown.getClass().getSimpleName() + " (lee가 아직 팀을 가리킨다)");
    }
}
