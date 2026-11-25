package lab.devlessons.dynamicquery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DqProductRepository
        extends JpaRepository<DqProduct, Long>, JpaSpecificationExecutor<DqProduct> {
}
