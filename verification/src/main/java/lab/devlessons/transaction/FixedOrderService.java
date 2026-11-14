package lab.devlessons.transaction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 해결 1: 호출하는 쪽(placeOrder)에 @Transactional. 주문 저장과 포인트 적립이 한 트랜잭션이 된다. */
@Service
public class FixedOrderService {

    private final PurchaseOrderRepository orderRepository;
    private final PointRepository pointRepository;

    public FixedOrderService(PurchaseOrderRepository orderRepository, PointRepository pointRepository) {
        this.orderRepository = orderRepository;
        this.pointRepository = pointRepository;
    }

    @Transactional
    public void placeOrder(PurchaseOrder order) {
        orderRepository.save(order);
        addPoint(order.getUserId(), 100);
    }

    void addPoint(Long userId, int amount) {
        Point point = pointRepository.findByUserId(userId);
        point.add(amount);
    }
}
