package lab.devlessons.lock;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface PlainStockRepository extends JpaRepository<PlainStock, Long> {

    /** 비관적 락: SELECT ... FOR UPDATE */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PlainStock s where s.id = :id")
    Optional<PlainStock> findForUpdate(@Param("id") Long id);

    /** 기다리지 않는 비관적 락: 이미 잠겨 있으면 바로 실패 (NOWAIT) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("select s from PlainStock s where s.id = :id")
    Optional<PlainStock> findForUpdateNoWait(@Param("id") Long id);
}
