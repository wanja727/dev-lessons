package lab.devlessons.transaction;

import org.springframework.stereotype.Service;

@Service
public class SplitOrderService {

    private final PurchaseOrderRepository orderRepository;
    private final PointService pointService;

    public SplitOrderService(PurchaseOrderRepository orderRepository, PointService pointService) {
        this.orderRepository = orderRepository;
        this.pointService = pointService;
    }

    public void placeOrder(PurchaseOrder order) {
        orderRepository.save(order);
        pointService.addPoint(order.getUserId(), 100); // 다른 빈 호출: 프록시를 거친다
    }
}
