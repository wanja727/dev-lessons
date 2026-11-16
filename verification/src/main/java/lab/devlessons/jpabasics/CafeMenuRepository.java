package lab.devlessons.jpabasics;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 인터페이스만 선언하면 Spring Data JPA가 구현을 만들어 빈으로 등록한다 */
public interface CafeMenuRepository extends JpaRepository<CafeMenu, Long> {

    /** 메서드 이름으로 만드는 쿼리: name이 같은 메뉴 */
    Optional<CafeMenu> findByName(String name);

    /** 메서드 이름으로 만드는 쿼리: price가 기준보다 작은 메뉴 */
    List<CafeMenu> findByPriceLessThan(int price);

    /** JPQL: 엔티티 이름(CafeMenu)과 필드 이름(price)으로 쓴다 */
    @Query("select m from CafeMenu m where m.price >= :min order by m.price")
    List<CafeMenu> findExpensive(@Param("min") int min);
}
