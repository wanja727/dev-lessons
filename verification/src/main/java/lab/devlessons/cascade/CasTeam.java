package lab.devlessons.cascade;

import jakarta.persistence.*;

@Entity
@Table(name = "cas_team")
public class CasTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    protected CasTeam() {
    }

    public CasTeam(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }
}
