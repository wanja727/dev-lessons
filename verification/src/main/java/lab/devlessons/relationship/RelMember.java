package lab.devlessons.relationship;

import jakarta.persistence.*;

/** 레슨 "테이블 관계 설계"(D17)의 회원. 프로필과 1:1이고, 외래 키는 프로필 쪽에 있다 */
@Entity
@Table(name = "rel_member")
public class RelMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @OneToOne(mappedBy = "member")
    private RelProfile profile;

    protected RelMember() {
    }

    public RelMember(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public RelProfile getProfile() {
        return profile;
    }
}
