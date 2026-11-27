package lab.devlessons.manytomany;

import jakarta.persistence.*;
import java.time.LocalDate;

/** 매핑 테이블을 엔티티로 올린 것. 관계의 속성(역할, 가입일)을 담고, 같은 쌍은 UNIQUE로 막는다 */
@Entity
@Table(name = "mtm_study_member",
        uniqueConstraints = @UniqueConstraint(
                name = "mtm_study_member_pair",
                columnNames = {"member_id", "study_id"}))
public class MtmStudyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id")
    private MtmMember member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_id")
    private MtmStudy study;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private LocalDate joinedAt;

    protected MtmStudyMember() {
    }

    public MtmStudyMember(MtmMember member, MtmStudy study, String role, LocalDate joinedAt) {
        this.member = member;
        this.study = study;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public Long getId() {
        return id;
    }

    public String getRole() {
        return role;
    }
}
