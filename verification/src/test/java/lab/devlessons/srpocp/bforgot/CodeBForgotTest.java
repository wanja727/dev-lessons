package lab.devlessons.srpocp.bforgot;

import static org.assertj.core.api.Assertions.assertThat;

import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 코드 B에서 Grade에 SILVER만 더하고 calculate()는 고치지 않은 것.
 * DiscountCalculator가 코드 B의 것과 같은 파일인지는 SrpOcpChangeTest가 확인한다.
 */
class CodeBForgotTest {

    @Test
    @DisplayName("코드 B: Grade에 SILVER만 더하고 calculate()를 고치지 않으면 SILVER 10,000원 주문은 10000이 나온다")
    void silverGetsNoDiscountWithoutError() {
        DiscountCalculator calculator = new DiscountCalculator();

        int silver = calculator.calculate(new Order(Grade.SILVER, 10_000, "choi@a.com"));
        int gold = calculator.calculate(new Order(Grade.GOLD, 10_000, "lee@a.com"));

        assertThat(silver).isEqualTo(10_000);
        assertThat(gold).isEqualTo(9500);

        CiOutput.record("srp-ocp", "b-forgot", "[코드 B] Grade에 SILVER만 더하고 calculate()는 고치지 않았어요\n"
                + "calculate(GOLD 10,000원 주문)    → " + gold + "\n"
                + "calculate(SILVER 10,000원 주문)  → " + silver + "  (예외 없음)");
    }
}
