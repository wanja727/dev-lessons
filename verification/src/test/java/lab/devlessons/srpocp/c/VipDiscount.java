package lab.devlessons.srpocp.c;

class VipDiscount
        implements DiscountPolicy {
    public boolean supports(Grade grade) {
        return grade == Grade.VIP;
    }
    public int apply(int price) {
        return price * 90 / 100;
    }
}
