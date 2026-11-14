package lab.devlessons.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** @Transactional은 프록시가 처리한다. 테스트 자체에는 트랜잭션이 없다. */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class TransactionalProxyTest {

    @Autowired TxProbeService probe;

    @Test
    @DisplayName("@Transactional이 붙은 빈은 원본이 아니라 CGLIB 프록시가 주입된다")
    void beanIsProxy() {
        assertThat(AopUtils.isAopProxy(probe)).isTrue();
        assertThat(AopUtils.isCglibProxy(probe)).isTrue();
        assertThat(probe.getClass()).isNotEqualTo(TxProbeService.class);
        assertThat(AopUtils.getTargetClass(probe)).isEqualTo(TxProbeService.class);
    }

    @Test
    @DisplayName("밖에서 프록시를 거쳐 부르면 메서드 안에서 트랜잭션이 열려 있다")
    void externalCallHasTransaction() {
        assertThat(probe.annotated()).isTrue();
    }

    @Test
    @DisplayName("@Transactional이 없는 메서드 안에는 트랜잭션이 없다")
    void noAnnotationNoTransaction() {
        assertThat(probe.notAnnotated()).isFalse();
    }

    @Test
    @DisplayName("같은 객체 안에서 부르면(this 호출) @Transactional이 붙어 있어도 트랜잭션이 없다")
    void internalCallHasNoTransaction() {
        assertThat(probe.callAnnotatedInternally()).isFalse();
    }

    @Test
    @DisplayName("TransactionTemplate은 프록시 없이도 트랜잭션을 연다")
    void transactionTemplateOpensTransaction() {
        assertThat(probe.withTemplate()).isTrue();
    }
}
