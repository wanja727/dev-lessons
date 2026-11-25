package lab.devlessons.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.JavaSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * 레슨 "예외"(J10)의 주장을 검증한다. 출력 대신 리스트에 기록해서 실행 순서를 본다.
 */
class ExceptionTest {

    private static final String LESSON = "exception";

    static final List<String> LOG = new ArrayList<>();

    /** 코드 res */
    static class Res implements AutoCloseable {
        private final String name;
        private final boolean failOnClose;

        Res(String name) {
            this(name, false);
        }

        Res(String name, boolean failOnClose) {
            this.name = name;
            this.failOnClose = failOnClose;
        }

        @Override
        public void close() {
            LOG.add(name + " 닫힘");
            if (failOnClose) {
                throw new IllegalArgumentException("닫기");
            }
        }
    }

    @Test
    @DisplayName("코드 predict: 본문, B 닫힘, A 닫힘, catch, finally. 예외가 없으면 catch만 빠진다")
    void predict() {
        LOG.clear();
        try (Res a = new Res("A"); Res b = new Res("B")) {
            LOG.add("본문");
            throw new IllegalStateException("실패");
        } catch (IllegalStateException e) {
            LOG.add("catch");
        } finally {
            LOG.add("finally");
        }
        List<String> withError = List.copyOf(LOG);

        LOG.clear();
        try (Res a = new Res("A"); Res b = new Res("B")) {
            LOG.add("본문");
        } catch (IllegalStateException e) {
            LOG.add("catch");
        } finally {
            LOG.add("finally");
        }
        List<String> noError = List.copyOf(LOG);

        assertThat(withError).containsExactly("본문", "B 닫힘", "A 닫힘", "catch", "finally");
        assertThat(noError).containsExactly("본문", "B 닫힘", "A 닫힘", "finally");
        CiOutput.record(LESSON, "predict", String.join("\n", withError));
        CiOutput.record(LESSON, "no-error", String.join("\n", noError));
    }

    @Test
    @DisplayName("본문과 close가 둘 다 예외를 던지면 본문의 예외가 나가고 close의 예외는 suppressed로 붙는다")
    void suppressed() {
        Throwable caught = null;
        try {
            try (Res r = new Res("R", true)) {
                throw new IllegalStateException("본문");
            }
        } catch (Throwable t) {
            caught = t;
        }

        assertThat(caught).isInstanceOf(IllegalStateException.class).hasMessage("본문");
        assertThat(caught.getSuppressed()).hasSize(1);
        assertThat(caught.getSuppressed()[0]).isInstanceOf(IllegalArgumentException.class);
        CiOutput.record(LESSON, "suppressed", """
                밖에서 잡힌 예외 → %s: %s
                getSuppressed()  → [%s: %s]""".formatted(caught.getClass().getSimpleName(), caught.getMessage(),
                caught.getSuppressed()[0].getClass().getSimpleName(), caught.getSuppressed()[0].getMessage()));
    }

    // ── 예외가 거슬러 올라가는 길 ──
    static void client() {
        LOG.add("C1");
        throw new IllegalStateException("결제 서버 응답 없음");
    }

    static void service() {
        LOG.add("S1");
        client();
        LOG.add("S2");
    }

    static void controller() {
        try {
            service();
            LOG.add("OK");
        } catch (IllegalStateException e) {
            LOG.add("ERR");
        }
    }

    @Test
    @DisplayName("던져진 뒤의 줄은 실행되지 않고 받는 catch로 간다")
    void propagation() {
        LOG.clear();
        controller();
        assertThat(LOG).containsExactly("S1", "C1", "ERR");
        CiOutput.record(LESSON, "propagation", String.join("\n", LOG));
    }

    @Test
    @DisplayName("스택 트레이스는 던져진 줄이 맨 위이고 아래로 갈수록 호출한 쪽이다")
    void stackTrace() {
        Throwable caught = null;
        try {
            service();
        } catch (IllegalStateException e) {
            caught = e;
        }
        StackTraceElement[] trace = caught.getStackTrace();
        assertThat(trace[0].getMethodName()).isEqualTo("client");
        assertThat(trace[1].getMethodName()).isEqualTo("service");
        CiOutput.record(LESSON, "stack", caught + "\n"
                + "\tat ExceptionTest.client(ExceptionTest.java:" + trace[0].getLineNumber() + ")\n"
                + "\tat ExceptionTest.service(ExceptionTest.java:" + trace[1].getLineNumber() + ")\n"
                + "\tat ExceptionTest.stackTrace(ExceptionTest.java:" + trace[2].getLineNumber() + ")\n"
                + "\t...");
    }

    static int returnInTry() {
        try {
            return 1;
        } finally {
            LOG.add("F");
        }
    }

    @SuppressWarnings("finally")
    static int returnInFinally() {
        try {
            throw new IllegalStateException("사라지는 예외");
        } finally {
            return 2;
        }
    }

    @Test
    @DisplayName("try에서 return해도 finally가 실행되고, finally에서 return하면 예외가 사라진다")
    void finallyRules() {
        LOG.clear();
        assertThat(returnInTry()).isEqualTo(1);
        assertThat(LOG).containsExactly("F");
        assertThat(returnInFinally()).isEqualTo(2);
    }

    // ── 삼키기 ──
    interface PaymentClient {
        void request(long orderId) throws IOException;
    }

    static class SwallowingService {
        private final PaymentClient client;

        SwallowingService(PaymentClient client) {
            this.client = client;
        }

        boolean pay(long orderId) {
            try {
                client.request(orderId);
            } catch (Exception e) {
                // 로그만 남긴다
            }
            return true;
        }
    }

    static class PaymentFailedException extends RuntimeException {
        PaymentFailedException(long orderId, Throwable cause) {
            super("결제 실패 orderId=" + orderId, cause);
        }

        PaymentFailedException(long orderId) {
            super("결제 실패 orderId=" + orderId);
        }
    }

    @Test
    @DisplayName("예외를 삼킨 메서드는 호출한 쪽에 성공한 값을 돌려준다")
    void swallowing() {
        PaymentClient failing = id -> {
            throw new IOException("결제 서버 연결 끊김");
        };
        boolean result = new SwallowingService(failing).pay(1L);

        assertThat(result).isTrue();
        CiOutput.record(LESSON, "swallow", "client.request가 IOException을 던졌을 때\npay(1L)의 반환값 → " + result);
    }

    @Test
    @DisplayName("원인을 넘겨 다시 던지면 getCause가 원래 예외이고 Caused by가 나온다. 넘기지 않으면 null이다")
    void rethrowWithCause() {
        IOException original = new IOException("결제 서버 연결 끊김");
        PaymentFailedException withCause = new PaymentFailedException(1L, original);
        PaymentFailedException withoutCause = new PaymentFailedException(1L);

        StringWriter out = new StringWriter();
        withCause.printStackTrace(new PrintWriter(out));
        String trace = out.toString();

        assertThat(withCause.getCause()).isSameAs(original);
        assertThat(withoutCause.getCause()).isNull();
        assertThat(trace).contains("Caused by: java.io.IOException: 결제 서버 연결 끊김");
        String summary = trace.lines()
                .filter(l -> !l.startsWith("\tat") && !l.startsWith("\t..."))
                .reduce((a, b) -> a + "\n" + b).orElse("")
                .replace("lab.devlessons.exception.ExceptionTest$", "");
        CiOutput.record(LESSON, "cause", """
                원인을 넘겼을 때 (스택 트레이스에서 at 줄을 뺀 것)
                %s

                원인을 넘기지 않았을 때
                getCause() → %s""".formatted(summary, withoutCause.getCause()));
    }

    @Test
    @DisplayName("log.error(메시지, 예외)는 예외 정보를 남기고, 메시지에 getMessage만 이어 붙이면 남지 않는다")
    void loggingException() {
        Logger logger = (Logger) LoggerFactory.getLogger("lab.devlessons.exception.PayService");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        IOException e = new IOException("결제 서버 연결 끊김");

        logger.error("결제 실패: " + e.getMessage());
        logger.error("결제 실패 orderId={}", 1L, e);
        logger.detachAppender(appender);

        ILoggingEvent onlyMessage = appender.list.get(0);
        ILoggingEvent withException = appender.list.get(1);
        assertThat(onlyMessage.getThrowableProxy()).isNull();
        assertThat(withException.getThrowableProxy()).isNotNull();
        assertThat(withException.getFormattedMessage()).isEqualTo("결제 실패 orderId=1");
        CiOutput.record(LESSON, "logging", """
                log.error("결제 실패: " + e.getMessage())
                → 메시지: %s
                → 예외 정보: %s

                log.error("결제 실패 orderId={}", orderId, e)
                → 메시지: %s
                → 예외 정보: %s""".formatted(onlyMessage.getFormattedMessage(),
                onlyMessage.getThrowableProxy() == null ? "없음" : "있음",
                withException.getFormattedMessage(),
                withException.getThrowableProxy().getClassName() + ": " + withException.getThrowableProxy().getMessage()));
    }

    @Test
    @DisplayName("체크 예외는 잡거나 throws로 선언해야 컴파일되고, 언체크 예외는 그렇지 않다")
    void checkedVersusUnchecked() {
        JavaSource.Result checked = JavaSource.compile("Reader", """
                import java.nio.file.*;
                public class Reader {
                  String read(Path path) { return Files.readString(path); }
                }
                """);
        JavaSource.Result unchecked = JavaSource.compile("Finder", """
                class OrderNotFoundException extends RuntimeException {}
                public class Finder {
                  void find() { throw new OrderNotFoundException(); }
                }
                """);
        JavaSource.Result declared = JavaSource.compile("Reader", """
                import java.io.IOException;
                import java.nio.file.*;
                public class Reader {
                  String read(Path path) throws IOException { return Files.readString(path); }
                }
                """);

        assertThat(checked.ok()).isFalse();
        assertThat(unchecked.ok()).isTrue();
        assertThat(declared.ok()).isTrue();
        CiOutput.record(LESSON, "checked", """
                IOException을 잡지도 선언하지도 않음
                → error: %s

                RuntimeException의 자식을 던짐 → 컴파일됨
                throws IOException을 선언함      → 컴파일됨""".formatted(checked.errors().get(0)));
    }

    @Test
    @DisplayName("부모 타입의 catch를 자식 타입의 catch보다 먼저 쓰면 컴파일되지 않는다")
    void catchOrder() {
        JavaSource.Result r = JavaSource.compile("Shop", """
                public class Shop {
                  void run() {
                    try {
                      throw new IllegalStateException();
                    } catch (RuntimeException e) {
                    } catch (IllegalStateException e) {
                    }
                  }
                }
                """);
        assertThat(r.ok()).isFalse();
        assertThat(r.errors()).anyMatch(e -> e.contains("has already been caught"));
    }
}
