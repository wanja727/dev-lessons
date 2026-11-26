package lab.devlessons.srpocp.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 레슨 "단일 책임과 개방 폐쇄 원칙"의 "실무에서는". 코드 C의 조립 코드를 스프링이 대신한다.
 * DiscountCalculator는 생성자로 List<DiscountPolicy>를 받고, 스프링이 DiscountPolicy 빈을 모두 모아 넣는다.
 */
class SrpOcpSpringTest {

    private static final String LESSON = "srp-ocp";

    enum Grade { VIP, GOLD, SILVER, BASIC }

    interface DiscountPolicy {
        boolean supports(Grade grade);

        int apply(int price);
    }

    @Component
    static class VipDiscount implements DiscountPolicy {
        public boolean supports(Grade grade) {
            return grade == Grade.VIP;
        }

        public int apply(int price) {
            return price * 90 / 100;
        }
    }

    @Component
    static class GoldDiscount implements DiscountPolicy {
        public boolean supports(Grade grade) {
            return grade == Grade.GOLD;
        }

        public int apply(int price) {
            return price * 95 / 100;
        }
    }

    @Component
    static class SilverDiscount implements DiscountPolicy {
        public boolean supports(Grade grade) {
            return grade == Grade.SILVER;
        }

        public int apply(int price) {
            return price * 97 / 100;
        }
    }

    @Component
    static class DiscountCalculator {
        private final List<DiscountPolicy> policies;

        DiscountCalculator(List<DiscountPolicy> policies) {
            this.policies = policies;
        }

        int calculate(Grade grade, int price) {
            for (DiscountPolicy p : policies) {
                if (p.supports(grade)) {
                    return p.apply(price);
                }
            }
            return price;
        }
    }

    @Test
    @DisplayName("@Component를 붙인 SilverDiscount를 더하면 DiscountCalculator를 고치지 않아도 SILVER 10,000원은 9700")
    void springCollectsPolicies() {
        try (var ctx = new AnnotationConfigApplicationContext(VipDiscount.class, GoldDiscount.class,
                SilverDiscount.class, DiscountCalculator.class)) {
            DiscountCalculator calc = ctx.getBean(DiscountCalculator.class);
            List<String> names = calc.policies.stream().map(p -> p.getClass().getSimpleName()).sorted().toList();

            assertThat(calc.calculate(Grade.SILVER, 10_000)).isEqualTo(9700);
            assertThat(calc.calculate(Grade.VIP, 10_000)).isEqualTo(9000);
            assertThat(names).containsExactly("GoldDiscount", "SilverDiscount", "VipDiscount");
            CiOutput.record(LESSON, "spring-list", "스프링이 넣어 준 정책 → " + names
                    + "\nSILVER 10,000원 → " + calc.calculate(Grade.SILVER, 10_000)
                    + "\nVIP 10,000원    → " + calc.calculate(Grade.VIP, 10_000));
        }
    }
}
