package lab.devlessons.idgen;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.persistence.EntityManager;
import java.util.function.Consumer;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JDBC 배치를 켠 앱에서 SEQUENCE와 IDENTITY의 INSERT가 배치로 묶이는지 본다.
 * Hibernate가 배치를 보낼 때 남기는 로그(org.hibernate.orm.jdbc.batch, TRACE)를 세어 배치 수를 얻는다.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.jdbc.batch_size=50")
@Import(PostgresContainerConfig.class)
class IdGenerationBatchTest {

    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger batchLogger = (Logger) LoggerFactory.getLogger("org.hibernate.orm.jdbc.batch");
    private Level oldLevel;

    @BeforeEach
    void attach() {
        oldLevel = batchLogger.getLevel();
        batchLogger.setLevel(Level.TRACE);
        appender.start();
        batchLogger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        batchLogger.detachAppender(appender);
        batchLogger.setLevel(oldLevel);
    }

    /** "Executing JDBC batch"류 로그 수 = DB로 보낸 배치 수 */
    private long batches() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.toLowerCase().contains("executing") && m.toLowerCase().contains("batch"))
                .count();
    }

    private long[] save(Consumer<Integer> persist) {
        appender.list.clear();
        long start = System.nanoTime();
        tx.executeWithoutResult(s -> {
            for (int i = 0; i < 1000; i++) {
                persist.accept(i);
            }
        });
        return new long[] {batches(), (System.nanoTime() - start) / 1_000_000};
    }

    @Test
    @DisplayName("batch_size 50이면 SEQUENCE 1,000건은 배치 20번으로 묶이고, IDENTITY는 묶이지 않는다 (출력 공개)")
    void sequenceBatchesIdentityDoesNot() {
        long[] seq = save(i -> em.persist(new SeqLog("log" + i)));
        long[] identity = save(i -> em.persist(new IdentityLog("log" + i)));
        String sample = appender.list.isEmpty() ? "(로그 없음)" : appender.list.get(0).getFormattedMessage();

        CiOutput.record(IdGenerationTest.LESSON, "batch", String.join("\n",
                "hibernate.jdbc.batch_size = 50, 1,000건 저장",
                "SEQUENCE: 배치로 보낸 횟수 " + seq[0] + "번, " + seq[1] + "ms",
                "IDENTITY: 배치로 보낸 횟수 " + identity[0] + "번, " + identity[1] + "ms"));
        assertThat(seq[0]).as("배치 로그 예: %s", sample).isEqualTo(20);
        assertThat(identity[0]).isZero();
    }
}
