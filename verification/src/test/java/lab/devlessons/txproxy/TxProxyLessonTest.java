package lab.devlessons.txproxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import javax.sql.DataSource;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 레슨 "@Transactional 동작 원리"(S10)에서 예전 테스트(transaction/TransactionalProxyTest, SelfInvocationTest)가
 * 다루지 않은 주장을 확인한다. 테스트에는 트랜잭션이 없다. 레슨용 빈은 @Import로만 넣는다.
 */
@SpringBootTest
@Import({PostgresContainerConfig.class, TxProxyLessonTest.Probe.class, TxProxyLessonTest.ReadOnlyByDefault.class,
        TxProxyLessonTest.TemplateOrderService.class})
class TxProxyLessonTest {

    private static final String LESSON = "transactional-proxy";

    static boolean active() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }

    public static class Probe {
        private final JdbcTemplate jdbc;
        private final DataSource dataSource;

        public Probe(JdbcTemplate jdbc, DataSource dataSource) {
            this.jdbc = jdbc;
            this.dataSource = dataSource;
        }

        @Transactional
        public List<Integer> twoPids() {
            Integer a = jdbc.queryForObject("select pg_backend_pid()", Integer.class);
            Integer b = jdbc.queryForObject("select pg_backend_pid()", Integer.class);
            return List.of(a, b);
        }

        @Transactional
        protected boolean protectedMethod() {
            return active();
        }

        @Transactional
        boolean packageMethod() {
            return active();
        }

        @Transactional
        public final boolean finalMethod() {
            return active();
        }

        public boolean callPrivate() {
            return privateMethod();
        }

        @Transactional
        private boolean privateMethod() {
            return active();
        }

        /** 트랜잭션 안에서 외부 호출을 흉내 내는 동안 풀의 활성 커넥션 수를 읽는다 */
        @Transactional
        public int activeDuringExternalCallInTx() throws InterruptedException {
            jdbc.update("insert into tx_proxy_order(name) values ('paying')");
            int during = activeConnections();
            Thread.sleep(50); // 외부 결제 API
            return during;
        }

        public int activeDuringExternalCallOutside() throws InterruptedException {
            int during = activeConnections();
            Thread.sleep(50);
            return during;
        }

        int activeConnections() {
            return ((HikariDataSource) dataSource).getHikariPoolMXBean().getActiveConnections();
        }
    }

    @Transactional(readOnly = true)
    public static class ReadOnlyByDefault {
        public boolean read() {
            return TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        }

        @Transactional
        public boolean write() {
            return TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        }
    }

    /** 레슨의 코드 template */
    public static class TemplateOrderService {
        private final TransactionTemplate tx;
        private final JdbcTemplate jdbc;
        boolean insideActive;
        boolean outsideActive;

        public TemplateOrderService(TransactionTemplate tx, JdbcTemplate jdbc) {
            this.tx = tx;
            this.jdbc = jdbc;
        }

        public void placeOrder(String name, boolean failNotify) {
            outsideActive = active();
            tx.executeWithoutResult(s -> {
                insideActive = active();
                jdbc.update("insert into tx_proxy_order(name) values (?)", name);
            });
            if (failNotify) {
                throw new IllegalStateException("알림 실패");
            }
        }
    }

    @Autowired Probe probe;
    @Autowired ReadOnlyByDefault readOnly;
    @Autowired TemplateOrderService templateService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void table() {
        jdbc.execute("create table if not exists tx_proxy_order(id serial primary key, name text not null)");
        jdbc.update("delete from tx_proxy_order");
    }

    @Test
    @DisplayName("한 @Transactional 메서드 안의 두 SQL은 같은 커넥션(같은 pg_backend_pid)으로 나간다")
    void sameConnectionInTransaction() {
        List<Integer> pids = probe.twoPids();

        assertThat(pids.get(0)).isEqualTo(pids.get(1));
        CiOutput.record(LESSON, "pid", "@Transactional 메서드 안에서 select pg_backend_pid() 두 번\n첫째: " + pids.get(0) + "\n둘째: " + pids.get(1));
    }

    @Test
    @DisplayName("protected와 패키지 접근 메서드는 밖에서 부르면 트랜잭션이 있고(스프링 6), final과 private 메서드에는 없다")
    void visibility() {
        boolean prot = probe.protectedMethod();
        boolean pkg = probe.packageMethod();
        boolean fin = probe.finalMethod();
        boolean priv = probe.callPrivate();

        assertThat(prot).isTrue();
        assertThat(pkg).isTrue();
        assertThat(fin).isFalse();
        assertThat(priv).isFalse();
        CiOutput.record(LESSON, "visibility", "protected 메서드 → " + prot + "\n패키지 접근 메서드 → " + pkg
                + "\npublic final 메서드 → " + fin + "\nprivate 메서드(같은 클래스에서 부름) → " + priv);
    }

    @Test
    @DisplayName("클래스의 readOnly = true를 메서드의 @Transactional이 덮는다")
    void methodOverridesClass() {
        boolean read = readOnly.read();
        boolean write = readOnly.write();

        assertThat(read).isTrue();
        assertThat(write).isFalse();
        CiOutput.record(LESSON, "read-only", "클래스에 @Transactional(readOnly = true)\nread() (메서드에 표시 없음) → 읽기 전용 " + read
                + "\nwrite() (메서드에 @Transactional) → 읽기 전용 " + write);
    }

    @Test
    @DisplayName("TransactionTemplate 람다 안에서만 트랜잭션이 열리고, 람다가 끝나면 커밋되어 뒤에서 예외가 나도 남는다")
    void transactionTemplate() {
        assertThatThrownBy(() -> templateService.placeOrder("latte", true)).isInstanceOf(IllegalStateException.class);
        Integer rows = jdbc.queryForObject("select count(*) from tx_proxy_order", Integer.class);

        assertThat(templateService.outsideActive).isFalse();
        assertThat(templateService.insideActive).isTrue();
        assertThat(rows).isEqualTo(1);
        CiOutput.record(LESSON, "template", "람다 밖에서 트랜잭션? " + templateService.outsideActive
                + "\n람다 안에서 트랜잭션? " + templateService.insideActive
                + "\n람다 뒤 notifyUser에서 예외 → 주문 행 " + rows + "개 (이미 커밋됨)");
    }

    @Test
    @DisplayName("트랜잭션 안에서 외부 호출을 기다리는 동안 커넥션을 쥐고 있고, 트랜잭션 밖에서는 쥐지 않는다")
    void connectionHeldDuringExternalCall() throws Exception {
        int inTx = probe.activeDuringExternalCallInTx();
        int outside = probe.activeDuringExternalCallOutside();

        assertThat(inTx).isGreaterThanOrEqualTo(1);
        assertThat(outside).isZero();
        CiOutput.record(LESSON, "hold", "외부 호출을 기다리는 동안 풀의 활성 커넥션\n@Transactional 안: " + inTx + "개\n트랜잭션 밖: " + outside + "개");
    }
}
