package lab.devlessons.propagation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PropRecordRepository extends JpaRepository<PropRecord, Long> {

    @Query("select r.name from PropRecord r order by r.name")
    List<String> findAllNames();
}
