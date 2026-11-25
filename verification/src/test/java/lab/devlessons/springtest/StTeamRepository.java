package lab.devlessons.springtest;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StTeamRepository extends JpaRepository<StTeam, Long> {
}
