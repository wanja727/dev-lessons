package lab.devlessons.nplusone;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TeamRepository extends JpaRepository<Team, Long> {

    @Query("select t from Team t left join fetch t.members")
    List<Team> findAllWithMembers();

    @EntityGraph(attributePaths = "members")
    List<Team> findAllBy();
}
