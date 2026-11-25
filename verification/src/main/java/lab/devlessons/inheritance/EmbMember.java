package lab.devlessons.inheritance;

import jakarta.persistence.*;

@Entity
@Table(name = "emb_member")
public class EmbMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Embedded
    private Address home;

    @Embedded
    @AttributeOverride(name = "city", column = @Column(name = "work_city"))
    @AttributeOverride(name = "street", column = @Column(name = "work_street"))
    @AttributeOverride(name = "zipcode", column = @Column(name = "work_zipcode"))
    private Address work;

    protected EmbMember() {
    }

    public EmbMember(String name, Address home, Address work) {
        this.name = name;
        this.home = home;
        this.work = work;
    }

    /** 값 타입은 바꾸지 않고 새 값으로 갈아 끼운다 */
    public void moveHome(Address home) {
        this.home = home;
    }

    public Long getId() {
        return id;
    }

    public Address getHome() {
        return home;
    }
}
