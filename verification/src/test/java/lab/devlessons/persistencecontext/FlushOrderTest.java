package lab.devlessons.persistencecontext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import jakarta.persistence.EntityManager;
import java.util.List;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import lab.devlessons.support.SqlCapture;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/** 레슨 "영속성 컨텍스트": flush는 코드의 순서가 아니라 INSERT, UPDATE, DELETE 순서로 SQL을 보낸다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
class FlushOrderTest {

    private static final String LESSON = "jpa-persistence-context";

    @Autowired TestEntityManager tem;
    EntityManager em;
    Long oldId;
    Long keepId;

    @BeforeEach
    void setUp() {
        em = tem.getEntityManager();
        oldId = tem.persist(new IsbnBook("978-1", "잘못 등록한 책")).getId();
        keepId = tem.persist(new IsbnBook("978-3", "스프링 핵심")).getId();
        tem.flush();
        tem.clear();
        SqlCapture.clear();
    }

    @Test
    @DisplayName("코드는 DELETE, UPDATE, INSERT 순서인데 flush는 INSERT, UPDATE, DELETE 순서로 보낸다")
    void flushOrder() {
        IsbnBook old = em.find(IsbnBook.class, oldId);
        IsbnBook keep = em.find(IsbnBook.class, keepId);
        SqlCapture.clear();

        em.remove(old);
        keep.changeTitle("스프링 핵심 2판");
        em.persist(new IsbnBook("978-2", "새 책"));
        em.flush();

        List<String> kinds = kinds();
        assertThat(kinds).containsExactly("insert", "update", "delete");
        CiOutput.record(LESSON, "flush-order", String.join("\n",
                "코드의 순서: remove, changeTitle, persist",
                "flush가 보낸 SQL:",
                String.join("\n", dml())));
    }

    @Test
    @DisplayName("같은 ISBN의 책을 지우고 다시 넣으면 INSERT가 먼저 나가 23505로 실패한다")
    void deleteThenInsertSameUniqueValue() {
        IsbnBook old = em.find(IsbnBook.class, oldId);
        SqlCapture.clear();

        em.remove(old);
        em.persist(new IsbnBook("978-1", "다시 등록"));
        ConstraintViolationException e = catchThrowableOfType(ConstraintViolationException.class, () -> em.flush());

        assertThat(e).isNotNull();
        assertThat(e.getSQLState()).isEqualTo("23505");
        assertThat(kinds()).containsExactly("insert");
        CiOutput.record(LESSON, "delete-then-insert", String.join("\n",
                "코드의 순서: remove(978-1), persist(978-1)",
                "flush가 보낸 SQL:",
                String.join("\n", dml()),
                "",
                "오류: " + e.getSQLState() + " " + e.getConstraintName(),
                "DELETE는 보내기 전에 멈췄어요"));
    }

    @Test
    @DisplayName("remove 뒤에 flush를 한 번 부르면 DELETE가 먼저 나가 다시 넣을 수 있다")
    void flushAfterRemove() {
        IsbnBook old = em.find(IsbnBook.class, oldId);
        SqlCapture.clear();

        em.remove(old);
        em.flush();
        em.persist(new IsbnBook("978-1", "다시 등록"));
        em.flush();

        assertThat(kinds()).containsExactly("delete", "insert");
        em.clear();
        Long count = em.createQuery("select count(b) from IsbnBook b where b.isbn = '978-1'", Long.class)
                .getSingleResult();
        assertThat(count).isEqualTo(1);
        CiOutput.record(LESSON, "flush-after-remove", String.join("\n",
                "코드의 순서: remove(978-1), flush, persist(978-1), flush",
                "보낸 SQL:",
                String.join("\n", dml()),
                "",
                "ISBN 978-1인 책: " + count + "권"));
    }

    /** isbn_book에 보낸 INSERT, UPDATE, DELETE (시퀀스 조회는 뺀다) */
    private static List<String> dml() {
        return SqlCapture.sqls().stream()
                .filter(s -> {
                    String l = s.toLowerCase();
                    return l.startsWith("insert") || l.startsWith("update") || l.startsWith("delete");
                })
                .toList();
    }

    private static List<String> kinds() {
        return dml().stream().map(s -> s.toLowerCase().split(" ")[0]).toList();
    }
}
