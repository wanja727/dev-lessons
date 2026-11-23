package lab.devlessons.srpocp.c;

class GoldDiscount
        implements DiscountPolicy {
    public boolean supports(Grade grade) {
        return grade == Grade.GOLD;
    }
    public int apply(int price) {
        return price * 95 / 100;
    }
}
