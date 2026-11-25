package lab.devlessons.bulkupdate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BulkProductRepository extends JpaRepository<BulkProduct, Long> {

    /** 벌크 연산: 모든 상품의 가격을 10% 올린다. 영속성 컨텍스트는 그대로 둔다 */
    @Modifying
    @Query("update BulkProduct p set p.price = p.price * 11 / 10")
    int raiseAll();

    /** 같은 벌크 연산. 끝난 뒤 영속성 컨텍스트를 비운다 */
    @Modifying(clearAutomatically = true)
    @Query("update BulkProduct p set p.price = p.price * 11 / 10")
    int raiseAllAndClear();
}
