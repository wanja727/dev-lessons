package lab.devlessons.srpocp.csilver;

import java.util.List;

class DiscountCalculator {
    List<DiscountPolicy> policies;
    int calculate(Order order) {
        Grade grade = order.grade();
        int price = order.price();
        for (DiscountPolicy p : policies) {
            if (p.supports(grade)) {
                return p.apply(price);
            }
        }
        return price;
    }
}
