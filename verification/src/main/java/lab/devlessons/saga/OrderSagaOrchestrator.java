package lab.devlessons.saga;

import lab.devlessons.saga.SagaServices.OutOfStockException;
import lab.devlessons.saga.SagaServices.SagaOrderService;
import lab.devlessons.saga.SagaServices.SagaPaymentService;
import lab.devlessons.saga.SagaServices.SagaStockService;
import org.springframework.stereotype.Component;

/**
 * 오케스트레이션 사가: 지휘자 하나가 순서를 정해 각 서비스를 부르고, 실패하면 앞 단계의 보상을 역순으로 부른다.
 * 이 클래스에는 @Transactional이 없다. 단계마다 각 서비스가 따로 커밋한다.
 */
@Component
public class OrderSagaOrchestrator {

    private final SagaOrderService orders;
    private final SagaPaymentService payments;
    private final SagaStockService stock;
    /** 테스트에서 "결제 뒤, 재고 차감 전"을 재현하려고 끼워 넣는 지점 */
    private volatile Runnable afterPayment = () -> { };

    public OrderSagaOrchestrator(SagaOrderService orders, SagaPaymentService payments, SagaStockService stock) {
        this.orders = orders;
        this.payments = payments;
        this.stock = stock;
    }

    public void setAfterPayment(Runnable afterPayment) {
        this.afterPayment = afterPayment;
    }

    /** 주문 id를 돌려준다. 성공이면 CONFIRMED, 재고가 없으면 보상 후 CANCELLED */
    public long placeOrder(String item, long amount) {
        long orderId = orders.create(item, amount);
        payments.charge(orderId, amount);
        afterPayment.run();
        try {
            stock.reserve(item);
        } catch (OutOfStockException e) {
            // 보상은 성공한 단계를 역순으로
            payments.refund(orderId);
            orders.cancel(orderId);
            return orderId;
        }
        orders.confirm(orderId);
        return orderId;
    }
}
