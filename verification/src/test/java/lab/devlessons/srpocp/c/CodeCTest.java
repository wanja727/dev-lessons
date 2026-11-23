package lab.devlessons.srpocp.c;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "단일 책임과 개방 폐쇄 원칙"의 코드 C. 등급마다 다른 계산을 DiscountPolicy 인터페이스로 뺐다.
 * 이 패키지의 클래스는 레슨의 코드 C와 같은 모양이고, 조립 코드는 Wiring에 있다.
 */
class CodeCTest {

    @Test
    @DisplayName("코드 C: 10,000원 주문은 코드 B와 같이 VIP 9000, GOLD 9500, BASIC 10000이다")
    void calculateReturnsSameAsCodeB() {
        DiscountCalculator calculator = Wiring.calculator();

        assertThat(calculator.calculate(new Order(Grade.VIP, 10_000, "kim@a.com"))).isEqualTo(9000);
        assertThat(calculator.calculate(new Order(Grade.GOLD, 10_000, "lee@a.com"))).isEqualTo(9500);
        assertThat(calculator.calculate(new Order(Grade.BASIC, 10_000, "park@a.com"))).isEqualTo(10_000);
    }
}
