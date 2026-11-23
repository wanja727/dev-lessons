package lab.devlessons.srpocp.csilver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 코드 C에 SILVER 등급 3% 할인을 더한 뒤. SilverDiscount를 새로 만들고 조립 코드(Wiring)의 목록에 넣었다 */
class CodeCSilverTest {

    private static final Order SILVER_ORDER = new Order(Grade.SILVER, 10_000, "choi@a.com");

    @Test
    @DisplayName("코드 C: SilverDiscount를 목록에 넣지 않으면 SILVER 10,000원 주문은 10000, 넣으면 9700이 나온다")
    void silverNeedsToBeInTheList() {
        // SilverDiscount 클래스는 있지만 목록은 고치기 전 그대로다
        DiscountCalculator notListed = new DiscountCalculator();
        notListed.policies = List.of(
            new VipDiscount(),
            new GoldDiscount());
        DiscountCalculator listed = Wiring.calculator();

        int without = notListed.calculate(SILVER_ORDER);
        int with = listed.calculate(SILVER_ORDER);

        assertThat(without).isEqualTo(10_000);
        assertThat(with).isEqualTo(9700);

        CiOutput.record("srp-ocp", "c-list", "[코드 C] SilverDiscount 클래스를 새로 만들었어요\n"
                + "목록에 넣지 않았을 때 calculate(SILVER 10,000원 주문)  → " + without + "  (예외 없음)\n"
                + "목록에 넣었을 때 calculate(SILVER 10,000원 주문)       → " + with);
    }

    @Test
    @DisplayName("코드 C: SILVER를 더한 뒤에도 VIP 9000, GOLD 9500, BASIC 10000은 그대로다")
    void otherGradesAreUnchanged() {
        DiscountCalculator calculator = Wiring.calculator();

        assertThat(calculator.calculate(new Order(Grade.VIP, 10_000, "kim@a.com"))).isEqualTo(9000);
        assertThat(calculator.calculate(new Order(Grade.GOLD, 10_000, "lee@a.com"))).isEqualTo(9500);
        assertThat(calculator.calculate(new Order(Grade.BASIC, 10_000, "park@a.com"))).isEqualTo(10_000);
    }
}
