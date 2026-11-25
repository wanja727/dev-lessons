package lab.devlessons.springtest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 레슨 "스프링 테스트"(T3)의 회원. 팀은 지연 로딩, 이메일은 유니크, id는 시퀀스 */
@Entity
@Table(name = "st_member")
public class StMember {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    private StTeam team;

    protected StMember() {
    }

    public StMember(String email, StTeam team) {
        this.email = email;
        this.team = team;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public StTeam getTeam() {
        return team;
    }
}
