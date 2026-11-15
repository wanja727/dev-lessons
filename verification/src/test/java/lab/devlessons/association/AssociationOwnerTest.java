package lab.devlessons.association;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import lab.devlessons.support.PostgresContainerConfig;
import lab.devlessons.support.SqlCapture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/** 레슨 "연관관계 주인과 양방향 매핑"의 주장을 검증한다. FK는 DB에서 직접 읽는다. */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class AssociationOwnerTest {

    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx.executeWithoutResult(s -> {
            em.createQuery("delete from AssocMember").executeUpdate();
            em.createQuery("delete from AssocTeam").executeUpdate();
            em.createQuery("delete from UniMember").executeUpdate();
            em.createQuery("delete from UniTeam").executeUpdate();
        });
    }

    /** DB에 실제로 저장된 FK 값 */
    private Long teamIdInDb(Long memberId) {
        return tx.execute(s -> {
            Object v = em.createNativeQuery("select team_id from assoc_member where id = ?1")
                    .setParameter(1, memberId)
                    .getSingleResult();
            return v == null ? null : ((Number) v).longValue();
        });
    }

    @Test
    @DisplayName("주인이 아닌 쪽(team.members)에만 추가하면 FK는 null로 저장된다")
    void nonOwnerSideOnlyIsIgnored() {
        Long memberId = tx.execute(s -> {
            AssocTeam team = new AssocTeam("레드");
            em.persist(team);
            AssocMember member = new AssocMember("kim");
            team.getMembers().add(member);      // 거울에만 추가
            em.persist(member);
            return member.getId();
        });

        assertThat(teamIdInDb(memberId)).isNull();
    }

    @Test
    @DisplayName("주인(member.team)만 설정하면 FK는 저장되지만, 같은 트랜잭션 안의 team.members는 비어 있다")
    void ownerSideOnlySavesButMemoryIsStale() {
        Long[] ids = tx.execute(s -> {
            AssocTeam team = new AssocTeam("레드");
            em.persist(team);
            AssocMember member = new AssocMember("kim");
            member.assignTeamOnly(team);
            em.persist(member);
            em.flush();

            assertThat(team.getMembers()).isEmpty();   // 메모리의 객체 그래프는 안 맞는다
            return new Long[] {team.getId(), member.getId()};
        });

        assertThat(teamIdInDb(ids[1])).isEqualTo(ids[0]);
        tx.executeWithoutResult(s ->
                assertThat(em.find(AssocTeam.class, ids[0]).getMembers()).hasSize(1)); // 새로 읽으면 보인다
    }

    @Test
    @DisplayName("편의 메서드로 양쪽을 맞추면 FK도 저장되고 같은 트랜잭션 안에서도 team.members에 보인다")
    void convenienceMethodKeepsBothSides() {
        Long[] ids = tx.execute(s -> {
            AssocTeam team = new AssocTeam("레드");
            em.persist(team);
            AssocMember member = new AssocMember("kim");
            member.changeTeam(team);
            em.persist(member);

            assertThat(team.getMembers()).containsExactly(member);
            return new Long[] {team.getId(), member.getId()};
        });

        assertThat(teamIdInDb(ids[1])).isEqualTo(ids[0]);
    }

    @Test
    @DisplayName("주인이 아닌 쪽 컬렉션에서 빼도 FK는 그대로다. 관계를 끊으려면 주인 쪽을 바꿔야 한다")
    void removingFromNonOwnerDoesNothing() {
        Long[] ids = tx.execute(s -> {
            AssocTeam team = new AssocTeam("레드");
            em.persist(team);
            AssocMember member = new AssocMember("kim");
            member.changeTeam(team);
            em.persist(member);
            return new Long[] {team.getId(), member.getId()};
        });

        tx.executeWithoutResult(s -> {
            AssocTeam team = em.find(AssocTeam.class, ids[0]);
            team.getMembers().clear();
        });
        assertThat(teamIdInDb(ids[1])).isEqualTo(ids[0]);

        tx.executeWithoutResult(s -> em.find(AssocMember.class, ids[1]).changeTeam(null));
        assertThat(teamIdInDb(ids[1])).isNull();
    }

    @Test
    @DisplayName("일대다 단방향(@OneToMany + @JoinColumn)은 멤버 INSERT 뒤에 FK를 채우는 UPDATE가 멤버 수만큼 더 나간다")
    void unidirectionalOneToManyIssuesExtraUpdates() {
        tx.executeWithoutResult(s -> {
            SqlCapture.clear();
            UniTeam team = new UniTeam("블루");
            team.getMembers().add(new UniMember("a"));
            team.getMembers().add(new UniMember("b"));
            team.getMembers().forEach(em::persist);
            em.persist(team);
            em.flush();

            long inserts = SqlCapture.sqls().stream().filter(q -> q.toLowerCase().startsWith("insert into uni_member")).count();
            long updates = SqlCapture.sqls().stream().filter(q -> q.toLowerCase().startsWith("update uni_member")).count();
            assertThat(inserts).isEqualTo(2);
            assertThat(updates).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("양방향 다대일(주인이 FK를 가진 쪽)은 같은 저장에 UPDATE가 없다")
    void bidirectionalManyToOneNoExtraUpdates() {
        tx.executeWithoutResult(s -> {
            SqlCapture.clear();
            AssocTeam team = new AssocTeam("블루");
            em.persist(team);
            AssocMember a = new AssocMember("a");
            AssocMember b = new AssocMember("b");
            a.changeTeam(team);
            b.changeTeam(team);
            em.persist(a);
            em.persist(b);
            em.flush();

            long updates = SqlCapture.sqls().stream().filter(q -> q.toLowerCase().startsWith("update assoc_member")).count();
            assertThat(updates).isZero();
        });
    }
}
