package lab.devlessons.nplusone;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;

/** members에 배치 페치(크기 5)를 적용한 예제. */
@Entity
@Table(name = "batch_team")
public class BatchTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @BatchSize(size = 5)
    @OneToMany(mappedBy = "team")
    private List<BatchMember> members = new ArrayList<>();

    protected BatchTeam() {
    }

    public BatchTeam(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<BatchMember> getMembers() {
        return members;
    }
}
