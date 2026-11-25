package lab.devlessons.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/**
 * 설계 레슨이 "이 클래스를 바꾸면 어느 파일이 깨지나"를 말할 때 쓴다.
 * JavaSource와 같이 소스 문자열을 컴파일하되, 오류가 난 파일 이름을 함께 돌려준다.
 * 키는 파일 이름(클래스 이름, 패키지가 있으면 a.b.C)이다.
 *
 * <pre>
 * BrokenFiles r = BrokenFiles.compile(sources);
 * r.files();     // [OrderPage, ReceiptPage]
 * r.messages();  // ["OrderPage: cannot find symbol ..."]
 * </pre>
 */
public record BrokenFiles(List<String> files, List<String> messages) {

    public boolean ok() {
        return files.isEmpty();
    }

    public static BrokenFiles compile(Map<String, String> sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<JavaFileObject> units = new ArrayList<>();
        sources.forEach((name, src) -> units.add(new SimpleJavaFileObject(
                URI.create("string:///" + name.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return src;
            }
        }));
        String dir;
        try {
            dir = Files.createTempDirectory("brokenfiles").toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        compiler.getTask(null, null, diagnostics, List.of("-d", dir, "-proc:none",
                "-cp", System.getProperty("java.class.path")), null, units).call();
        TreeSet<String> files = new TreeSet<>();
        List<String> messages = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (d.getKind() != Diagnostic.Kind.ERROR) {
                continue;
            }
            String path = d.getSource() == null ? "?" : d.getSource().toUri().getPath();
            String file = path.substring(path.lastIndexOf('/') + 1).replace(".java", "");
            files.add(file);
            messages.add(file + ": " + d.getMessage(Locale.ENGLISH).lines().findFirst().orElse(""));
        }
        return new BrokenFiles(List.copyOf(files), messages);
    }
}
