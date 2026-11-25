package lab.devlessons.unittest;

/** 3만 원 이상이면 배송비 무료. buggy는 경계값에서 틀린다 (> 와 >=) */
public final class ShippingFee {
    private ShippingFee() {
    }

    public static int fee(int price) {
        return price >= 30000 ? 0 : 3000;
    }

    public static int buggyFee(int price) {
        return price > 30000 ? 0 : 3000;
    }
}
