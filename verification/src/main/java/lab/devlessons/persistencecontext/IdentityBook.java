package lab.devlessons.persistencecontext;

import jakarta.persistence.*;

/** IDENTITY 전략: id를 DB가 INSERT 때 만든다. persist 즉시 INSERT가 나간다. */
@Entity
@Table(name = "identity_book")
public class IdentityBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    protected IdentityBook() {
    }

    public IdentityBook(String title) {
        this.title = title;
    }

    public Long getId() {
        return id;
    }
}
