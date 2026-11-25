package lab.devlessons.idgen;

import jakarta.persistence.*;

/** IDENTITY: INSERT할 때 DB가 id를 만든다 */
@Entity
@Table(name = "idgen_identity_log")
public class IdentityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String message;

    protected IdentityLog() {
    }

    public IdentityLog(String message) {
        this.message = message;
    }

    public Long getId() {
        return id;
    }
}
