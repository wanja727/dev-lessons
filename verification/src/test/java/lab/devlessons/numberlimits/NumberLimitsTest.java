package lab.devlessons.numberlimits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "숫자 타입의 한계"(J19)의 주장을 검증한다. 순수 JUnit 테스트와 Jackson.
 * 엔티티의 금액 열은 NumberLimitsJpaTest가 본다.
 */
class NumberLimitsTest {

    private static final String LESSON = "number-limits";

    @Test
    @DisplayName("Integer.MAX_VALUE + 1은 -2147483648이다")
    void overflow() {
        int max = Integer.MAX_VALUE;

        assertThat(max + 1).isEqualTo(Integer.MIN_VALUE);
        CiOutput.record(LESSON, "predict", max + "\n" + (max + 1));
    }

    @Test
    @DisplayName("int끼리의 곱은 long에 담아도 int로 계산되어 넘친다. L을 붙이거나 Duration을 쓰면 맞다")
    void expireMillis() {
        long expireMs = 30 * 24 * 60 * 60 * 1000;
        long fixed = 30L * 24 * 60 * 60 * 1000;
        long viaDuration = Duration.ofDays(30).toMillis();
        // 컴파일러가 상수로 계산하는 경우와 별개로, 변수로 받아도 같은지 본다
        int days = 30;
        long fromVariable = days * 24 * 60 * 60 * 1000;

        assertThat(expireMs).isEqualTo(-1702967296L);
        assertThat(fromVariable).isEqualTo(-1702967296L);
        assertThat(fixed).isEqualTo(2_592_000_000L);
        assertThat(viaDuration).isEqualTo(2_592_000_000L);
        CiOutput.record(LESSON, "expire", expireMs + "\n" + fixed);
    }

    @Test
    @DisplayName("Math.addExact는 범위를 넘으면 ArithmeticException, 정수를 0으로 나눠도 같다")
    void exactAndDivide() {
        int zero = 0;
        assertThatThrownBy(() -> Math.addExact(Integer.MAX_VALUE, 1)).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> {
            int x = 10 / zero;
        }).isInstanceOf(ArithmeticException.class);

        int price = 990;
        int rate = 10;
        assertThat(price / 100 * rate).isEqualTo(90);
        assertThat(price * rate / 100).isEqualTo(99);
        assertThat(10 / 4).isEqualTo(2);

        String message = null;
        try {
            Math.addExact(Integer.MAX_VALUE, 1);
        } catch (ArithmeticException e) {
            message = e.getMessage();
        }
        CiOutput.record(LESSON, "exact", """
                Math.addExact(Integer.MAX_VALUE, 1)
                → ArithmeticException: %s

                990 / 100 * 10 → %d
                990 * 10 / 100 → %d""".formatted(message, price / 100 * rate, price * rate / 100));
    }

    @Test
    @DisplayName("0.1 + 0.2는 0.30000000000000004이고, 0.1을 열 번 더한 값은 1.0과 ==가 false다")
    void doubleError() {
        double total = 0;
        for (int i = 0; i < 10; i++) {
            total += 0.1;
        }

        assertThat(0.1 + 0.2).isEqualTo(0.30000000000000004);
        assertThat(0.1 + 0.2 == 0.3).isFalse();
        assertThat(total == 1.0).isFalse();
        assertThat(0.5 + 0.25 == 0.75).isTrue();
        CiOutput.record(LESSON, "double", (0.1 + 0.2) + "\n" + (0.1 + 0.2 == 0.3) + "\n" + (total == 1.0)
                + "\n\n(0.1을 열 번 더한 값: " + total + ")");
    }

    @Test
    @DisplayName("double을 0으로 나누면 예외가 아니라 Infinity, 0.0/0.0은 NaN이고 NaN은 자신과 ==가 false다")
    void doubleSpecialValues() {
        double zero = 0.0;
        double nan = zero / zero;

        assertThat(1.0 / zero).isEqualTo(Double.POSITIVE_INFINITY);
        assertThat(nan == nan).isFalse();
    }

    @Test
    @DisplayName("문자열이나 valueOf로 만든 BigDecimal은 0.1이고, new BigDecimal(0.1)은 double의 오차를 그대로 옮긴다")
    void bigDecimalCreation() {
        BigDecimal fromString = new BigDecimal("0.1");
        BigDecimal fromValueOf = BigDecimal.valueOf(0.1);
        BigDecimal fromDouble = new BigDecimal(0.1);

        assertThat(fromString.toString()).isEqualTo("0.1");
        assertThat(fromValueOf.toString()).isEqualTo("0.1");
        assertThat(fromDouble.toString()).startsWith("0.1000000000000000055");
        assertThat(new BigDecimal("0.1").add(new BigDecimal("0.2"))).isEqualByComparingTo("0.3");
        assertThat(new BigDecimal(0.1).add(new BigDecimal(0.2)).compareTo(new BigDecimal("0.3"))).isNotZero();

        CiOutput.record(LESSON, "create", """
                new BigDecimal("0.1")  → %s
                BigDecimal.valueOf(0.1) → %s
                new BigDecimal(0.1)    → %s""".formatted(fromString, fromValueOf, fromDouble));
        CiOutput.record(LESSON, "sum-compare", """
                double: 0.1 + 0.2
                → %s

                new BigDecimal(0.1) + new BigDecimal(0.2)
                → %s

                new BigDecimal("0.1") + new BigDecimal("0.2")
                → %s""".formatted(0.1 + 0.2, new BigDecimal(0.1).add(new BigDecimal(0.2)),
                new BigDecimal("0.1").add(new BigDecimal("0.2"))));
    }

    @Test
    @DisplayName("나누어떨어지지 않는 divide는 자릿수 없이 ArithmeticException, 자릿수와 반올림을 주면 3333")
    void bigDecimalDivide() {
        BigDecimal price = new BigDecimal("10000");
        BigDecimal three = new BigDecimal("3");

        assertThatThrownBy(() -> price.divide(three)).isInstanceOf(ArithmeticException.class);
        assertThat(price.divide(three, 0, RoundingMode.DOWN)).isEqualByComparingTo("3333");

        String message = null;
        try {
            price.divide(three);
        } catch (ArithmeticException e) {
            message = e.getMessage();
        }
        CiOutput.record(LESSON, "divide", """
                price.divide(three)
                → ArithmeticException: %s

                price.divide(three, 0, RoundingMode.DOWN)
                → %s""".formatted(message, price.divide(three, 0, RoundingMode.DOWN)));
    }

    @Test
    @DisplayName("BigDecimal은 불변이다. add의 결과를 받지 않으면 원래 값은 그대로다")
    void bigDecimalImmutable() {
        BigDecimal total = new BigDecimal("1000");
        total.add(new BigDecimal("500"));

        assertThat(total).isEqualByComparingTo("1000");
        total = total.add(new BigDecimal("500"));
        assertThat(total).isEqualByComparingTo("1500");
    }

    @Test
    @DisplayName("2.0과 2.00은 equals가 false, compareTo가 0이다. HashSet은 둘, TreeSet은 하나로 담는다")
    void bigDecimalEquality() {
        BigDecimal a = new BigDecimal("2.0");
        BigDecimal b = new BigDecimal("2.00");
        Set<BigDecimal> hash = new HashSet<>(Set.of(a, b));
        Set<BigDecimal> tree = new TreeSet<>(Set.of(a, b));
        BigDecimal product = new BigDecimal("1.5").multiply(new BigDecimal("2"));

        assertThat(a.equals(b)).isFalse();
        assertThat(a.compareTo(b)).isZero();
        assertThat(hash).hasSize(2);
        assertThat(tree).hasSize(1);
        assertThat(product.toString()).isEqualTo("3.0");
        assertThat(product.equals(new BigDecimal("3"))).isFalse();
        CiOutput.record(LESSON, "scale", """
                a.equals(b)          → %s
                a.compareTo(b) == 0  → %s

                HashSet에 둘 넣은 크기 → %d
                TreeSet에 둘 넣은 크기 → %d""".formatted(a.equals(b), a.compareTo(b) == 0, hash.size(), tree.size()));
    }

    record PlainAmount(BigDecimal amount, long id) {
    }

    record StringAmount(@JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
                        @JsonFormat(shape = JsonFormat.Shape.STRING) long id) {
    }

    @Test
    @DisplayName("Jackson은 BigDecimal을 JSON 숫자로 쓰고, @JsonFormat(shape = STRING)이면 문자열로 쓴다")
    void jacksonShape() throws Exception {
        ObjectMapper json = new ObjectMapper();
        String plain = json.writeValueAsString(new PlainAmount(new BigDecimal("12345.67"), 9007199254740993L));
        String asString = json.writeValueAsString(new StringAmount(new BigDecimal("12345.67"), 9007199254740993L));

        assertThat(plain).isEqualTo("{\"amount\":12345.67,\"id\":9007199254740993}");
        assertThat(asString).isEqualTo("{\"amount\":\"12345.67\",\"id\":\"9007199254740993\"}");
        CiOutput.record(LESSON, "json", "기본\n" + plain + "\n\n@JsonFormat(shape = STRING)\n" + asString);
    }
}
