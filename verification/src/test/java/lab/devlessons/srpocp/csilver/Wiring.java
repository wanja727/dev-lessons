package lab.devlessons.srpocp.csilver;

import java.util.List;

/** 조립 코드. 프로그램이 시작할 때 한 번 실행된다고 본다 */
class Wiring {
    static DiscountCalculator calculator() {
        var policies = List.of(
            new VipDiscount(),
            new GoldDiscount(),
            new SilverDiscount());
        DiscountCalculator calculator = new DiscountCalculator();
        calculator.policies = policies;
        return calculator;
    }
}
