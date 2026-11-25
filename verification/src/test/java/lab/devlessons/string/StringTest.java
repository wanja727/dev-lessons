package lab.devlessons.string;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 레슨 "String"(J4)의 주장을 검증한다. 순수 JUnit 테스트와 SLF4J(Logback).
 */
class StringTest {

    private static final String LESSON = "string";

    @Test
    @DisplayName("코드 predict: 같은 리터럴은 같은 객체, new String은 새 객체, equals는 글자를 비교한다")
    void predict() {
        String a = "PAID";
        String b = "PAID";
        String c = new String("PAID");

        assertThat(a == b).isTrue();
        assertThat(a == c).isFalse();
        assertThat(a.equals(c)).isTrue();
        assertThat(a == c.intern()).isTrue();
        CiOutput.record(LESSON, "predict", (a == b) + "\n" + (a == c) + "\n" + a.equals(c));
        CiOutput.record(LESSON, "intern", "a == c.intern() → " + (a == c.intern()));
    }

    static class A {
        static String x = "KRW";
    }

    static class B {
        static String y = "KRW";
    }

    @Test
    @DisplayName("다른 클래스에 쓴 같은 리터럴도 같은 객체다")
    void literalAcrossClasses() {
        assertThat(A.x == B.y).isTrue();
    }

    @Test
    @DisplayName("String의 메서드는 새 객체를 돌려준다. 받지 않으면 원래 문자열은 그대로다")
    void immutable() {
        String s = "paid";
        s.toUpperCase();
        String kept = s;

        String t0 = "A";
        String t = t0;
        t0 = t0 + "B";

        assertThat(s).isEqualTo("paid");
        assertThat(kept).isSameAs(s);
        assertThat(t).isEqualTo("A");
        assertThat(t0).isEqualTo("AB");
        CiOutput.record(LESSON, "immutable", """
                String s = "paid";
                s.toUpperCase();
                s → %s

                String s = "A"; String t = s;
                s = s + "B";
                s → %s
                t → %s""".formatted(s, t0, t));
    }

    static String join(String left, String right) {
        return left + right;
    }

    @Test
    @DisplayName("변수를 이어 붙인 문자열은 리터럴과 ==가 false, 리터럴끼리나 final 변수를 이어 붙인 것은 true")
    void runtimeConcat() {
        String a = "PAID";
        String half = "PA";
        final String finalHalf = "PA";
        String d = half + "ID";
        String e = "PA" + "ID";
        String f = finalHalf + "ID";
        String g = join("PA", "ID");

        assertThat(a == d).isFalse();
        assertThat(a == e).isTrue();
        assertThat(a == f).isTrue();
        assertThat(a == g).isFalse();
        assertThat(a.equals(d)).isTrue();
        CiOutput.record(LESSON, "runtime", """
                a == d   (half + "ID")       → %s
                a == e   ("PA" + "ID")       → %s
                a == f   (final half + "ID") → %s
                a.equals(d)                  → %s""".formatted(a == d, a == e, a == f, a.equals(d)));
    }

    @Test
    @DisplayName("toUpperCase의 결과와 바이트에서 만든 문자열은 리터럴과 ==가 false, equals가 true")
    void readAtRuntime() {
        String a = "PAID";
        String upper = "paid".toUpperCase();
        String fromBytes = new String("PAID".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        assertThat(a == upper).isFalse();
        assertThat(a.equals(upper)).isTrue();
        assertThat(a == fromBytes).isFalse();
        assertThat(a.equals(fromBytes)).isTrue();
        CiOutput.record(LESSON, "request", """
                // 요청 본문의 바이트에서 읽은 "PAID"
                status == "PAID"       → %s
                "PAID".equals(status)  → %s""".formatted(fromBytes == "PAID", "PAID".equals(fromBytes)));
    }

    @Test
    @DisplayName("StringBuilder.append는 자기 자신을 돌려주고, 같은 객체를 가리키는 변수에서도 덧붙인 것이 보인다")
    void builderIsMutable() {
        StringBuilder sb = new StringBuilder("A");
        StringBuilder sb2 = sb;

        assertThat(sb.append("B")).isSameAs(sb);
        assertThat(sb2.toString()).isEqualTo("AB");
    }

    static String withPlus(List<String> ids) {
        String csv = "";
        for (String id : ids) {
            csv += id + ",";
        }
        return csv;
    }

    static String withBuilder(List<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            sb.append(id).append(',');
        }
        return sb.toString();
    }

    @Test
    @DisplayName("반복문의 += 누적은 원소 수가 늘수록 StringBuilder보다 훨씬 느려진다")
    void concatInLoop() {
        StringBuilder text = new StringBuilder("원소 수     +=          StringBuilder\n");
        long lastPlus = 0;
        long lastBuilder = 0;
        for (int n : new int[] {1_000, 10_000, 50_000}) {
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                ids.add("order-" + i);
            }
            // 한 번씩 돌려 JIT를 데운다
            withPlus(ids.subList(0, Math.min(n, 1000)));
            withBuilder(ids);

            long t0 = System.nanoTime();
            String a = withPlus(ids);
            long plus = (System.nanoTime() - t0) / 1_000_000;
            long t1 = System.nanoTime();
            String b = withBuilder(ids);
            long builder = Math.max(0, (System.nanoTime() - t1) / 1_000_000);

            assertThat(a).isEqualTo(b);
            text.append("%,-10d %,7d ms     %s".formatted(n, plus,
                    builder == 0 ? "1 ms 미만" : "%,d ms".formatted(builder))).append('\n');
            lastPlus = plus;
            lastBuilder = builder;
        }
        // 값은 기계마다 달라서 모양만 본다: 가장 큰 입력에서 +=가 더 오래 걸린다
        assertThat(lastPlus).isGreaterThan(lastBuilder);
        CiOutput.record(LESSON, "loop", text.toString().strip());
    }

    static class Order {
        static int toStringCalls = 0;

        @Override
        public String toString() {
            toStringCalls++;
            return "Order#1";
        }
    }

    @Test
    @DisplayName("로그 수준이 INFO일 때 log.debug(\"주문 \" + order)는 toString을 부르고 {}는 부르지 않는다")
    void loggingPlaceholder() {
        Logger log = LoggerFactory.getLogger("lab.devlessons.string.OrderService");
        ((ch.qos.logback.classic.Logger) log).setLevel(Level.INFO);
        Order order = new Order();

        Order.toStringCalls = 0;
        log.debug("주문 " + order);
        int withPlus = Order.toStringCalls;

        Order.toStringCalls = 0;
        log.debug("주문 {}", order);
        int withPlaceholder = Order.toStringCalls;

        assertThat(log.isDebugEnabled()).isFalse();
        assertThat(withPlus).isEqualTo(1);
        assertThat(withPlaceholder).isZero();
        CiOutput.record(LESSON, "logging", """
                로그 수준: INFO (debug는 남지 않는다)

                log.debug("주문 " + order)
                → order.toString() 호출 %d번

                log.debug("주문 {}", order)
                → order.toString() 호출 %d번""".formatted(withPlus, withPlaceholder));
    }
}
