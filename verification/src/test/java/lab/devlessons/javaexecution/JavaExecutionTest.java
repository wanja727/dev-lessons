package lab.devlessons.javaexecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import lab.devlessons.support.ChildJvm;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.JavaSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "자바 프로그램이 실행되는 과정"(J1)의 주장을 검증한다. 순수 JUnit 테스트.
 * 레슨의 OrderApp.java를 테스트 안에서 javac로 컴파일하고, 만든 .class를 읽고 실행한다.
 */
class JavaExecutionTest {

    private static final String LESSON = "java-execution";

    static final String ORDER_APP = """
            public class OrderApp {
              public static void main(String[] args) {
                int price = 12000;
                System.out.println("주문 " + price);
              }
            }
            """;

    @Test
    @DisplayName("자바 21로 컴파일한 클래스 파일은 CAFEBABE로 시작하고 주 버전이 65다")
    void classFileHeader() throws Exception {
        JavaSource.Result r = JavaSource.compile("OrderApp", ORDER_APP);
        assertThat(r.ok()).isTrue();

        byte[] bytes = Files.readAllBytes(r.classFile("OrderApp"));
        String head = HexFormat.of().withUpperCase().formatHex(bytes, 0, 8);
        int major = ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);

        assertThat(head).startsWith("CAFEBABE");
        assertThat(major).isEqualTo(65);
        CiOutput.record(LESSON, "class-header", """
                OrderApp.class의 처음 8바이트
                %s

                처음 4바이트: %s (매직 넘버)
                다음 2바이트: %s (부 버전)
                다음 2바이트: %s = %d (주 버전)""".formatted(
                head.replaceAll("(..)", "$1 ").strip(), head.substring(0, 8), head.substring(8, 12),
                head.substring(12, 16), major));
    }

    @Test
    @DisplayName("컴파일한 클래스를 읽어 main을 부르면 '주문 12000'이 출력된다")
    void runCompiledClass() throws Exception {
        JavaSource.Result r = JavaSource.compile("OrderApp", ORDER_APP);
        Class<?> app = r.load("OrderApp");
        Method main = app.getMethod("main", String[].class);

        String out = captureStdout(() -> main.invoke(null, (Object) new String[0]));

        assertThat(out.strip()).isEqualTo("주문 12000");
        CiOutput.record(LESSON, "run", "$ java OrderApp\n" + out.strip());
    }

    @Test
    @DisplayName("주 버전이 더 높은 클래스 파일은 UnsupportedClassVersionError로 읽지 않는다")
    void newerClassFileIsRejected() throws Exception {
        JavaSource.Result r = JavaSource.compile("OrderApp", ORDER_APP);
        byte[] bytes = Files.readAllBytes(r.classFile("OrderApp"));
        // 주 버전을 66(자바 22)으로 바꾼 클래스 파일을 자바 21의 JVM에 읽힌다.
        // 레슨의 "자바 21로 빌드하고 17에서 실행"과 같은 상황을 한 버전 위로 옮긴 것이다
        bytes[7] = 66;
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) {
                return defineClass(name, bytes, 0, bytes.length);
            }
        };

        assertThatThrownBy(() -> loader.loadClass("OrderApp"))
                .isInstanceOf(UnsupportedClassVersionError.class)
                .hasMessageContaining("class file version 66.0")
                .hasMessageContaining("up to 65.0");
        try {
            loader.loadClass("OrderApp");
        } catch (UnsupportedClassVersionError e) {
            CiOutput.record(LESSON, "unsupported-version", e.getClass().getName() + ": " + e.getMessage());
        }
    }

    static final List<String> INIT_LOG = new ArrayList<>();

    static class Coupon {
        static {
            INIT_LOG.add("Coupon 읽음");
        }
    }

    @Test
    @DisplayName("클래스는 처음 쓰이는 줄에서 초기화된다")
    void classIsInitializedOnFirstUse() {
        INIT_LOG.clear();
        INIT_LOG.add("시작");
        Coupon declaredOnly = null; // 변수 선언만으로는 초기화되지 않는다
        INIT_LOG.add("변수 선언 뒤");
        new Coupon();
        INIT_LOG.add("new 뒤");

        assertThat(declaredOnly).isNull();
        assertThat(INIT_LOG).containsExactly("시작", "변수 선언 뒤", "Coupon 읽음", "new 뒤");
        CiOutput.record(LESSON, "first-use", String.join("\n", INIT_LOG));
    }

    @Test
    @DisplayName("String의 클래스 로더는 null이고, 내 클래스는 시스템 클래스 로더 쪽에서 읽힌다")
    void classLoaders() {
        ClassLoader app = JavaExecutionTest.class.getClassLoader();

        assertThat(String.class.getClassLoader()).isNull();
        assertThat(app).isNotNull();
        // Gradle 테스트에서는 내 클래스를 읽는 로더가 시스템 로더 자신이거나 그 아래에 있다
        boolean underSystem = false;
        for (ClassLoader l = app; l != null; l = l.getParent()) {
            if (l == ClassLoader.getSystemClassLoader()) {
                underSystem = true;
            }
        }
        assertThat(underSystem).isTrue();
        assertThat(ClassLoader.getSystemClassLoader().getParent()).isEqualTo(ClassLoader.getPlatformClassLoader());

        CiOutput.record(LESSON, "loaders", """
                String.class.getClassLoader()
                → %s
                JavaExecutionTest.class.getClassLoader()
                → %s
                그 부모
                → %s""".formatted(String.class.getClassLoader(), app.getClass().getName(),
                app.getParent() == null ? "null" : app.getParent().getClass().getName()));
    }

    @Test
    @DisplayName("없는 이름으로 Class.forName을 부르면 ClassNotFoundException이 난다")
    void classNotFound() {
        assertThatThrownBy(() -> Class.forName("lab.shop.Coupon"))
                .isInstanceOf(ClassNotFoundException.class);
        try {
            Class.forName("lab.shop.Coupon");
        } catch (ClassNotFoundException e) {
            CiOutput.record(LESSON, "not-found", e.getClass().getName() + ": " + e.getMessage());
        }
    }

    /** 자식 JVM에서 같은 계산을 다섯 묶음으로 돌려 묶음마다 걸린 시간을 출력한다 */
    public static class Warmup {
        public static void main(String[] args) {
            long sink = 0;
            StringBuilder out = new StringBuilder();
            for (int batch = 1; batch <= 5; batch++) {
                long start = System.nanoTime();
                for (int i = 0; i < 20_000; i++) {
                    sink += discount(10_000 + i, i % 30);
                }
                long micros = (System.nanoTime() - start) / 1_000;
                out.append(batch).append(" ").append(micros).append("\n");
            }
            System.out.print(out);
            System.err.println(sink);
        }

        static long discount(int price, int rate) {
            long total = 0;
            for (int k = 0; k < 20; k++) {
                total += (long) price * (100 - rate) / 100 + k;
            }
            return total;
        }
    }

    @Test
    @DisplayName("같은 메서드를 묶음으로 부르면 시간이 줄어들고, 인터프리터만 쓰면(-Xint) 줄지 않는다")
    void jitWarmup() {
        ChildJvm.Run normal = ChildJvm.run(List.of(), Warmup.class);
        ChildJvm.Run interpretOnly = ChildJvm.run(List.of("-Xint"), Warmup.class);
        assertThat(normal.exitCode()).isZero();
        assertThat(interpretOnly.exitCode()).isZero();

        List<Long> fast = micros(normal.stdout());
        List<Long> slow = micros(interpretOnly.stdout());
        assertThat(fast).hasSize(5);
        assertThat(slow).hasSize(5);
        // 시간은 기계마다 달라서 값은 단정하지 않는다. 모양만 본다:
        // JIT가 있으면 마지막 묶음이 첫 묶음보다 빠르고, 인터프리터만 쓴 쪽의 마지막 묶음보다도 빠르다
        assertThat(fast.get(4)).isLessThan(fast.get(0));
        assertThat(fast.get(4)).isLessThan(slow.get(4));

        StringBuilder text = new StringBuilder("묶음  기본(JIT 켬)  -Xint(인터프리터만)\n");
        for (int i = 0; i < 5; i++) {
            text.append("%d     %,7d µs      %,7d µs".formatted(i + 1, fast.get(i), slow.get(i))).append('\n');
        }
        CiOutput.record(LESSON, "warmup", text.toString().strip());
    }

    private static List<Long> micros(String stdout) {
        return stdout.lines().filter(l -> !l.isBlank()).map(l -> Long.parseLong(l.split(" ")[1])).toList();
    }

    private interface Body {
        void run() throws Exception;
    }

    private static String captureStdout(Body body) throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            body.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
