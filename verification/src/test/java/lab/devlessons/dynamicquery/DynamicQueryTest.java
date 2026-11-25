package lab.devlessons.dynamicquery;

import static lab.devlessons.dynamicquery.ProductSpecs.categoryIs;
import static lab.devlessons.dynamicquery.ProductSpecs.nameIs;
import static lab.devlessons.dynamicquery.ProductSpecs.priceAtLeast;
import static lab.devlessons.dynamicquery.QDqProduct.dqProduct;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.support.TransactionTemplate;

/** 레슨 "동적 쿼리"의 주장을 검증한다. */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class DynamicQueryTest {

    private static final String LESSON = "dynamic-query";

    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired DqProductRepository products;

    @BeforeEach
    void setUp() {
        products.deleteAll();
        products.save(new DqProduct("펜", 1000, "문구"));
        products.save(new DqProduct("노트", 3000, "문구"));
        products.save(new DqProduct("머그", 12000, "주방"));
        products.save(new DqProduct("텀블러", 25000, "주방"));
        SqlCapture.clear();
    }

    /** 흔한 방법: 조건이 있을 때만 JPQL을 이어 붙인다 */
    private static String concat(String name, Integer minPrice) {
        String jpql = "select p from DqProduct p";
        if (name != null) {
            jpql += " where p.name = :name";
        }
        if (minPrice != null) {
            jpql += " and p.price >= :min";
        }
        return jpql;
    }

    @Test
    @DisplayName("이어 붙인 JPQL에 가격만 넣으면 where 없이 and로 이어져 실행할 때 구문 오류가 난다 (출력 공개)")
    void concatenatedJpqlBreaks() {
        String jpql = concat(null, 10000);

        Throwable thrown = catchThrowable(() -> tx.execute(s -> em.createQuery(jpql, DqProduct.class)
                .setParameter("min", 10000).getResultList()));

        assertThat(jpql).isEqualTo("select p from DqProduct p and p.price >= :min");
        assertThat(thrown).isNotNull();
        CiOutput.record(LESSON, "concat", "만들어진 JPQL: " + jpql + "\n→ " + thrown.getClass().getSimpleName()
                + ": " + String.valueOf(thrown.getMessage()).split("\n")[0]);
    }

    @Test
    @DisplayName("값을 JPQL 문자열에 이으면 따옴표가 든 입력이 조건을 풀어 모든 상품이 나오고, 파라미터로 넘기면 0개다 (출력 공개)")
    void injection() {
        String input = "x' or '1'='1";
        List<DqProduct> concat = tx.execute(s -> em.createQuery(
                "select p from DqProduct p where p.name = '" + input + "'", DqProduct.class).getResultList());
        List<DqProduct> param = tx.execute(s -> em.createQuery(
                "select p from DqProduct p where p.name = :name", DqProduct.class)
                .setParameter("name", input).getResultList());

        assertThat(concat).hasSize(4);
        assertThat(param).isEmpty();
        CiOutput.record(LESSON, "injection", String.join("\n",
                "입력: " + input,
                "문자열에 이었을 때 결과: " + concat.size() + "개",
                "파라미터로 넘겼을 때 결과: " + param.size() + "개"));
    }

    private String whereOf(Specification<DqProduct> spec) {
        SqlCapture.clear();
        products.findAll(spec);
        String sql = SqlCapture.sqls().get(0);
        int at = sql.indexOf(" where ");
        return at < 0 ? "(where 없음)" : sql.substring(at + 1);
    }

    @Test
    @DisplayName("Specification은 null 조건을 건너뛰어, 메서드 하나로 들어온 조건만 WHERE에 넣는다 (출력 공개)")
    void specificationCombinations() {
        List<String> lines = new ArrayList<>();
        lines.add("조건 없음 → " + whereOf(Specification.allOf(nameIs(null), priceAtLeast(null), categoryIs(null))));
        lines.add("이름만 → " + whereOf(Specification.allOf(nameIs("펜"), priceAtLeast(null), categoryIs(null))));
        lines.add("가격만 → " + whereOf(Specification.allOf(nameIs(null), priceAtLeast(10000), categoryIs(null))));
        lines.add("셋 다 → " + whereOf(Specification.allOf(nameIs("머그"), priceAtLeast(10000), categoryIs("주방"))));
        List<DqProduct> priceOnly = products.findAll(
                Specification.allOf(nameIs(null), priceAtLeast(10000), categoryIs(null)));

        assertThat(lines.get(0)).contains("where 없음");
        assertThat(lines.get(2)).contains("price>=").doesNotContain("name");
        assertThat(priceOnly).extracting(DqProduct::getName).containsExactlyInAnyOrder("머그", "텀블러");
        CiOutput.record(LESSON, "spec", String.join("\n", lines));
    }

    private static BooleanExpression nameEq(String name) {
        return name == null ? null : dqProduct.name.eq(name);
    }

    private static BooleanExpression priceGoe(Integer min) {
        return min == null ? null : dqProduct.price.goe(min);
    }

    private static BooleanExpression categoryEq(String category) {
        return category == null ? null : dqProduct.category.eq(category);
    }

    @Test
    @DisplayName("Querydsl의 where는 null 조건을 무시해서 들어온 조건만 WHERE에 들어간다 (출력 공개)")
    void querydslIgnoresNull() {
        List<String> lines = new ArrayList<>();
        List<DqProduct> result = tx.execute(s -> {
            JPAQueryFactory query = new JPAQueryFactory(em);
            SqlCapture.clear();
            List<DqProduct> r = query.selectFrom(dqProduct)
                    .where(nameEq(null), priceGoe(10000), categoryEq(null))
                    .fetch();
            lines.add("where(nameEq(null), priceGoe(10000), categoryEq(null))");
            lines.add("→ " + SqlCapture.sqls().get(0));
            return r;
        });

        assertThat(result).extracting(DqProduct::getName).containsExactlyInAnyOrder("머그", "텀블러");
        assertThat(lines.get(1)).contains("price>=").doesNotContain("name=");
        CiOutput.record(LESSON, "querydsl", String.join("\n", lines));
    }
}
