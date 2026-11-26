package lab.devlessons.hexagonal.domain;

/** 나가는 포트: 결제를 요청한다. 어느 PG인지, HTTP인지 도메인은 모른다 */
public interface PaymentPort {
    boolean pay(String orderId, long amount);
}
