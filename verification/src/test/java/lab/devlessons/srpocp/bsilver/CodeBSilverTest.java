package lab.devlessons.srpocp.bsilver;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 코드 B에 SILVER 등급 3% 할인을 더한 뒤. Grade에 SILVER를, calculate()에 else if를 더했다 */
class CodeBSilverTest {

    @Test
    @DisplayName("코드 B: calculate()에 else if를 더하면 SILVER 10,000원 주문은 9700이 나온다")
    void silverGetsThreePercent() {
        DiscountCalculator calculator = new DiscountCalculator();

        assertThat(calculator.calculate(new Order(Grade.SILVER, 10_000, "choi@a.com"))).isEqualTo(9700);
        assertThat(calculator.calculate(new Order(Grade.VIP, 10_000, "kim@a.com"))).isEqualTo(9000);
        assertThat(calculator.calculate(new Order(Grade.GOLD, 10_000, "lee@a.com"))).isEqualTo(9500);
        assertThat(calculator.calculate(new Order(Grade.BASIC, 10_000, "park@a.com"))).isEqualTo(10_000);
    }
}
