package lab.devlessons.idgen;

import jakarta.persistence.*;

/** SEQUENCE: 번호를 50개씩 예약한다 */
@Entity
@Table(name = "idgen_seq_log")
public class SeqLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "idgen_seq")
    @SequenceGenerator(name = "idgen_seq", sequenceName = "idgen_seq", allocationSize = 50)
    private Long id;

    private String message;

    protected SeqLog() {
    }

    public SeqLog(String message) {
        this.message = message;
    }

    public Long getId() {
        return id;
    }
}
