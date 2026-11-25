package lab.devlessons.idgen;

import jakarta.persistence.*;

/** SEQUENCE: 번호를 하나씩 받는다 */
@Entity
@Table(name = "idgen_seq_one_log")
public class SeqOneLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "idgen_one_seq")
    @SequenceGenerator(name = "idgen_one_seq", sequenceName = "idgen_one_seq", allocationSize = 1)
    private Long id;

    private String message;

    protected SeqOneLog() {
    }

    public SeqOneLog(String message) {
        this.message = message;
    }

    public Long getId() {
        return id;
    }
}
