package lab.devlessons.lazy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lab.devlessons.support.CiOutput;
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
            em.createQuery("delete from DefaultFetchPlayer").executeUpdate();
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
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> player.getTeam().getName());
        CiOutput.record("proxy-lazy-loading", "lazy-init", String.join("\n",
                "① player.getTeam().getId() → " + player.getTeam().getId(),
                "② player.getTeam().getName() → " + thrown.getClass().getSimpleName(),
                "   " + thrown.getMessage()));
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

    @Test
    @DisplayName("LAZY 연관 자리의 프록시는 엔티티를 상속해 Hibernate가 만든 클래스다 (출력 공개)")
    void proxyClassIsSubclass() {
        tx.executeWithoutResult(s -> {
            SqlCapture.clear();
            LazyPlayer player = em.find(LazyPlayer.class, playerId);
            Object team = player.getTeam();

            assertThat(team.getClass().getSuperclass()).isEqualTo(LazyTeam.class);
            assertThat(team.getClass().getSimpleName()).contains("HibernateProxy");
            CiOutput.record("proxy-lazy-loading", "proxy-class", String.join("\n",
                    "선수 조회 뒤 팀 SELECT 수: " + teamSelects(),
                    "player.getTeam().getClass(): " + team.getClass().getSimpleName(),
                    "그 클래스의 부모: " + team.getClass().getSuperclass().getSimpleName(),
                    "초기화됐나: " + Hibernate.isInitialized(team)));
        });
    }

    @Test
    @DisplayName("getReference 뒤 getId는 SELECT 없음, 처음 getName에서 1번, 다시 읽어도 1번 (출력 공개)")
    void referenceStepByStep() {
        tx.executeWithoutResult(s -> {
            SqlCapture.clear();
            java.util.List<String> lines = new java.util.ArrayList<>();
            LazyTeam ref = em.getReference(LazyTeam.class, teamId);
            lines.add("getReference 뒤 팀 SELECT: " + teamSelects());
            ref.getId();
            lines.add("getId() 뒤 팀 SELECT: " + teamSelects());
            ref.getName();
            lines.add("getName() 뒤 팀 SELECT: " + teamSelects());
            ref.getName();
            lines.add("다시 getName() 뒤 팀 SELECT: " + teamSelects());

            assertThat(teamSelects()).isEqualTo(1);
            CiOutput.record("proxy-lazy-loading", "steps", String.join("\n", lines));
        });
    }

    @Test
    @DisplayName("스트림의 map에서 서로 다른 팀 프록시를 건드리면 팀 수만큼 SELECT가 나간다 (출력 공개)")
    void streamTouchesEachProxy() {
        tx.executeWithoutResult(s -> {
            for (String name : java.util.List.of("블루", "그린")) {
                LazyTeam t = new LazyTeam(name);
                em.persist(t);
                em.persist(new LazyPlayer("p-" + name, t));
            }
            em.flush();
            em.clear();
            SqlCapture.clear();

            java.util.List<LazyPlayer> players = em.createQuery(
                    "select p from LazyPlayer p order by p.id", LazyPlayer.class).getResultList();
            long afterQuery = teamSelects();
            java.util.List<String> names = players.stream().map(p -> p.getTeam().getName()).toList();

            assertThat(players).hasSize(3);
            assertThat(afterQuery).isZero();
            assertThat(teamSelects()).isEqualTo(3);
            CiOutput.record("proxy-lazy-loading", "stream", String.join("\n",
                    "선수 3명 조회 뒤 팀 SELECT: " + afterQuery,
                    "stream().map(p -> p.getTeam().getName()) → " + names,
                    "그 뒤 팀 SELECT: " + teamSelects()));
        });
    }

    record PlayerView(String name, String teamName) {
    }

    @Test
    @DisplayName("트랜잭션 안에서 DTO로 바꿔 돌려주면 트랜잭션 밖에서 예외 없이 쓴다")
    void dtoInsideTransaction() {
        PlayerView view = tx.execute(s -> {
            LazyPlayer p = em.find(LazyPlayer.class, playerId);
            return new PlayerView(p.getName(), p.getTeam().getName());
        });

        assertThat(view.teamName()).isEqualTo("레드");
    }

    @Test
    @DisplayName("fetch를 적지 않은 @ManyToOne(기본 EAGER)은 선수를 조회할 때 팀도 함께 가져온다 (출력 공개)")
    void manyToOneDefaultIsEager() {
        Long id = tx.execute(s -> {
            DefaultFetchPlayer p = new DefaultFetchPlayer("lee", em.find(LazyTeam.class, teamId));
            em.persist(p);
            return p.getId();
        });
        tx.executeWithoutResult(s -> {
            SqlCapture.clear();
            DefaultFetchPlayer p = em.find(DefaultFetchPlayer.class, id);
            String sql = String.join("\n", SqlCapture.sqls());

            assertThat(Hibernate.isInitialized(p.getTeam())).isTrue();
            assertThat(sql).contains("lazy_team");
            CiOutput.record("proxy-lazy-loading", "default-eager", "em.find(DefaultFetchPlayer.class, id)\n→ " + sql);
        });
    }

    @Test
    @DisplayName("getReference로 외래 키만 채워 저장하면 팀 SELECT 없이 INSERT만 나간다 (출력 공개)")
    void referenceForForeignKey() {
        tx.executeWithoutResult(s -> {
            SqlCapture.clear();
            LazyTeam ref = em.getReference(LazyTeam.class, teamId);
            em.persist(new LazyPlayer("park", ref));
            em.flush();

            assertThat(teamSelects()).isZero();
            CiOutput.record("proxy-lazy-loading", "reference-save", "나간 SQL:\n" + String.join("\n", SqlCapture.sqls()));
        });
    }

    /** 흔한 실수: getClass()로 비교하는 equals */
    static boolean equalsByClass(LazyTeam a, Object o) {
        if (o == null || a.getClass() != o.getClass()) {
            return false;
        }
        return a.getId().equals(((LazyTeam) o).getId());
    }

    /** 고친 equals: instanceof와 getId() */
    static boolean equalsByInstanceof(LazyTeam a, Object o) {
        return o instanceof LazyTeam t && a.getId().equals(t.getId());
    }

    @Test
    @DisplayName("getClass()로 비교하면 프록시와 진짜 엔티티가 다르다고 나오고, instanceof와 getId()로 비교하면 같다 (출력 공개)")
    void equalsWithProxy() {
        LazyTeam real = tx.execute(s -> em.find(LazyTeam.class, teamId));
        tx.executeWithoutResult(s -> {
            LazyTeam proxy = em.getReference(LazyTeam.class, teamId);

            assertThat(equalsByClass(real, proxy)).isFalse();
            assertThat(equalsByInstanceof(real, proxy)).isTrue();
            CiOutput.record("proxy-lazy-loading", "equals", String.join("\n",
                    "real.getClass(): " + real.getClass().getSimpleName(),
                    "proxy.getClass(): " + proxy.getClass().getSimpleName(),
                    "getClass()로 비교: " + equalsByClass(real, proxy),
                    "instanceof와 getId()로 비교: " + equalsByInstanceof(real, proxy)));
        });
    }
}
