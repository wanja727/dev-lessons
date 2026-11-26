package lab.devlessons.hexagonal.domain;

/** 나가는 포트: 도메인이 필요한 저장 기능을 도메인의 말로 정한다. 구현(어댑터)은 바깥에 있다 */
public interface OrderStore {
    void save(Order order);

    Order find(String id);
}
