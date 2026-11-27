package lab.devlessons.manytomany;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 회원. @ManyToMany를 List와 Set 두 가지로 둔다(실무 구역의 흔한 실수와 비교용).
 * 연결 엔티티 MtmStudyMember로 푼 모양은 그 클래스에 있다.
 */
@Entity
@Table(name = "mtm_member")
public class MtmMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToMany
    @JoinTable(name = "mtm_member_study_list",
            joinColumns = @JoinColumn(name = "member_id"),
            inverseJoinColumns = @JoinColumn(name = "study_id"))
    private List<MtmStudy> studies = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "mtm_member_study_set",
            joinColumns = @JoinColumn(name = "member_id"),
            inverseJoinColumns = @JoinColumn(name = "study_id"))
    private Set<MtmStudy> studySet = new HashSet<>();

    protected MtmMember() {
    }

    public MtmMember(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public List<MtmStudy> getStudies() {
        return studies;
    }

    public Set<MtmStudy> getStudySet() {
        return studySet;
    }
}
