package lab.devlessons.hexagonal.domain;

/** 유스케이스 구현. 포트에만 기댄다 */
public class PlaceOrderService implements PlaceOrder {
    private final OrderStore store;
    private final PaymentPort payment;

    public PlaceOrderService(OrderStore store, PaymentPort payment) {
        this.store = store;
        this.payment = payment;
    }

    @Override
    public Order place(String orderId, long amount) {
        Order order = new Order(orderId, amount);
        if (payment.pay(orderId, amount)) {
            order.markPaid();
        }
        store.save(order);
        return order;
    }
}
