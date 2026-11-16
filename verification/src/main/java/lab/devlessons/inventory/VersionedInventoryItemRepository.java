package lab.devlessons.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VersionedInventoryItemRepository extends JpaRepository<VersionedInventoryItem, Long> {
}
