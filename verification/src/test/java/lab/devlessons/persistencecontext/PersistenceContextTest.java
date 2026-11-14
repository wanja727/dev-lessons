package lab.devlessons.persistencecontext;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.List;
import lab.devlessons.support.PostgresContainerConfig;
import lab.devlessons.support.SqlCapture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/** 레슨 "영속성 컨텍스트"의 주장을 하나의 트랜잭션 안에서 SQL로 검증한다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
class PersistenceContextTest {

    @Autowired TestEntityManager tem;
    EntityManager em;
    Long bookId;

    @BeforeEach
    void setUp() {
        em = tem.getEntityManager();
        bookId = tem.persist(new Book("JPA 입문")).getId();
        tem.persist(new Book("스프링 핵심"));
        tem.flush();
        tem.clear();
        SqlCapture.clear();
    }

    @Test
    @DisplayName("1차 캐시: 같은 id로 두 번 find하면 SELECT는 1번, 같은 객체")
    void findTwice() {
        Book a = em.find(Book.class, bookId);
        Book b = em.find(Book.class, bookId);

        assertThat(selects()).isEqualTo(1);
        assertThat(a).isSameAs(b);
    }

    @Test
    @DisplayName("JPQL은 항상 SQL을 보내지만, 이미 있는 엔티티는 1차 캐시의 객체를 돌려준다")
    void jpqlAlwaysQueries() {
        Book a = em.find(Book.class, bookId);
        Book b = em.createQuery("select b from Book b where b.id = :id", Book.class)
                .setParameter("id", bookId)
                .getSingleResult();

        assertThat(selects()).isEqualTo(2);
        assertThat(a).isSameAs(b);
    }

    @Test
    @DisplayName("clear() 뒤에 find하면 다시 SELECT")
    void clearThenFind() {
        em.find(Book.class, bookId);
        em.clear();
        em.find(Book.class, bookId);

        assertThat(selects()).isEqualTo(2);
    }

    @Test
    @DisplayName("쓰기 지연(SEQUENCE): flush 전에는 INSERT 0번, flush하면 3번")
    void writeBehind() {
        em.persist(new Book("새 책 1"));
        em.persist(new Book("새 책 2"));
        em.persist(new Book("새 책 3"));
        assertThat(inserts()).isZero();

        em.flush();
        assertThat(inserts()).isEqualTo(3);
    }

    @Test
    @DisplayName("IDENTITY: persist 즉시 INSERT (쓰기 지연이 안 된다)")
    void identityInsertsImmediately() {
        em.persist(new IdentityBook("새 책"));

        assertThat(inserts()).isEqualTo(1);
    }

    @Test
    @DisplayName("JPQL 실행 전에 자동으로 flush한다 (INSERT가 SELECT보다 먼저)")
    void autoFlushBeforeJpql() {
        em.persist(new Book("새 책"));
        List<Book> all = em.createQuery("select b from Book b", Book.class).getResultList();

        List<String> sqls = SqlCapture.sqls().stream().map(String::toLowerCase).toList();
        int insertAt = indexOf(sqls, "insert");
        int selectAt = indexOfQuery(sqls);
        assertThat(insertAt).isGreaterThanOrEqualTo(0).isLessThan(selectAt);
        assertThat(all).hasSize(3);
    }

    @Test
    @DisplayName("변경 감지: save() 없이 값만 바꿔도 flush 때 UPDATE 1번")
    void dirtyChecking() {
        Book book = em.find(Book.class, bookId);
        book.changeTitle("JPA 입문 2판");
        em.flush();

        assertThat(updates()).isEqualTo(1);
        em.clear();
        assertThat(em.find(Book.class, bookId).getTitle()).isEqualTo("JPA 입문 2판");
    }

    @Test
    @DisplayName("변경이 없으면 UPDATE도 없다")
    void noChangeNoUpdate() {
        em.find(Book.class, bookId);
        em.flush();

        assertThat(updates()).isZero();
    }

    @Test
    @DisplayName("준영속(detach) 엔티티는 바꿔도 반영되지 않는다")
    void detachedChangeIgnored() {
        Book book = em.find(Book.class, bookId);
        em.detach(book);
        book.changeTitle("반영될까?");
        em.flush();

        assertThat(updates()).isZero();
        em.clear();
        assertThat(em.find(Book.class, bookId).getTitle()).isEqualTo("JPA 입문");
    }

    /** book 테이블을 조회한 SELECT 수 (시퀀스 조회는 제외) */
    private static long selects() {
        return SqlCapture.sqls().stream()
                .map(String::toLowerCase)
                .filter(s -> s.startsWith("select") && s.contains(" from book "))
                .count();
    }

    private static long inserts() {
        return SqlCapture.sqls().stream().filter(s -> s.toLowerCase().startsWith("insert")).count();
    }

    private static long updates() {
        return SqlCapture.sqls().stream().filter(s -> s.toLowerCase().startsWith("update")).count();
    }

    private static int indexOf(List<String> sqls, String prefix) {
        for (int i = 0; i < sqls.size(); i++) {
            if (sqls.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfQuery(List<String> sqls) {
        for (int i = 0; i < sqls.size(); i++) {
            if (sqls.get(i).startsWith("select") && sqls.get(i).contains(" from book ")) {
                return i;
            }
        }
        return -1;
    }
}
