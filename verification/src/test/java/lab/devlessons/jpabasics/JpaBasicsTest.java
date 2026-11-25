package lab.devlessons.jpabasics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import lab.devlessons.support.SqlCapture;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.EntityManagerFactoryInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** 레슨 "JPA 첫걸음"의 주장을 Hibernate 6.6과 PostgreSQL 16에서 실제로 나간 SQL로 검증한다. */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class JpaBasicsTest {

    @Autowired CafeMenuRepository menus;
    @Autowired EntityManagerFactory emf;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        menus.deleteAll();
        menus.save(new CafeMenu("americano", 4500));
        menus.save(new CafeMenu("latte", 5000));
        menus.save(new CafeMenu("vanilla latte", 5500));
        SqlCapture.clear();
    }

    /** 이 레슨 테이블에 나간 SQL만 */
    private static List<String> sqls() {
        return SqlCapture.sqls().stream().filter(s -> s.contains("cafe_menu")).toList();
    }

    private static String join(List<String> sqls) {
        return sqls.stream().collect(Collectors.joining("\n"));
    }

    @Test
    @DisplayName("엔티티 클래스로 테이블이 만들어진다: 필드 name은 @Column에 적은 menu_name 컬럼이 된다 (출력 공개)")
    void entityBecomesTable() {
        List<String> columns = jdbc.queryForList(
                "select column_name || ' ' || data_type from information_schema.columns "
                        + "where table_name = 'cafe_menu' order by ordinal_position", String.class);

        assertThat(columns).anyMatch(c -> c.startsWith("menu_name"));
        assertThat(columns).noneMatch(c -> c.startsWith("name "));
        assertThat(columns).anyMatch(c -> c.startsWith("sold_out"));
        CiOutput.record("jpa-basics", "table", "테이블 cafe_menu의 컬럼\n" + String.join("\n", columns));
    }

    @Test
    @DisplayName("save()는 INSERT, findById()는 id 조건 SELECT로 나간다 (출력 공개)")
    void saveAndFind() {
        CafeMenu saved = menus.save(new CafeMenu("mocha", 5800));
        List<String> insert = sqls();
        SqlCapture.clear();
        CafeMenu found = menus.findById(saved.getId()).orElseThrow();
        List<String> select = sqls();

        assertThat(insert).anyMatch(s -> s.startsWith("insert into cafe_menu"));
        assertThat(select).hasSize(1).first().asString().startsWith("select").contains("where");
        assertThat(found.getName()).isEqualTo("mocha");
        CiOutput.record("jpa-basics", "save-find", String.join("\n",
                "menus.save(new CafeMenu(\"mocha\", 5800))", "→ " + join(insert), "",
                "menus.findById(" + saved.getId() + ")", "→ " + join(select)));
    }

    @Test
    @DisplayName("메서드 이름 쿼리: findByName, findByPriceLessThan이 WHERE 조건이 된다 (출력 공개)")
    void derivedQueries() {
        menus.findByName("latte");
        String byName = join(sqls());
        SqlCapture.clear();
        List<CafeMenu> cheap = menus.findByPriceLessThan(5000);
        String lessThan = join(sqls());

        assertThat(byName).contains("where").contains("menu_name=");
        assertThat(lessThan).contains("price<");
        assertThat(cheap).extracting(CafeMenu::getName).containsExactly("americano");
        CiOutput.record("jpa-basics", "derived", String.join("\n",
                "findByName(\"latte\")", "→ " + byName, "",
                "findByPriceLessThan(5000)", "→ " + lessThan));
    }

    @Test
    @DisplayName("JPQL은 엔티티 이름과 필드 이름으로 쓰고, Hibernate가 테이블과 컬럼 이름의 SQL로 바꾼다 (출력 공개)")
    void jpqlIsTranslated() {
        List<CafeMenu> result = menus.findExpensive(5000);
        String sql = join(sqls());

        assertThat(result).extracting(CafeMenu::getName).containsExactly("latte", "vanilla latte");
        assertThat(sql).contains("from cafe_menu").contains("order by");
        CiOutput.record("jpa-basics", "jpql", String.join("\n",
                "JPQL: select m from CafeMenu m where m.price >= :min order by m.price",
                "SQL : " + sql));
    }

    @Test
    @DisplayName("리포지토리는 인터페이스만 썼는데 Spring Data JPA가 구현 객체(프록시)를 만들어 빈으로 넣는다")
    void repositoryIsGenerated() {
        assertThat(CafeMenuRepository.class.isInterface()).isTrue();
        assertThat(AopUtils.isAopProxy(menus)).isTrue();
        CiOutput.record("jpa-basics", "proxy", String.join("\n",
                "CafeMenuRepository는 인터페이스인가: " + CafeMenuRepository.class.isInterface(),
                "주입받은 객체의 클래스: " + menus.getClass().getName(),
                "그 클래스가 구현한 것: " + java.util.Arrays.stream(menus.getClass().getInterfaces())
                        .map(Class::getSimpleName).filter(n -> n.equals("CafeMenuRepository")).findFirst().orElse("없음")));
    }

    /** 필드 이름이 틀린 메서드 이름 쿼리. 스프링이 스캔하지 않게 테스트 안에 중첩해 둔다 */
    interface TypoMenuRepository extends org.springframework.data.repository.Repository<CafeMenu, Long> {
        List<CafeMenu> findByNmae(String name);
    }

    @Autowired jakarta.persistence.EntityManager em;

    @Test
    @DisplayName("필드에 없는 이름의 메서드 이름 쿼리는 리포지토리를 만들 때 실패하고, 메시지에 그 이름이 나온다 (출력 공개)")
    void typoInMethodNameFailsAtCreation() {
        var factory = new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em);
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> factory.getRepository(TypoMenuRepository.class));

        Throwable root = thrown;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertThat(thrown).isNotNull();
        assertThat(root.getMessage()).contains("nmae");
        CiOutput.record("jpa-basics", "typo", "List<CafeMenu> findByNmae(String name);\n→ "
                + root.getClass().getSimpleName() + ": " + root.getMessage());
    }

    @Test
    @DisplayName("JPA는 표준(인터페이스)이고 실제로 일하는 구현은 Hibernate다 (출력 공개)")
    void hibernateIsTheImplementation() {
        // 스프링이 JPA 객체를 프록시로 한 번 감싸므로, 스프링이 제공하는 EntityManagerFactoryInfo로 원본을 꺼낸다
        EntityManagerFactoryInfo info = (EntityManagerFactoryInfo) emf;
        String impl = info.getNativeEntityManagerFactory().getClass().getName();
        String provider = info.getPersistenceProvider().getClass().getName();

        assertThat(impl).startsWith("org.hibernate");
        assertThat(info.getPersistenceProvider()).isInstanceOf(org.hibernate.jpa.HibernatePersistenceProvider.class);
        CiOutput.record("jpa-basics", "impl",
                "jakarta.persistence.EntityManagerFactory (JPA 표준 인터페이스)\n→ 실제 구현: " + impl
                        + "\nJPA 구현체(PersistenceProvider): " + provider);
    }
}
