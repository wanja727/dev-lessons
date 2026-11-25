package lab.devlessons.domainmodel;

public class CannotCancel extends RuntimeException {
    public CannotCancel(OrderStatus status) {
        super(status + " 상태의 주문은 취소할 수 없어요");
    }
}
