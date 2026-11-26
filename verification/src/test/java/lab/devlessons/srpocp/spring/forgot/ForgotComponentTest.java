package lab.devlessons.srpocp.spring.forgot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 레슨 "단일 책임과 개방 폐쇄 원칙"의 "실무에서는"의 흔한 실수. SilverDiscount 클래스를 만들었지만 @Component를 빠뜨렸다.
 * 이 패키지를 컴포넌트 스캔해서, 스프링이 SilverDiscount를 목록에 넣지 않는 것을 본다.
 */
class ForgotComponentTest {

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

    // @Component를 빠뜨렸다
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
        final List<DiscountPolicy> policies;

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
    @DisplayName("컴포넌트 스캔에서 @Component를 빠뜨린 SilverDiscount는 빠지고, SILVER 10,000원은 오류 없이 10000")
    void forgotComponent() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.scan("lab.devlessons.srpocp.spring.forgot");
            ctx.refresh();
            DiscountCalculator calc = ctx.getBean(DiscountCalculator.class);
            List<String> names = calc.policies.stream().map(p -> p.getClass().getSimpleName()).sorted().toList();

            assertThat(names).containsExactly("GoldDiscount", "VipDiscount");
            assertThat(calc.calculate(Grade.SILVER, 10_000)).isEqualTo(10_000);
            CiOutput.record("srp-ocp", "spring-forgot", "@Component를 빠뜨린 SilverDiscount\n→ 스프링이 넣어 준 정책 " + names
                    + "\n→ SILVER 10,000원 → " + calc.calculate(Grade.SILVER, 10_000) + " (오류 없음)");
        }
    }
}
