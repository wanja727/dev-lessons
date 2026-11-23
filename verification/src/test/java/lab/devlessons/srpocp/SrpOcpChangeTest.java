package lab.devlessons.srpocp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 레슨 "단일 책임과 개방 폐쇄 원칙"의 주장 가운데 "요구가 오면 어느 파일을 고치나"를 검증한다.
 *
 * 요구를 반영하기 전과 후의 코드를 패키지 둘에 나란히 두었다. 테스트는 두 패키지의 같은 이름 파일을 비교한다.
 *   b  → bsilver   코드 B에 SILVER 등급을 더한다
 *   c  → csilver   코드 C에 SILVER 등급을 더한다
 * 패키지 선언 줄은 패키지마다 달라서 빼고 비교한다. 테스트 파일(*Test.java)은 세지 않는다.
 *
 * 인터페이스를 바꿨을 때 고쳐야 하는 파일은 컴파일해서 확인한다. 코드 C의 파일을 읽어 일부만 바꾼 뒤
 * 자바 컴파일러(javax.tools.JavaCompiler)에 넣고, 오류가 난 파일을 본다.
 *
 * 반환값과 send() 호출 횟수는 패키지마다 있는 테스트(CodeATest, CodeBTest ...)가 확인한다.
 */
class SrpOcpChangeTest {

    private static final String LESSON = "srp-ocp";
    private static final Path ROOT = Path.of("src", "test", "java", "lab", "devlessons", "srpocp");

    /** 코드 named의 DiscountPolicy. name()을 더했다 */
    private static final String POLICY_WITH_NAME = """
            package lab.devlessons.srpocp.c;

            interface DiscountPolicy {
                boolean supports(Grade grade);
                int apply(int price);
                String name();
            }
            """;

    /** 코드 named의 VipDiscount. name()을 구현했다 */
    private static final String VIP_WITH_NAME = """
            package lab.devlessons.srpocp.c;

            class VipDiscount
                    implements DiscountPolicy {
                public boolean supports(Grade grade) {
                    return grade == Grade.VIP;
                }
                public int apply(int price) {
                    return price * 90 / 100;
                }
                public String name() {
                    return "VIP 할인";
                }
            }
            """;

    private static final String POLICY_WITH_DEFAULT_NAME = """
            package lab.devlessons.srpocp.c;

            interface DiscountPolicy {
                boolean supports(Grade grade);
                int apply(int price);
                default String name() {
                    return "할인";
                }
            }
            """;

    private static final String POLICY_RETURNING_LONG = """
            package lab.devlessons.srpocp.c;

            interface DiscountPolicy {
                boolean supports(Grade grade);
                long apply(int price);
            }
            """;

    private static final String POLICY_SUPPORTING_PRICE = """
            package lab.devlessons.srpocp.c;

            interface DiscountPolicy {
                boolean supports(int price);
                int apply(int price);
            }
            """;

    // ── 요구를 반영하기 전과 후의 파일 비교 ──

    @Test
    @DisplayName("코드 B에 SILVER를 더하면 Grade와 DiscountCalculator 두 파일이 바뀐다")
    void codeBChangesGradeAndCalculator() {
        Change change = Change.between("b", "bsilver");

        assertThat(change.changed).containsExactly("DiscountCalculator", "Grade");
        assertThat(change.added).isEmpty();
        assertThat(change.removed).isEmpty();
        assertThat(change.same).containsExactly("MailSender", "Order", "OrderService", "ReceiptWriter");
    }

    @Test
    @DisplayName("코드 C에 SILVER를 더하면 Grade와 조립 코드가 바뀌고 SilverDiscount가 새로 생긴다. 계산 코드는 바뀌지 않는다")
    void codeCChangesGradeAndWiringOnly() {
        Change b = Change.between("b", "bsilver");
        Change c = Change.between("c", "csilver");

        assertThat(c.changed).containsExactly("Grade", "Wiring");
        assertThat(c.added).containsExactly("SilverDiscount");
        assertThat(c.removed).isEmpty();
        assertThat(c.same).containsExactly(
                "DiscountCalculator", "DiscountPolicy", "GoldDiscount", "Order", "VipDiscount");

        CiOutput.record(LESSON, "files", "[코드 B에 SILVER 등급 더하기]\n"
                + "고친 파일      → " + String.join(", ", b.changed) + "\n"
                + "새로 만든 파일 → " + (b.added.isEmpty() ? "없음" : String.join(", ", b.added)) + "\n"
                + "그대로인 파일  → " + String.join(", ", b.same) + "\n"
                + "\n[코드 C에 SILVER 등급 더하기]  (Wiring이 조립 코드예요)\n"
                + "고친 파일      → " + String.join(", ", c.changed) + "\n"
                + "새로 만든 파일 → " + String.join(", ", c.added) + "\n"
                + "그대로인 파일  → " + String.join(", ", c.same));
    }

    @Test
    @DisplayName("calculate()를 고치지 않은 코드 B(bforgot)의 DiscountCalculator는 코드 B의 것과 같고, Grade는 SILVER를 더한 것과 같다")
    void forgotPackageIsCodeBWithNewGradeOnly() {
        assertThat(body("bforgot", "DiscountCalculator")).isEqualTo(body("b", "DiscountCalculator"));
        assertThat(body("bforgot", "Grade")).isEqualTo(body("bsilver", "Grade"));
    }

    // ── 인터페이스를 바꿨을 때의 컴파일 오류 ──

    @Test
    @DisplayName("코드 C는 그대로 컴파일된다")
    void codeCCompiles(@TempDir Path out) {
        assertThat(compile(sources("c"), out)).isEmpty();
    }

    @Test
    @DisplayName("코드 named: DiscountPolicy에 name()을 더하고 VipDiscount만 고치면 GoldDiscount에서 컴파일 오류가 난다")
    void addingMethodBreaksImplementationsLeftBehind(@TempDir Path out) {
        Map<String, String> sources = sources("c");
        sources.put("DiscountPolicy", POLICY_WITH_NAME);
        sources.put("VipDiscount", VIP_WITH_NAME);

        List<Diagnostic<? extends JavaFileObject>> errors = compile(sources, out);

        assertThat(errors).hasSize(1);
        assertThat(fileOf(errors.get(0))).isEqualTo("GoldDiscount");
        assertThat(errors.get(0).getMessage(Locale.ENGLISH))
                .contains("GoldDiscount is not abstract and does not override abstract method name() in DiscountPolicy");

        CiOutput.record(LESSON, "named", "오류가 난 파일: " + fileOf(errors.get(0)) + ".java\n"
                + "error: " + errors.get(0).getMessage(Locale.ENGLISH));
    }

    @Test
    @DisplayName("name()을 인터페이스에만 더하면 VipDiscount와 GoldDiscount에서 오류가 하나씩 난다. 고칠 파일은 인터페이스까지 3개다")
    void addingMethodBreaksEveryImplementation(@TempDir Path out) {
        Map<String, String> sources = sources("c");
        sources.put("DiscountPolicy", POLICY_WITH_NAME);

        List<Diagnostic<? extends JavaFileObject>> errors = compile(sources, out);

        assertThat(errors).hasSize(2);
        assertThat(filesOf(errors)).containsExactly("GoldDiscount", "VipDiscount");
        // 고칠 파일: 메서드를 더한 인터페이스 1개와 오류가 난 구현 2개
        assertThat(1 + filesOf(errors).size()).isEqualTo(3);
    }

    @Test
    @DisplayName("name()을 default 메서드로 더하면 구현 클래스를 고치지 않아도 컴파일된다")
    void defaultMethodBreaksNothing(@TempDir Path out) {
        Map<String, String> sources = sources("c");
        sources.put("DiscountPolicy", POLICY_WITH_DEFAULT_NAME);

        assertThat(compile(sources, out)).isEmpty();
    }

    @Test
    @DisplayName("apply()가 돌려주는 타입을 long으로 바꾸면 구현 클래스 둘과 DiscountCalculator에서 컴파일 오류가 난다")
    void changingReturnTypeBreaksCallerToo(@TempDir Path out) {
        Map<String, String> sources = sources("c");
        sources.put("DiscountPolicy", POLICY_RETURNING_LONG);

        assertThat(filesOf(compile(sources, out)))
                .containsExactly("DiscountCalculator", "GoldDiscount", "VipDiscount");
    }

    @Test
    @DisplayName("supports()가 주문 금액(int)을 받게 바꾸면 구현 클래스 둘과 DiscountCalculator에서 컴파일 오류가 난다")
    void changingParameterBreaksCallerToo(@TempDir Path out) {
        Map<String, String> sources = sources("c");
        sources.put("DiscountPolicy", POLICY_SUPPORTING_PRICE);

        assertThat(filesOf(compile(sources, out)))
                .containsExactly("DiscountCalculator", "GoldDiscount", "VipDiscount");
    }

    @Test
    @DisplayName("테스트가 쓰는 named 코드는 코드 C의 파일에 name()만 더한 것이다")
    void namedSourcesDifferFromCodeCOnlyByName() {
        assertThat(withoutPackage(POLICY_WITH_NAME).replace("    String name();\n", ""))
                .isEqualTo(body("c", "DiscountPolicy"));
        assertThat(withoutPackage(VIP_WITH_NAME)
                .replace("    public String name() {\n        return \"VIP 할인\";\n    }\n", ""))
                .isEqualTo(body("c", "VipDiscount"));
    }

    // ── 도구 ──

    /** 패키지 둘의 같은 이름 파일을 비교한 결과. 이름은 가나다순이고 .java는 뗐다 */
    record Change(List<String> changed, List<String> same, List<String> added, List<String> removed) {

        static Change between(String before, String after) {
            Map<String, String> old = sources(before);
            Map<String, String> now = sources(after);
            Set<String> names = new TreeSet<>(old.keySet());
            names.retainAll(now.keySet());
            return new Change(
                    names.stream().filter(n -> !withoutPackage(old.get(n)).equals(withoutPackage(now.get(n)))).toList(),
                    names.stream().filter(n -> withoutPackage(old.get(n)).equals(withoutPackage(now.get(n)))).toList(),
                    now.keySet().stream().filter(n -> !old.containsKey(n)).toList(),
                    old.keySet().stream().filter(n -> !now.containsKey(n)).toList());
        }
    }

    /** 패키지의 소스 파일을 이름(.java를 뗀 것)과 내용으로 읽는다. 테스트 파일은 뺀다 */
    private static Map<String, String> sources(String pkg) {
        try (Stream<Path> files = Files.list(ROOT.resolve(pkg))) {
            Map<String, String> sources = new TreeMap<>();
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith(".java") && !name.endsWith("Test.java")) {
                    sources.put(name.substring(0, name.length() - ".java".length()), read(file));
                }
            }
            assertThat(sources).as("패키지 " + pkg + "의 소스 파일. verification 폴더에서 돌려야 한다").isNotEmpty();
            return sources;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String body(String pkg, String name) {
        return withoutPackage(sources(pkg).get(name));
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file).replace("\r\n", "\n");
    }

    /** 패키지 선언 줄을 뺀 내용. 그 줄은 패키지마다 달라서 비교하지 않는다 */
    private static String withoutPackage(String source) {
        return source.lines().filter(line -> !line.startsWith("package ")).collect(Collectors.joining("\n")).strip();
    }

    /**
     * 소스 여럿을 함께 컴파일하고 컴파일러가 낸 오류를 돌려준다.
     * 레슨의 코드처럼 패키지 선언 없이 컴파일한다. 그래야 오류 문장에 패키지 이름이 붙지 않는다
     */
    private static List<Diagnostic<? extends JavaFileObject>> compile(Map<String, String> sources, Path out) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("JDK로 돌려야 한다 (JRE에는 컴파일러가 없다)").isNotNull();
        List<JavaFileObject> files = new LinkedHashMap<>(sources).entrySet().stream()
                .<JavaFileObject>map(e -> new SimpleJavaFileObject(
                        URI.create("string:///" + e.getKey() + ".java"), JavaFileObject.Kind.SOURCE) {
                    @Override
                    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                        return withoutPackage(e.getValue());
                    }
                })
                .toList();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        compiler.getTask(null, null, diagnostics, List.of("-d", out.toString(), "-proc:none"), null, files).call();
        return diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .toList();
    }

    /** 오류가 난 파일의 이름 (.java를 뗀 것) */
    private static String fileOf(Diagnostic<? extends JavaFileObject> error) {
        String name = error.getSource().getName();
        return name.substring(name.lastIndexOf('/') + 1).replace(".java", "");
    }

    /** 오류가 난 파일들. 가나다순, 겹치는 것은 하나로 */
    private static Set<String> filesOf(List<Diagnostic<? extends JavaFileObject>> errors) {
        return errors.stream().map(SrpOcpChangeTest::fileOf).collect(Collectors.toCollection(TreeSet::new));
    }
}
