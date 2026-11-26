package lab.devlessons.txbasics;

import static lab.devlessons.support.Jdbc.exec;
import static lab.devlessons.support.Jdbc.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 레슨 "트랜잭션 기초"(D5)의 "실무에서는" 구역을 검증한다.
 * 트랜잭션 매니저의 debug 로그로 트랜잭션이 열리고 닫히는 것을 보고, 트랜잭션 안의 메일 발송과 커밋 뒤 발송을 비교한다.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class TxBasicsPracticeTest {

    private static final String LESSON = "tx-basics";

    @Autowired DataSource ds;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    @BeforeEach
    void setUp() {
        exec(ds, "drop table if exists txp_order", "create table txp_order (id int primary key, status text not null)");
    }

    @Test
    @DisplayName("트랜잭션 매니저의 debug 로그에 트랜잭션 시작, 커밋, 롤백이 남는다")
    void transactionLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(txManager.getClass());
        Level before = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        try {
            TransactionTemplate tt = new TransactionTemplate(txManager);
            tt.executeWithoutResult(s -> jdbc.update("insert into txp_order values (1, 'PAID')"));
            catchThrowable(() -> tt.executeWithoutResult(s -> {
                jdbc.update("insert into txp_order values (2, 'PAID')");
                throw new IllegalStateException("결제 실패");
            }));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(before);
        }
        List<String> lines = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.startsWith("Creating new transaction") || m.startsWith("Committing")
                        || m.startsWith("Initiating transaction rollback") || m.startsWith("Rolling back"))
                .map(m -> m.length() > 110 ? m.substring(0, 110) + "..." : m)
                .toList();

        assertThat(lines).anyMatch(l -> l.startsWith("Creating new transaction"));
        assertThat(lines).anyMatch(l -> l.startsWith("Committing"));
        assertThat(lines).anyMatch(l -> l.startsWith("Rolling back"));
        assertThat(number(ds, "select count(*) from txp_order")).isEqualTo(1);
        CiOutput.record(LESSON, "tx-log", "로거: " + txManager.getClass().getName() + " (DEBUG)\n" + String.join("\n", lines));
    }

    @Test
    @DisplayName("트랜잭션 안에서 보낸 메일은 롤백돼도 나가고, 커밋 뒤에 보내도록 등록한 메일은 롤백되면 나가지 않는다")
    void mailAfterCommit() {
        TransactionTemplate tt = new TransactionTemplate(txManager);
        List<String> sent = new ArrayList<>();

        catchThrowable(() -> tt.executeWithoutResult(s -> {
            jdbc.update("insert into txp_order values (1, 'PAID')");
            sent.add("[안에서 바로] 주문 1 확인 메일");
            throw new IllegalStateException("재고 부족");
        }));
        catchThrowable(() -> tt.executeWithoutResult(s -> {
            jdbc.update("insert into txp_order values (2, 'PAID')");
            afterCommit(() -> sent.add("[커밋 뒤] 주문 2 확인 메일"));
            throw new IllegalStateException("재고 부족");
        }));
        tt.executeWithoutResult(s -> {
            jdbc.update("insert into txp_order values (3, 'PAID')");
            afterCommit(() -> sent.add("[커밋 뒤] 주문 3 확인 메일"));
        });
        long saved = number(ds, "select count(*) from txp_order");

        assertThat(sent).containsExactly("[안에서 바로] 주문 1 확인 메일", "[커밋 뒤] 주문 3 확인 메일");
        assertThat(saved).isEqualTo(1);
        CiOutput.record(LESSON, "after-commit", String.join("\n",
                "주문 1: 트랜잭션 안에서 메일을 보낸 뒤 예외 → 롤백",
                "주문 2: 커밋 뒤에 보내도록 등록한 뒤 예외 → 롤백",
                "주문 3: 커밋 뒤에 보내도록 등록, 정상 → 커밋",
                "",
                "저장된 주문: " + saved + "건 (주문 3)",
                "나간 메일:",
                String.join("\n", sent)));
    }

    private static void afterCommit(Runnable r) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                r.run();
            }
        });
    }
}
