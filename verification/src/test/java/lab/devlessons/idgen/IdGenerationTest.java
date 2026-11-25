package lab.devlessons.idgen;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import lab.devlessons.support.SqlCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/** 레슨 "식별자 생성 전략"의 주장 가운데 배치를 켜지 않아도 되는 것을 검증한다. */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class IdGenerationTest {

    static final String LESSON = "id-generation";

    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;

    private static long inserts() {
        return SqlCapture.sqls().stream().filter(s -> s.startsWith("insert")).count();
    }

    private static long nextvals() {
        return SqlCapture.sqls().stream().filter(s -> s.contains("nextval")).count();
    }

    @Test
    @DisplayName("IDENTITY는 persist마다 바로 INSERT하고, SEQUENCE는 flush 전 INSERT가 0번이다 (출력 공개)")
    void whenIdIsKnown() {
        List<String> lines = new ArrayList<>();
        tx.executeWithoutResult(s -> {
            SqlCapture.clear();
            em.persist(new IdentityLog("A"));
            em.persist(new IdentityLog("B"));
            em.persist(new IdentityLog("C"));
            assertThat(inserts()).isEqualTo(3);
            lines.add("IDENTITY: persist 세 번 뒤 INSERT " + inserts() + "번");
            lines.addAll(SqlCapture.sqls());
        });
        tx.executeWithoutResult(s -> {
            SqlCapture.clear();
            SeqLog a = new SeqLog("A");
            em.persist(a);
            em.persist(new SeqLog("B"));
            em.persist(new SeqLog("C"));
            assertThat(inserts()).isZero();
            assertThat(a.getId()).isNotNull();
            lines.add("");
            lines.add("SEQUENCE: persist 세 번 뒤 INSERT " + inserts() + "번, 첫 엔티티의 id " + a.getId());
            lines.addAll(SqlCapture.sqls());
        });
        CiOutput.record(LESSON, "when", String.join("\n", lines));
    }

    @Test
    @DisplayName("allocationSize 50이면 120건을 저장할 때 시퀀스를 몇 번만 부르고, allocationSize 1이면 120번 부른다 (출력 공개)")
    void allocationSizeSavesSequenceCalls() {
        tx.executeWithoutResult(s -> {
            SqlCapture.clear();
            for (int i = 0; i < 120; i++) {
                em.persist(new SeqLog("log" + i));
            }
        });
        long pooled = nextvals();
        tx.executeWithoutResult(s -> {
            SqlCapture.clear();
            for (int i = 0; i < 120; i++) {
                em.persist(new SeqOneLog("log" + i));
            }
        });
        long one = nextvals();

        assertThat(pooled).isLessThanOrEqualTo(4);
        assertThat(one).isEqualTo(120);
        CiOutput.record(LESSON, "allocation", String.join("\n",
                "allocationSize 50: 120건 저장에 시퀀스 호출 " + pooled + "번",
                "allocationSize 1: 120건 저장에 시퀀스 호출 " + one + "번"));
    }

    @Test
    @DisplayName("롤백한 저장이 받은 번호는 다음 저장에 다시 쓰이지 않는다 (IDENTITY, SEQUENCE) (출력 공개)")
    void rollbackLeavesGap() {
        Long[] ids = new Long[4];
        tx.executeWithoutResult(s -> {
            IdentityLog log = new IdentityLog("롤백될 로그");
            em.persist(log);
            ids[0] = log.getId();
            s.setRollbackOnly();
        });
        tx.executeWithoutResult(s -> {
            IdentityLog log = new IdentityLog("다음 로그");
            em.persist(log);
            ids[1] = log.getId();
        });
        tx.executeWithoutResult(s -> {
            SeqOneLog log = new SeqOneLog("롤백될 로그");
            em.persist(log);
            em.flush();
            ids[2] = log.getId();
            s.setRollbackOnly();
        });
        tx.executeWithoutResult(s -> {
            SeqOneLog log = new SeqOneLog("다음 로그");
            em.persist(log);
            ids[3] = log.getId();
        });

        assertThat(ids[1]).isGreaterThan(ids[0]);
        assertThat(ids[3]).isGreaterThan(ids[2]);
        CiOutput.record(LESSON, "gap", String.join("\n",
                "IDENTITY: 롤백된 저장의 id " + ids[0] + ", 다음 저장의 id " + ids[1],
                "SEQUENCE: 롤백된 저장의 id " + ids[2] + ", 다음 저장의 id " + ids[3]));
    }
}
