package lab.devlessons.unittest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 페이크: 맵으로 진짜처럼 동작하는 저장소 */
public class MemoryMemberRepository implements MemberRepository {
    private final Map<String, Member> store = new HashMap<>();

    @Override
    public boolean existsByEmail(String email) {
        return store.containsKey(email);
    }

    @Override
    public Optional<Member> findByEmail(String email) {
        return Optional.ofNullable(store.get(email));
    }

    @Override
    public Member save(Member member) {
        store.put(member.email(), member);
        return member;
    }

    @Override
    public List<Member> findAll() {
        return new ArrayList<>(store.values());
    }
}
