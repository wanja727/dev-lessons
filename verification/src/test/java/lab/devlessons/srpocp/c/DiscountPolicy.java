package lab.devlessons.srpocp.c;

interface DiscountPolicy {
    boolean supports(Grade grade);
    int apply(int price);
}
