package lab.devlessons.saga;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;
import lab.devlessons.saga.SagaServices.OutOfStockException;
import lab.devlessons.saga.SagaServices.SagaOrderService;
import lab.devlessons.saga.SagaServices.SagaPaymentService;
import lab.devlessons.saga.SagaServices.SagaStockService;
import org.springframework.stereotype.Component;

/**
 * 코레오그래피 사가: 지휘자 없이, 각 서비스가 이벤트를 듣고 자기 일을 한 뒤 다음 이벤트를 낸다.
 * 메시지 브로커 대신 같은 스레드에서 바로 전달하는 단순한 이벤트 버스를 쓴다.
 */
@Component
public class ChoreographySaga {

    private final Map<String, List<LongConsumer>> handlers = new HashMap<>();
    private final List<String> events = new ArrayList<>();
    private final SagaOrderService orders;

    public ChoreographySaga(SagaOrderService orders, SagaPaymentService payments, SagaStockService stock) {
        this.orders = orders;
        // 결제 서비스가 듣는 이벤트
        on("OrderCreated", orderId -> {
            payments.charge(orderId, amountOf(orderId));
            publish("PaymentCompleted", orderId);
        });
        on("StockFailed", orderId -> {
            payments.refund(orderId);
            publish("PaymentRefunded", orderId);
        });
        // 재고 서비스가 듣는 이벤트
        on("PaymentCompleted", orderId -> {
            try {
                stock.reserve(itemOf(orderId));
                publish("StockReserved", orderId);
            } catch (OutOfStockException e) {
                publish("StockFailed", orderId);
            }
        });
        // 주문 서비스가 듣는 이벤트
        on("StockReserved", orders::confirm);
        on("PaymentRefunded", orders::cancel);
    }

    private final Map<Long, String> items = new HashMap<>();
    private final Map<Long, Long> amounts = new HashMap<>();

    private String itemOf(long orderId) {
        return items.get(orderId);
    }

    private long amountOf(long orderId) {
        return amounts.get(orderId);
    }

    private void on(String event, LongConsumer handler) {
        handlers.computeIfAbsent(event, k -> new ArrayList<>()).add(handler);
    }

    private void publish(String event, long orderId) {
        events.add(event);
        handlers.getOrDefault(event, List.of()).forEach(h -> h.accept(orderId));
    }

    public synchronized long placeOrder(String item, long amount) {
        events.clear();
        long orderId = orders.create(item, amount);
        items.put(orderId, item);
        amounts.put(orderId, amount);
        publish("OrderCreated", orderId);
        return orderId;
    }

    /** 마지막 placeOrder에서 오간 이벤트 */
    public synchronized List<String> events() {
        return List.copyOf(events);
    }
}
