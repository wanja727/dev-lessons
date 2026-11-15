package lab.devlessons.association;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/** 연관관계의 주인이 아닌 쪽 (mappedBy). members는 읽기용 거울이다 */
@Entity
@Table(name = "assoc_team")
public class AssocTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @OneToMany(mappedBy = "team")
    private List<AssocMember> members = new ArrayList<>();

    protected AssocTeam() {
    }

    public AssocTeam(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<AssocMember> getMembers() {
        return members;
    }
}
