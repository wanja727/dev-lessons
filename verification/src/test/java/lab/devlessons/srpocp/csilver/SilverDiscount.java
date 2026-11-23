package lab.devlessons.srpocp.csilver;

class SilverDiscount
        implements DiscountPolicy {
    public boolean supports(Grade grade) {
        return grade == Grade.SILVER;
    }
    public int apply(int price) {
        return price * 97 / 100;
    }
}
