package lab.devlessons.springtest;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StMemberRepository extends JpaRepository<StMember, Long> {
    Optional<StMember> findByEmail(String email);
}
