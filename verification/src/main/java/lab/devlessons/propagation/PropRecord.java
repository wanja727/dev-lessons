package lab.devlessons.propagation;

import jakarta.persistence.*;

/** 전파 레슨용 기록 행. 어느 트랜잭션에서 저장했는지 이름만 남긴다 */
@Entity
@Table(name = "prop_record")
public class PropRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    protected PropRecord() {
    }

    public PropRecord(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
