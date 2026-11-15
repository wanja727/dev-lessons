package lab.devlessons.lazy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lab.devlessons.support.PostgresContainerConfig;
import lab.devlessons.support.SqlCapture;
import org.hibernate.Hibernate;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/** 레슨 "프록시와 지연 로딩"의 주장을 검증한다. 트랜잭션 범위는 TransactionTemplate으로 직접 정한다. */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class ProxyLazyLoadingTest {

    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;

    Long teamId;
    Long playerId;

    @BeforeEach
    void setUp() {
        tx.executeWithoutResult(s -> {
            em.createQuery("delete from LazyPlayer").executeUpdate();
            em.createQuery("delete from LazyTeam").executeUpdate();
            LazyTeam team = new LazyTeam("레드");
            em.persist(team);
            LazyPlayer player = new LazyPlayer("kim", team);
            em.persist(player);
            teamId = team.getId();
            playerId = player.getId();
        });
    }

    private static long teamSelects() {
        return SqlCapture.sqls().stream()
                .filter(sql -> sql.toLowerCase().startsWith("select") && sql.contains("lazy_team"))
                .count();
    }

    @Test
    @DisplayName("find는 바로 SELECT하고 진짜 엔티티를 돌려준다")
    void findSelectsImmediately() {
        tx.executeWithoutResult(s -> {
            SqlCapture.clear();
            LazyTeam team = em.find(LazyTeam.class, teamId);

            assertThat(teamSelects()).isEqualTo(1);
            assertThat(team.getClass()).isEqualTo(LazyTeam.class);
        });
    }

    @Test
    @DisplayName("getReference는 SELECT 없이 프록시를 돌려주고, id가 아닌 값을 처음 읽을 때 한 번 SELECT한다")
    void getReferenceDefersSelect() {
        tx.executeWithoutResult(s -> {
            SqlCapture.clear();
            LazyTeam ref = em.getReference(LazyTeam.class, teamId);

            assertThat(teamSelects()).isZero();
            assertThat(Hibernate.isInitialized(ref)).isFalse();
            assertThat(ref.getClass()).isNotEqualTo(LazyTeam.class);
            assertThat(ref).isInstanceOf(LazyTeam.class);

            assertThat(ref.getId()).isEqualTo(teamId);   // 식별자는 프록시가 이미 알고 있다
            assertThat(teamSelects()).isZero();

            assertThat(ref.getName()).isEqualTo("레드"); // 초기화
            assertThat(teamSelects()).isEqualTo(1);
            assertThat(Hibernate.isInitialized(ref)).isTrue();

            ref.getName();
            assertThat(teamSelects()).isEqualTo(1);       // 두 번째부터는 SELECT 없음
        });
    }

    @Test
    @DisplayName("LAZY 연관관계를 가진 엔티티를 조회하면 연관 필드에는 초기화되지 않은 프록시가 들어간다")
    void lazyAssociationIsProxy() {
        tx.executeWithoutResult(s -> {
            SqlCapture.clear();
            LazyPlayer player = em.find(LazyPlayer.class, playerId);

            assertThat(teamSelects()).isZero();
            assertThat(Hibernate.isInitialized(player.getTeam())).isFalse();

            player.getTeam().getName();
            assertThat(teamSelects()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("이미 영속성 컨텍스트에 있으면 getReference도 진짜 엔티티를 돌려준다 (같은 id는 같은 인스턴스)")
    void referenceAfterFindIsRealEntity() {
        tx.executeWithoutResult(s -> {
            LazyTeam found = em.find(LazyTeam.class, teamId);
            LazyTeam ref = em.getReference(LazyTeam.class, teamId);

            assertThat(ref).isSameAs(found);
            assertThat(ref.getClass()).isEqualTo(LazyTeam.class);
        });
    }

    @Test
    @DisplayName("프록시를 먼저 받았으면 find도 같은 프록시를 돌려준다")
    void findAfterReferenceIsSameProxy() {
        tx.executeWithoutResult(s -> {
            LazyTeam ref = em.getReference(LazyTeam.class, teamId);
            LazyTeam found = em.find(LazyTeam.class, teamId);

            assertThat(found).isSameAs(ref);
            assertThat(found.getClass()).isNotEqualTo(LazyTeam.class);
            assertThat(Hibernate.isInitialized(found)).isTrue();
        });
    }

    @Test
    @DisplayName("트랜잭션이 끝난 뒤 초기화되지 않은 프록시를 읽으면 LazyInitializationException (id는 괜찮다)")
    void lazyInitializationAfterTransaction() {
        LazyPlayer player = tx.execute(s -> em.find(LazyPlayer.class, playerId));

        assertThat(player.getTeam().getId()).isEqualTo(teamId);
        assertThatThrownBy(() -> player.getTeam().getName())
                .isInstanceOf(LazyInitializationException.class);
    }

    @Test
    @DisplayName("해결: 트랜잭션 안에서 fetch join으로 함께 조회하면 트랜잭션 밖에서도 읽을 수 있다")
    void fetchJoinInitializes() {
        LazyPlayer player = tx.execute(s -> em.createQuery(
                        "select p from LazyPlayer p join fetch p.team where p.id = :id", LazyPlayer.class)
                .setParameter("id", playerId)
                .getSingleResult());

        assertThat(Hibernate.isInitialized(player.getTeam())).isTrue();
        assertThat(player.getTeam().getName()).isEqualTo("레드");
    }

    @Test
    @DisplayName("해결: 트랜잭션 안에서 Hibernate.initialize로 미리 초기화해도 된다")
    void explicitInitialize() {
        LazyPlayer player = tx.execute(s -> {
            LazyPlayer p = em.find(LazyPlayer.class, playerId);
            Hibernate.initialize(p.getTeam());
            return p;
        });

        assertThat(player.getTeam().getName()).isEqualTo("레드");
    }

    @Test
    @DisplayName("없는 id로 getReference를 하면 바로는 예외가 없고, 초기화할 때 EntityNotFoundException")
    void referenceToMissingRow() {
        tx.executeWithoutResult(s -> {
            LazyTeam ref = em.getReference(LazyTeam.class, -1L);

            assertThatThrownBy(ref::getName).isInstanceOf(EntityNotFoundException.class);
        });
    }
}
