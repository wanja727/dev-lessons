package lab.devlessons.outbox;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** 아직 보내지 않은 메시지를 저장된 순서대로 */
    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByIdAsc();

    long countByPublishedAtIsNull();
}
