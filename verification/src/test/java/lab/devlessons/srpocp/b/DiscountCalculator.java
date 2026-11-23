package lab.devlessons.srpocp.b;

class DiscountCalculator {
    int calculate(Order order) {
        Grade grade = order.grade();
        int price = order.price();
        if (grade == Grade.VIP) {
            return price * 90 / 100;
        } else if (grade == Grade.GOLD) {
            return price * 95 / 100;
        }
        return price;
    }
}
