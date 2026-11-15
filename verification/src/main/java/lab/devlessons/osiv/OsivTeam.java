package lab.devlessons.osiv;

import jakarta.persistence.*;

@Entity
@Table(name = "osiv_team")
public class OsivTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    protected OsivTeam() {
    }

    public OsivTeam(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
