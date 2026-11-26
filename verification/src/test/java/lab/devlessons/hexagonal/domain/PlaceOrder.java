package lab.devlessons.hexagonal.domain;

/** 들어오는 포트: 바깥(컨트롤러, 배치, 메시지)이 부르는 유스케이스 */
public interface PlaceOrder {
    Order place(String orderId, long amount);
}
