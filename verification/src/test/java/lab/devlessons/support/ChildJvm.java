package lab.devlessons.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JVM 옵션(-Xmx, -Xss, -Xlog:gc, GC 선택)에 따라 달라지는 동작을 보려고 JVM을 하나 더 띄운다.
 * 테스트를 돌리는 JVM과 같은 java를 쓰고, 클래스패스도 같다. 그래서 테스트 소스의 클래스를 main으로 쓸 수 있다.
 *
 * <pre>
 * Run r = ChildJvm.run(List.of("-Xmx64m"), HeapFill.class);
 * r.exitCode(); r.stdout(); r.stderr();
 * </pre>
 */
public final class ChildJvm {

    private ChildJvm() {
    }

    public record Run(int exitCode, String stdout, String stderr) {
    }

    public static Run run(List<String> jvmOptions, Class<?> mainClass, String... args) {
        return run(jvmOptions, System.getProperty("java.class.path"), mainClass.getName(), args);
    }

    public static Run run(List<String> jvmOptions, String classPath, String mainClass, String... args) {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = new ArrayList<>();
        command.add(java);
        command.addAll(jvmOptions);
        command.add("-cp");
        command.add(classPath);
        command.add(mainClass);
        command.addAll(List.of(args));
        try {
            Process p = new ProcessBuilder(command).start();
            // 출력이 커도 막히지 않게 따로 읽는다
            var out = new java.util.concurrent.CompletableFuture<String>();
            var err = new java.util.concurrent.CompletableFuture<String>();
            Thread.ofVirtual().start(() -> out.complete(read(p.getInputStream())));
            Thread.ofVirtual().start(() -> err.complete(read(p.getErrorStream())));
            if (!p.waitFor(120, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IllegalStateException("자식 JVM이 2분 안에 끝나지 않았다: " + command);
            }
            return new Run(p.exitValue(), out.get(10, TimeUnit.SECONDS), err.get(10, TimeUnit.SECONDS));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String read(java.io.InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
