package lab.devlessons.persistencecontext;

import jakarta.persistence.*;

/** ISBN이 겹치면 안 되는 책. flush가 SQL을 어떤 순서로 보내는지 유니크 제약으로 본다. */
@Entity
@Table(name = "isbn_book")
public class IsbnBook {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "isbn_book_seq")
    @SequenceGenerator(name = "isbn_book_seq", sequenceName = "isbn_book_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, unique = true)
    private String isbn;

    private String title;

    protected IsbnBook() {
    }

    public IsbnBook(String isbn, String title) {
        this.isbn = isbn;
        this.title = title;
    }

    public void changeTitle(String title) {
        this.title = title;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }
}
