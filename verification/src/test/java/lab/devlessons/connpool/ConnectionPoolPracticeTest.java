package lab.devlessons.connpool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 레슨 "DB 커넥션과 커넥션 풀"(D6)의 "실무에서는" 구역을 검증한다.
 * 스프링 부트가 만든 풀의 설정과 상태를 읽고, 닫지 않은 연결이 풀을 말리는 것과 누수 감지 로그를 본다.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class ConnectionPoolPracticeTest {

    private static final String LESSON = "connection-pool";

    @Autowired DataSource dataSource;

    @Test
    @DisplayName("스프링 부트의 DataSource는 HikariDataSource이고, 기본 설정은 최대 10개, 기다리는 최대 시간 30초다. 풀의 상태는 HikariPoolMXBean으로 읽는다")
    void bootDefaultsAndState() throws SQLException {
        HikariDataSource ds = dataSource.unwrap(HikariDataSource.class);
        HikariPoolMXBean bean = ds.getHikariPoolMXBean();
        String idle;
        try (Connection c = ds.getConnection()) {
            idle = "연결 하나를 빌린 동안: active=" + bean.getActiveConnections() + ", idle=" + bean.getIdleConnections()
                    + ", total=" + bean.getTotalConnections() + ", 기다리는 스레드=" + bean.getThreadsAwaitingConnection();
        }
        assertThat(ds.getMaximumPoolSize()).isEqualTo(10);
        assertThat(ds.getConnectionTimeout()).isEqualTo(30_000);
        CiOutput.record(LESSON, "boot-pool", String.join("\n",
                "DataSource 클래스: " + dataSource.getClass().getName(),
                "maximumPoolSize: " + ds.getMaximumPoolSize(),
                "connectionTimeout: " + ds.getConnectionTimeout() + "ms",
                idle));
    }

    @Test
    @DisplayName("빌린 연결을 닫지 않으면 풀이 말라 다음 요청이 시간 초과로 실패하고, leakDetectionThreshold를 두면 누수 경고와 빌린 곳이 로그에 남는다")
    void leakDetection() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("com.zaxxer.hikari.pool.ProxyLeakTask");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        HikariDataSource base = dataSource.unwrap(HikariDataSource.class);
        HikariConfig c = new HikariConfig();
        c.setJdbcUrl(base.getJdbcUrl());
        c.setUsername(base.getUsername());
        c.setPassword(base.getPassword());
        c.setMaximumPoolSize(2);
        c.setConnectionTimeout(500);
        c.setLeakDetectionThreshold(2000);
        c.setPoolName("leak-lesson");
        List<String> log = new ArrayList<>();
        List<Connection> leaked = new ArrayList<>();
        try (HikariDataSource pool = new HikariDataSource(c)) {
            for (int i = 1; i <= 2; i++) {
                Connection conn = pool.getConnection(); // close()하지 않는다
                try (Statement s = conn.createStatement()) {
                    s.execute("select 1");
                }
                leaked.add(conn);
                log.add("요청 " + i + ": getConnection → select 1 → close() 안 함");
            }
            Throwable third = catchThrowable(pool::getConnection);
            log.add("요청 3: " + third.getClass().getSimpleName() + ": " + third.getMessage());
            Thread.sleep(2600);
            List<String> warns = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .filter(m -> m.contains("leak")).toList();
            log.add("");
            log.add("2초 뒤 HikariCP 로그 (" + warns.size() + "줄):");
            warns.stream().limit(1).forEach(log::add);
            ILoggingEvent first = appender.list.stream().filter(e -> e.getFormattedMessage().contains("leak")).findFirst().orElseThrow();
            String where = first.getThrowableProxy() == null ? "" : java.util.Arrays.stream(first.getThrowableProxy().getStackTraceElementProxyArray())
                    .map(Object::toString).filter(t -> t.contains("ConnectionPoolPracticeTest")).findFirst().orElse("");
            log.add("  빌린 곳: " + where.trim());

            assertThat(third.getMessage()).contains("Connection is not available");
            assertThat(warns).isNotEmpty();
            for (Connection conn : leaked) {
                conn.close();
            }
        } finally {
            logger.detachAppender(appender);
        }
        CiOutput.record(LESSON, "leak", String.join("\n", log));
    }
}
