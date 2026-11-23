package lab.devlessons.srpocp.csilver;

interface DiscountPolicy {
    boolean supports(Grade grade);
    int apply(int price);
}
