package lab.devlessons.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 레슨 "AOP와 감사 로그"의 주장을 검증한다. 테스트에는 트랜잭션이 없다. */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class AuditAspectTest {

    @Autowired LimitService service;
    @Autowired LimitAccountRepository accounts;
    @Autowired AuditLogRepository logs;

    Long accountId;

    @BeforeEach
    void setUp() {
        logs.deleteAll();
        accounts.deleteAll();
        accountId = accounts.save(new LimitAccount(100)).getId();
        CurrentActor.set("admin-kim");
    }

    @AfterEach
    void tearDown() {
        CurrentActor.clear();
    }

    @Test
    @DisplayName("@Audited 메서드를 부르면 누가, 무엇을, 변경 전후 값, 성공 여부가 감사 로그로 남는다")
    void successIsAudited() {
        service.changeLimit(accountId, 500);

        List<AuditLog> all = logs.findAll();
        assertThat(all).hasSize(1);
        AuditLog log = all.get(0);
        assertThat(log.getActor()).isEqualTo("admin-kim");
        assertThat(log.getAction()).isEqualTo("CHANGE_LIMIT");
        assertThat(log.getTargetId()).isEqualTo(accountId);
        assertThat(log.getBeforeValue()).isEqualTo("dailyLimit=100");
        assertThat(log.getAfterValue()).isEqualTo("dailyLimit=500");
        assertThat(log.getStatus()).isEqualTo("SUCCESS");
        assertThat(log.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("서비스 빈은 프록시이고, 감사 기능은 서비스 코드 안에 한 줄도 없다")
    void serviceIsProxy() {
        assertThat(AopUtils.isAopProxy(service)).isTrue();
    }

    @Test
    @DisplayName("실패 기록을 같은 트랜잭션에 남기면 본 작업과 함께 롤백되어 감사 로그가 사라진다")
    void failureLogInSameTxIsLost() {
        assertThatThrownBy(() -> service.changeLimitNaive(accountId, -1)).isInstanceOf(IllegalArgumentException.class);

        assertThat(logs.count()).isZero();
        assertThat(accounts.findById(accountId).orElseThrow().getDailyLimit()).isEqualTo(100);
    }

    @Test
    @DisplayName("실패 기록을 REQUIRES_NEW로 남기면 본 작업은 롤백되어도 FAILED 감사 로그는 남는다")
    void failureLogInNewTxSurvives() {
        assertThatThrownBy(() -> service.changeLimit(accountId, -1)).isInstanceOf(IllegalArgumentException.class);

        List<AuditLog> all = logs.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getStatus()).isEqualTo("FAILED");
        assertThat(all.get(0).getBeforeValue()).isEqualTo("dailyLimit=100");
        assertThat(all.get(0).getError()).contains("한도는 0 이상");
        assertThat(accounts.findById(accountId).orElseThrow().getDailyLimit()).isEqualTo(100);
    }

    @Test
    @DisplayName("같은 클래스 안에서 부르면 변경은 일어나도 감사 로그는 남지 않는다 (내부 호출)")
    void selfInvocationIsNotAudited() {
        service.resetLimit(accountId);

        assertThat(accounts.findById(accountId).orElseThrow().getDailyLimit()).isZero();
        assertThat(logs.count()).isZero();
    }
}
