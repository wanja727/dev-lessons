package lab.devlessons.transaction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 문제 [코드 1-A]의 코드. addPoint를 내부 호출해서 @Transactional이 적용되지 않는다. */
@Service
public class OrderService {

    private final PurchaseOrderRepository orderRepository;
    private final PointRepository pointRepository;

    public OrderService(PurchaseOrderRepository orderRepository, PointRepository pointRepository) {
        this.orderRepository = orderRepository;
        this.pointRepository = pointRepository;
    }

    public void placeOrder(PurchaseOrder order) {
        orderRepository.save(order);
        addPoint(order.getUserId(), 100); // this.addPoint(...): 프록시를 거치지 않는다
    }

    @Transactional
    public void addPoint(Long userId, int amount) {
        Point point = pointRepository.findByUserId(userId);
        point.add(amount);
    }
}
