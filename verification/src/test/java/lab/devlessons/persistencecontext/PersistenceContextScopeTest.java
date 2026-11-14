package lab.devlessons.persistencecontext;

import static org.assertj.core.api.Assertions.assertThat;

import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 영속성 컨텍스트의 범위는 트랜잭션이다. 테스트 자체에는 트랜잭션이 없다. */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class PersistenceContextScopeTest {

    @Autowired BookService bookService;
    @Autowired BookRepository bookRepository;
    Long bookId;

    @BeforeEach
    void setUp() {
        bookRepository.deleteAll();
        bookId = bookRepository.save(new Book("JPA 입문")).getId();
    }

    @Test
    @DisplayName("트랜잭션 안에서 바꾸면 save() 없이도 커밋 때 반영된다")
    void dirtyCheckingInTransaction() {
        bookService.rename(bookId, "JPA 입문 2판");

        assertThat(bookRepository.findById(bookId).orElseThrow().getTitle()).isEqualTo("JPA 입문 2판");
    }

    @Test
    @DisplayName("readOnly 트랜잭션에서는 바꿔도 반영되지 않는다")
    void readOnlySkipsDirtyChecking() {
        bookService.renameInReadOnly(bookId, "반영될까?");

        assertThat(bookRepository.findById(bookId).orElseThrow().getTitle()).isEqualTo("JPA 입문");
    }

    @Test
    @DisplayName("쓰기 트랜잭션 안에서 readOnly 메서드를 내부 호출해 얻은 엔티티는 영속 상태라 반영된다")
    void internalReadOnlyCallStillManaged() {
        bookService.renameViaFind(bookId, "JPA 입문 2판");

        assertThat(bookRepository.findById(bookId).orElseThrow().getTitle()).isEqualTo("JPA 입문 2판");
    }

    @Test
    @DisplayName("트랜잭션이 끝난 뒤(준영속) 바꾼 값은 반영되지 않는다")
    void changeAfterTransactionIgnored() {
        Book book = bookService.find(bookId);
        book.changeTitle("반영될까?");

        assertThat(bookRepository.findById(bookId).orElseThrow().getTitle()).isEqualTo("JPA 입문");
    }
}
