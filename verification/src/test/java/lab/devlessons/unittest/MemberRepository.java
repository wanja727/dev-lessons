package lab.devlessons.unittest;

import java.util.List;
import java.util.Optional;

public interface MemberRepository {
    boolean existsByEmail(String email);

    Optional<Member> findByEmail(String email);

    Member save(Member member);

    List<Member> findAll();
}
