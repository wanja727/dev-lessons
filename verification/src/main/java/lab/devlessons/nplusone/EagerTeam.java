package lab.devlessons.nplusone;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;


/** members를 EAGER로 바꾼 예제. 4장 "EAGER의 함정" 검증용. */
@Entity
@Table(name = "eager_team")
public class EagerTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @OneToMany(mappedBy = "team", fetch = FetchType.EAGER)
    private List<EagerMember> members = new ArrayList<>();

    protected EagerTeam() {
    }

    public EagerTeam(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<EagerMember> getMembers() {
        return members;
    }
}
