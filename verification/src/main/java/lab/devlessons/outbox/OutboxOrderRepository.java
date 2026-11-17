package lab.devlessons.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxOrderRepository extends JpaRepository<OutboxOrder, Long> {
}
