package lab.devlessons.inheritance;

import jakarta.persistence.*;

@Entity
@Table(name = "emb_mutable_member")
public class MutableAddrMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Embedded
    private MutableAddress home;

    protected MutableAddrMember() {
    }

    public MutableAddrMember(String name, MutableAddress home) {
        this.name = name;
        this.home = home;
    }

    public Long getId() {
        return id;
    }

    public MutableAddress getHome() {
        return home;
    }
}
