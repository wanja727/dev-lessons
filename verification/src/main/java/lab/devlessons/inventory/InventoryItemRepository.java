package lab.devlessons.inventory;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    /** 비관적 락: SELECT ... FOR UPDATE */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.id = :id")
    Optional<InventoryItem> findForUpdate(@Param("id") Long id);

    /** 조건부 UPDATE: 재고가 있을 때만 줄이고, 바뀐 행 수를 돌려준다 */
    @Modifying
    @Query("update InventoryItem i set i.quantity = i.quantity - 1 where i.id = :id and i.quantity >= 1")
    int decreaseIfAvailable(@Param("id") Long id);
}
