package lab.devlessons.lazy;

import jakarta.persistence.*;

@Entity
@Table(name = "lazy_team")
public class LazyTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    protected LazyTeam() {
    }

    public LazyTeam(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
