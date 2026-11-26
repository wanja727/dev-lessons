package lab.devlessons.linux;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

/**
 * 레슨 "리눅스 기초"(T8). 우분투 기반 JDK 컨테이너에 작은 자바 서버를 띄우고
 * ps, kill, ss, curl, grep, chmod를 실제로 돌려 출력을 본다.
 */
class LinuxBasicsTest {

    static final String LESSON = "linux-basics";

    /** 8080을 듣고, /health에 up을 답하고, 끝날 때 종료 훅에서 파일에 "정리 완료"를 쓰는 서버 */
    static final String SERVER = """
            import com.sun.net.httpserver.HttpServer;
            import java.net.InetSocketAddress;
            import java.nio.file.Files;
            import java.nio.file.Path;

            public class Server {
                public static void main(String[] args) throws Exception {
                    String hookFile = args.length > 0 ? args[0] : "/app/hook.txt";
                    HttpServer s = HttpServer.create(new InetSocketAddress(8080), 0);
                    s.createContext("/health", ex -> {
                        byte[] b = "up".getBytes();
                        ex.sendResponseHeaders(200, b.length);
                        ex.getResponseBody().write(b);
                        ex.close();
                    });
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        try {
                            Files.writeString(Path.of(hookFile), "정리 완료");
                        } catch (Exception ignored) {
                        }
                    }));
                    s.start();
                    System.out.println("order-api started on 8080");
                }
            }
            """;

    static final String LOG = """
            2026-11-25 10:00:01 INFO  [req-1] 주문 1001 생성
            2026-11-25 10:00:02 INFO  [req-2] 주문 1002 생성
            2026-11-25 10:00:03 ERROR [req-3] 결제 실패 주문 1003
            java.lang.IllegalStateException: 결제사 응답 없음
            \tat com.shop.PayClient.pay(PayClient.java:42)
            \tat com.shop.OrderService.order(OrderService.java:30)
            Caused by: java.net.SocketTimeoutException: Read timed out
            2026-11-25 10:00:04 INFO  [req-4] 주문 1004 생성
            2026-11-25 10:00:05 ERROR [req-5] 결제 실패 주문 1005
            2026-11-25 10:00:06 WARN  [req-6] 재고 부족 PEN-1
            """;

    static GenericContainer<?> box;

    @BeforeAll
    static void start() throws Exception {
        box = new GenericContainer<>(DockerImageName.parse("eclipse-temurin:21-jdk"))
                .withCommand("sleep", "infinity")
                .withCopyToContainer(Transferable.of(SERVER), "/app/Server.java")
                .withCopyToContainer(Transferable.of(LOG), "/app/app.log")
                .withStartupTimeout(Duration.ofMinutes(3));
        box.start();
        // 이미지에 없을 수 있는 명령을 깐다 (ps는 procps, ss는 iproute2)
        ExecResult apt = box.execInContainer("sh", "-c",
                "command -v ps >/dev/null && command -v ss >/dev/null && command -v curl >/dev/null"
                        + " || (apt-get update -qq && apt-get install -y -qq procps iproute2 curl >/dev/null)");
        assertThat(apt.getExitCode()).as(apt.getStderr()).isZero();
        // 서버를 뒤에서 띄우고 8080을 들을 때까지 기다린다
        sh("cd /app; nohup java Server.java > out.log 2>&1 &");
        sh("for i in $(seq 1 60); do curl -s localhost:8080/health >/dev/null && break; sleep 0.5; done");
    }

    @AfterAll
    static void stop() {
        box.stop();
    }

    static String sh(String script) throws Exception {
        ExecResult r = box.execInContainer("bash", "-c", script);
        return (r.getStdout() + r.getStderr()).strip();
    }

    @Test
    @DisplayName("ps aux | grep은 grep 자신의 줄도 찍고, pgrep -f는 서버의 PID만 돌려준다")
    void findProcess() throws Exception {
        // execInContainer가 띄운 bash -c 줄도 Server.java를 담고 있어서 보여 줄 때는 뺀다
        String grep = sh("ps aux | grep Server.java").lines()
                .filter(l -> !l.contains("bash -c")).collect(java.util.stream.Collectors.joining(System.lineSeparator().substring(System.lineSeparator().length() - 1)));
        String pgrep = sh("pgrep -f '^java Server.java$'");
        String header = sh("ps aux | head -1");

        assertThat(grep.lines().count()).isEqualTo(2);
        assertThat(grep).contains("grep Server.java");
        assertThat(pgrep.lines().count()).isEqualTo(1);
        assertThat(pgrep).matches("\\d+");
        CiOutput.record(LESSON, "ps", """
                $ ps aux | head -1
                %s
                $ ps aux | grep Server.java
                %s

                $ pgrep -f '^java Server.java$'
                %s""".formatted(header, trimColumns(grep), pgrep));
    }

    /** ps aux의 줄이 길어서 앞의 열 몇 개와 명령만 남긴다 */
    static String trimColumns(String lines) {
        return String.join("\n", lines.lines().map(l -> {
            String[] p = l.trim().split("\\s+", 11);
            return p.length < 11 ? l : "%s %s ... %s".formatted(p[0], p[1], p[10]);
        }).toList());
    }

    @Test
    @DisplayName("ss -ltnp에 8080을 듣는 java와 PID가 나오고, 같은 포트로 두 번째 서버를 띄우면 BindException이 난다")
    void whoListens() throws Exception {
        String ss = sh("ss -ltnp | grep 8080");
        String pid = sh("pgrep -f '^java Server.java$'");
        String curl = sh("curl -s -w ' (%{http_code})' localhost:8080/health");
        String second = sh("cd /app && timeout 30 java Server.java /app/hook2.txt; echo \"exit=$?\"");

        assertThat(ss).contains("LISTEN").contains(":8080").contains("java").contains("pid=" + pid);
        assertThat(curl).isEqualTo("up (200)");
        assertThat(second).contains("BindException").contains("Address already in use");
        String bind = second.lines().filter(l -> l.contains("BindException")).findFirst().orElse(second);
        CiOutput.record(LESSON, "ss", """
                $ ss -ltnp | grep 8080
                %s

                $ curl -s localhost:8080/health
                %s

                $ java Server.java   (같은 8080으로 하나 더)
                %s""".formatted(ss.replaceAll("\\s+", " "), curl, bind.strip()));
    }

    /** 서버 하나를 따로 띄우고 신호를 보낸 뒤 종료 코드와 종료 훅 파일을 본다 */
    static String signal(String sig, String hookFile) throws Exception {
        return sh("""
                cd /app && rm -f %1$s
                java Server.java %1$s > /dev/null 2>&1 &
                pid=$!
                for i in $(seq 1 60); do [ -n "$(ss -ltnp | grep "pid=$pid")" ] && break; sleep 0.5; done
                kill %2$s $pid
                wait $pid
                echo "종료 코드 $?"
                echo "hook 파일: $(cat %1$s 2>/dev/null || echo 없음)"
                """.formatted(hookFile, sig)).lines().filter(l -> !l.contains("Killed"))
                .collect(java.util.stream.Collectors.joining(System.lineSeparator().substring(System.lineSeparator().length() - 1)));
    }

    @Test
    @DisplayName("kill(SIGTERM)은 종료 훅을 돌리고 143으로 끝나며, kill -9는 종료 훅 없이 137로 끝난다")
    void killAndKill9() throws Exception {
        // 8080은 위 서버가 잡고 있으니 먼저 끈다
        sh("pkill -f '^java Server.java'; sleep 2");
        String term = signal("", "/app/term.txt");
        String kill9 = signal("-9", "/app/kill9.txt");
        // 다른 테스트가 쓰도록 서버를 다시 띄운다
        sh("cd /app; nohup java Server.java > out.log 2>&1 &");
        sh("for i in $(seq 1 60); do curl -s localhost:8080/health >/dev/null && break; sleep 0.5; done");

        assertThat(term).contains("종료 코드 143").contains("hook 파일: 정리 완료");
        assertThat(kill9).contains("종료 코드 137").contains("hook 파일: 없음");
        CiOutput.record(LESSON, "kill", """
                $ kill $PID        (SIGTERM)
                %s

                $ kill -9 $PID     (SIGKILL)
                %s""".formatted(term, kill9));
    }

    @Test
    @DisplayName("grep -n, grep -A, 파이프로 로그를 거르고 센다")
    void grepLogs() throws Exception {
        String errors = sh("grep -n ERROR /app/app.log");
        String after = sh("grep -A 3 'IllegalStateException' /app/app.log");
        String count = sh("grep -c ERROR /app/app.log");
        String perLevel = sh("awk '{print $3}' /app/app.log | grep -E '^(INFO|WARN|ERROR)$' | sort | uniq -c");
        String tail = sh("tail -n 2 /app/app.log");

        assertThat(errors.lines().count()).isEqualTo(2);
        assertThat(after).contains("Caused by: java.net.SocketTimeoutException");
        assertThat(count).isEqualTo("2");
        CiOutput.record(LESSON, "grep", """
                $ grep -n ERROR app.log
                %s

                $ grep -A 3 IllegalStateException app.log
                %s

                $ grep -c ERROR app.log
                %s

                $ awk '{print $3}' app.log | grep -E '^(INFO|WARN|ERROR)$' \\
                    | sort | uniq -c
                %s

                $ tail -n 2 app.log
                %s""".formatted(errors, after, count, perLevel, tail));
    }

    @Test
    @DisplayName("df와 du로 디스크와 폴더의 크기를 본다")
    void diskUsage() throws Exception {
        sh("mkdir -p /var/log/order && head -c 5000000 /dev/zero > /var/log/order/app-2026-11-24.log");
        String df = sh("df -h / | tail -1");
        String du = sh("du -sh /var/log/order");

        assertThat(du).contains("/var/log/order");
        CiOutput.record(LESSON, "disk", """
                $ df -h /
                Filesystem Size Used Avail Use%% Mounted on
                %s

                $ du -sh /var/log/order
                %s""".formatted(df.replaceAll("\\s+", " "), du.replaceAll("\\s+", " ")));
    }

    @Test
    @DisplayName("실행 권한이 없는 스크립트는 Permission denied로 실패하고 chmod +x 뒤에 돈다")
    void permissions() throws Exception {
        sh("printf '#!/bin/sh\\necho 배포 시작\\n' > /app/deploy.sh && chmod 644 /app/deploy.sh");
        String before = sh("ls -l /app/deploy.sh | awk '{print $1, $NF}'; cd /app && ./deploy.sh; echo \"exit=$?\"");
        String after = sh("chmod +x /app/deploy.sh && ls -l /app/deploy.sh | awk '{print $1, $NF}'"
                + " && cd /app && ./deploy.sh; echo \"exit=$?\"");

        assertThat(before).contains("Permission denied").contains("exit=126");
        assertThat(after).contains("배포 시작").contains("exit=0");
        CiOutput.record(LESSON, "chmod", """
                $ ls -l deploy.sh && ./deploy.sh
                %s

                $ chmod +x deploy.sh && ls -l deploy.sh && ./deploy.sh
                %s""".formatted(before.replaceAll("bash: line \\d+: ", ""), after));
    }
}
