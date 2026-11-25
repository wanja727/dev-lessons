package lab.devlessons.bulkupdate;

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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.repository.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 레슨 "벌크 연산과 영속성 컨텍스트"의 주장을 검증한다. 테스트에는 트랜잭션이 없고 TransactionTemplate으로 경계를 정한다. */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class BulkUpdateTest {

    private static final String LESSON = "jpa-bulk-update";

    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired BulkProductRepository products;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from bulk_product");
        List<Object[]> rows = new ArrayList<>();
        for (long i = 1; i <= 1000; i++) {
            rows.add(new Object[] {i, i == 1 ? "펜" : "상품" + i, 1000});
        }
        jdbc.batchUpdate("insert into bulk_product (id, name, price, version) values (?, ?, ?, 0)", rows);
        SqlCapture.clear();
    }

    private int priceInDb(long id) {
        return jdbc.queryForObject("select price from bulk_product where id = ?", Integer.class, id);
    }

    private static long count(String prefix) {
        return SqlCapture.sqls().stream().filter(s -> s.startsWith(prefix)).count();
    }

    @Test
    @DisplayName("1,000개를 변경 감지로 바꾸면 UPDATE 1,000번, 벌크 연산은 UPDATE 1번이고 바뀐 행 수 1,000을 돌려준다 (출력 공개)")
    void dirtyCheckingVsBulk() {
        long start = System.nanoTime();
        tx.executeWithoutResult(s -> products.findAll().forEach(p -> p.changePrice(p.getPrice() * 11 / 10)));
        long dirtyMs = (System.nanoTime() - start) / 1_000_000;
        long dirtyUpdates = count("update");

        SqlCapture.clear();
        start = System.nanoTime();
        int changed = tx.execute(s -> products.raiseAll());
        long bulkMs = (System.nanoTime() - start) / 1_000_000;
        long bulkUpdates = count("update");

        assertThat(dirtyUpdates).isEqualTo(1000);
        assertThat(bulkUpdates).isEqualTo(1);
        assertThat(changed).isEqualTo(1000);
        CiOutput.record(LESSON, "compare", String.join("\n",
                "변경 감지: UPDATE " + dirtyUpdates + "번, " + dirtyMs + "ms",
                "벌크 연산: UPDATE " + bulkUpdates + "번, " + bulkMs + "ms, 돌려받은 값 " + changed,
                "벌크 연산의 SQL: " + SqlCapture.sqls().stream().filter(q -> q.startsWith("update")).findFirst().orElse("")));
    }

    @Test
    @DisplayName("벌크 연산 뒤 이미 조회한 엔티티와 다시 findById한 엔티티, JPQL로 다시 조회한 엔티티는 옛 값이고 DB는 새 값이다 (출력 공개)")
    void staleAfterBulk() {
        List<String> lines = tx.execute(s -> {
            BulkProduct pen = products.findById(1L).orElseThrow();
            products.raiseAll();
            BulkProduct again = products.findById(1L).orElseThrow();
            BulkProduct byJpql = em.createQuery("select p from BulkProduct p where p.id = 1", BulkProduct.class)
                    .getSingleResult();

            assertThat(pen.getPrice()).isEqualTo(1000);
            assertThat(again).isSameAs(pen);
            assertThat(byJpql).isSameAs(pen);
            assertThat(priceInDb(1L)).isEqualTo(1100);
            return List.of(
                    "pen.getPrice(): " + pen.getPrice(),
                    "다시 findById(1L): " + again.getPrice() + " (같은 객체: " + (again == pen) + ")",
                    "JPQL로 다시 조회: " + byJpql.getPrice() + " (같은 객체: " + (byJpql == pen) + ")",
                    "DB의 가격: " + priceInDb(1L));
        });
        CiOutput.record(LESSON, "stale", String.join("\n", lines));
    }

    @Test
    @DisplayName("clearAutomatically = true면 벌크 연산 뒤 findById가 SELECT를 보내 새 값을 읽는다 (출력 공개)")
    void clearAutomatically() {
        List<String> lines = tx.execute(s -> {
            products.findById(1L).orElseThrow();
            products.raiseAllAndClear();
            SqlCapture.clear();
            BulkProduct pen = products.findById(1L).orElseThrow();

            assertThat(count("select")).isEqualTo(1);
            assertThat(pen.getPrice()).isEqualTo(1100);
            return List.of("벌크 연산 뒤 findById(1L): " + pen.getPrice(), "나간 SELECT: " + count("select") + "번");
        });
        CiOutput.record(LESSON, "clear", String.join("\n", lines));
    }

    @Test
    @DisplayName("벌크 연산 전에 바꾼 이름: Hibernate가 벌크 연산 직전에 자동으로 flush하는지 확인한다 (출력 공개)")
    void pendingChangeBeforeBulk() {
        List<String> order = new ArrayList<>();
        tx.executeWithoutResult(s -> {
            BulkProduct pen = products.findById(1L).orElseThrow();
            pen.rename("새 펜");
            SqlCapture.clear();
            products.raiseAllAndClear();
            order.addAll(SqlCapture.sqls());
        });
        String nameInDb = jdbc.queryForObject("select name from bulk_product where id = 1", String.class);

        CiOutput.record(LESSON, "pending", "벌크 연산을 부를 때 나간 SQL:\n" + String.join("\n", order)
                + "\n\n커밋 뒤 DB의 이름: " + nameInDb);
        assertThat(nameInDb).isEqualTo("새 펜");
        assertThat(order.get(0)).startsWith("update bulk_product set name");
    }

    @Test
    @DisplayName("벌크 UPDATE는 @Version을 올리지 않고 @PreUpdate도 부르지 않는다. 변경 감지는 둘 다 한다 (출력 공개)")
    void bulkSkipsVersionAndCallbacks() {
        tx.executeWithoutResult(s -> products.raiseAll());
        Long versionAfterBulk = jdbc.queryForObject("select version from bulk_product where id = 1", Long.class);
        Object updatedAfterBulk = jdbc.queryForObject("select updated_at from bulk_product where id = 1", Object.class);

        tx.executeWithoutResult(s -> products.findById(2L).orElseThrow().changePrice(5000));
        Long versionAfterDirty = jdbc.queryForObject("select version from bulk_product where id = 2", Long.class);
        Object updatedAfterDirty = jdbc.queryForObject("select updated_at from bulk_product where id = 2", Object.class);

        assertThat(versionAfterBulk).isZero();
        assertThat(updatedAfterBulk).isNull();
        assertThat(versionAfterDirty).isEqualTo(1);
        assertThat(updatedAfterDirty).isNotNull();
        CiOutput.record(LESSON, "version", String.join("\n",
                "벌크 연산 뒤 펜: version " + versionAfterBulk + ", updated_at " + updatedAfterBulk,
                "변경 감지 뒤 상품2: version " + versionAfterDirty + ", updated_at " + (updatedAfterDirty != null ? "채워짐" : "null")));
    }

    /** 흔한 실수: @Modifying을 빠뜨린 UPDATE. 스프링이 스캔하지 않게 테스트 안에 중첩해 둔다 */
    interface NoModifyingRepository extends Repository<BulkProduct, Long> {
        @Query("update BulkProduct p set p.price = p.price * 11 / 10")
        int raiseWithoutModifying();
    }

    @Test
    @DisplayName("@Modifying 없이 UPDATE JPQL을 @Query에 쓰면 부를 때 예외가 난다 (출력 공개)")
    void withoutModifyingFails() {
        NoModifyingRepository repo = new JpaRepositoryFactory(em).getRepository(NoModifyingRepository.class);

        Throwable thrown = catchThrowable(() -> tx.executeWithoutResult(s -> repo.raiseWithoutModifying()));

        assertThat(thrown).isNotNull();
        assertThat(priceInDb(1L)).isEqualTo(1000);
        CiOutput.record(LESSON, "no-modifying", thrown.getClass().getSimpleName() + ": "
                + String.valueOf(thrown.getMessage()).split("\n")[0]);
    }
}
