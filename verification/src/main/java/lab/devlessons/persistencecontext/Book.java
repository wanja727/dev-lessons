package lab.devlessons.persistencecontext;

import jakarta.persistence.*;

/** SEQUENCE 전략: id를 DB 시퀀스에서 미리 받아 오므로 INSERT를 flush까지 미룰 수 있다. */
@Entity
@Table(name = "book")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "book_seq")
    @SequenceGenerator(name = "book_seq", sequenceName = "book_seq", allocationSize = 50)
    private Long id;

    private String title;

    protected Book() {
    }

    public Book(String title) {
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
