package lab.devlessons.relationship;

import jakarta.persistence.*;

/** 회원 하나에 프로필 하나. 외래 키 member_id를 가진 쪽 */
@Entity
@Table(name = "rel_profile")
public class RelProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private RelMember member;

    private String bio;

    protected RelProfile() {
    }

    public RelProfile(RelMember member, String bio) {
        this.member = member;
        this.bio = bio;
    }

    public Long getId() {
        return id;
    }

    public String getBio() {
        return bio;
    }
}
