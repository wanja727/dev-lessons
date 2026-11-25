package lab.devlessons.enumlesson;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 같은 테이블(enum_order)을 상수를 끼워 넣은 enum으로 읽는 엔티티.
 * "배포한 뒤의 코드가 예전에 저장된 행을 읽는다"를 한 테스트 안에서 만든다. 저장에는 쓰지 않는다.
 */
@Entity
@Table(name = "enum_order")
public class OrdinalOrderV2 {

    @Id
    private Long id;

    private OrderStatusV2 status;

    protected OrdinalOrderV2() {
    }

    public OrderStatusV2 getStatus() {
        return status;
    }
}
