package lab.devlessons.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 문제 [코드 1-A~C]: 내부 호출(self-invocation)에는 @Transactional이 적용되지 않는다. */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class SelfInvocationTest {

    private static final long USER_ID = 1L;

    @Autowired OrderService orderService;
    @Autowired FixedOrderService fixedOrderService;
    @Autowired SplitOrderService splitOrderService;
    @Autowired PurchaseOrderRepository orderRepository;
    @Autowired PointRepository pointRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        pointRepository.deleteAll();
        pointRepository.save(new Point(USER_ID, 0));
    }

    @Test
    @DisplayName("내부 호출: 주문은 저장되지만 포인트는 그대로 (예외 없이 조용히 빠진다)")
    void selfInvocationLosesPoint() {
        orderService.placeOrder(new PurchaseOrder(USER_ID));

        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(pointRepository.findByUserId(USER_ID).getAmount()).isZero();
    }

    @Test
    @DisplayName("해결 1: 호출하는 쪽에 @Transactional을 붙이면 포인트가 반영된다")
    void transactionalOnCaller() {
        fixedOrderService.placeOrder(new PurchaseOrder(USER_ID));

        assertThat(pointRepository.findByUserId(USER_ID).getAmount()).isEqualTo(100);
    }

    @Test
    @DisplayName("해결 2: 다른 빈으로 분리하면 프록시를 거쳐 포인트가 반영된다")
    void separateBean() {
        splitOrderService.placeOrder(new PurchaseOrder(USER_ID));

        assertThat(pointRepository.findByUserId(USER_ID).getAmount()).isEqualTo(100);
    }
}
