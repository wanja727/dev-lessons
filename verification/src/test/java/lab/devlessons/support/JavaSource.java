package lab.devlessons.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/**
 * 레슨이 "이 코드는 컴파일되지 않는다"고 말하거나 컴파일한 클래스 파일을 들여다볼 때 쓴다.
 * 소스 문자열을 테스트를 돌리는 JDK의 javac(자바 21)로 컴파일한다.
 *
 * <pre>
 * Result r = JavaSource.compile(Map.of("Order", "class Order { ... }"));
 * r.ok();           // 오류가 없으면 true
 * r.errors();       // 오류 메시지(영어)
 * r.load("Order");  // 컴파일한 클래스를 읽는다
 * </pre>
 */
public final class JavaSource {

    private JavaSource() {
    }

    /** 컴파일 결과. 클래스 파일은 dir에 있다 */
    public record Result(boolean ok, List<String> errors, List<String> warnings, Path dir) {

        public Class<?> load(String className) {
            try {
                URLClassLoader loader = new URLClassLoader(
                        new java.net.URL[] {dir.toUri().toURL()}, JavaSource.class.getClassLoader());
                return loader.loadClass(className);
            } catch (Exception e) {
                throw new IllegalStateException(className + "을 읽지 못했다", e);
            }
        }

        public Path classFile(String className) {
            return dir.resolve(className.replace('.', '/') + ".class");
        }
    }

    /** 파일 하나. 클래스 이름은 소스의 public 클래스와 같게 준다 */
    public static Result compile(String className, String source, String... options) {
        return compile(Map.of(className, source), options);
    }

    /** 여러 파일을 한 번에. 키는 클래스 이름(패키지가 있으면 a.b.C), 값은 소스 */
    public static Result compile(Map<String, String> sources, String... options) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK로 돌려야 한다 (JRE에는 컴파일러가 없다)");
        }
        Path dir;
        try {
            dir = Files.createTempDirectory("javasource");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<JavaFileObject> files = new ArrayList<>();
        sources.forEach((name, src) -> files.add(new SimpleJavaFileObject(
                URI.create("string:///" + name.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return src;
            }
        }));
        List<String> args = new ArrayList<>(List.of("-d", dir.toString(), "-proc:none", "-Xlint:unchecked",
                "-cp", System.getProperty("java.class.path")));
        args.addAll(List.of(options));
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean ok = compiler.getTask(null, null, diagnostics, args, null, files).call();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            String text = d.getMessage(Locale.ENGLISH);
            if (d.getKind() == Diagnostic.Kind.ERROR) {
                errors.add(text);
            } else {
                warnings.add(text);
            }
        }
        return new Result(ok, errors, warnings, dir);
    }
}
