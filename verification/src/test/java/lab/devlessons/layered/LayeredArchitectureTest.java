package lab.devlessons.layered;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.MiniWeb;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 레슨 "계층형 아키텍처"의 주장을 검증한다. 서비스는 HTTP 없이 부르고, 저장소 구현을 바꿔 끼운다. */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class LayeredArchitectureTest {

    static final String LESSON = "layered-architecture";

    // ── 레슨의 코드: 세 계층 ──

    static class DuplicateEmail extends RuntimeException {
        DuplicateEmail(String email) {
            super("이미 가입한 이메일: " + email);
        }
    }

    static class Member {
        private Long id;
        private final String email;
        private final String name;
        private final String password;

        Member(String email, String name, String password) {
            this.email = email;
            this.name = name;
            this.password = password;
        }

        public Long getId() {
            return id;
        }

        public String getEmail() {
            return email;
        }

        public String getName() {
            return name;
        }

        public String getPassword() {
            return password;
        }
    }

    interface MemberRepository {
        boolean existsByEmail(String email);

        Member save(Member member);
    }

    /** 메모리에 담는 구현. 테스트와 처음 만들 때 쓴다 */
    static class MemoryMemberRepository implements MemberRepository {
        private final Map<Long, Member> store = new HashMap<>();
        private long seq = 0;

        public boolean existsByEmail(String email) {
            return store.values().stream().anyMatch(m -> m.email.equals(email));
        }

        public Member save(Member member) {
            member.id = ++seq;
            store.put(member.id, member);
            return member;
        }
    }

    /** JDBC 구현. PostgreSQL에 저장한다 */
    static class JdbcMemberRepository implements MemberRepository {
        private final JdbcTemplate jdbc;

        JdbcMemberRepository(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        public boolean existsByEmail(String email) {
            Integer n = jdbc.queryForObject("select count(*) from a1_member where email = ?", Integer.class, email);
            return n != null && n > 0;
        }

        public Member save(Member member) {
            member.id = jdbc.queryForObject(
                    "insert into a1_member(email, name, password) values (?, ?, ?) returning id",
                    Long.class, member.email, member.name, member.password);
            return member;
        }
    }

    /** 나눈 서비스. HTTP를 모르고 리포지토리 인터페이스에만 기댄다 */
    static class MemberService {
        private final MemberRepository repo;

        MemberService(MemberRepository repo) {
            this.repo = repo;
        }

        Long join(String email, String name) {
            if (repo.existsByEmail(email)) {
                throw new DuplicateEmail(email);
            }
            return repo.save(new Member(email, name, "secret")).getId();
        }
    }

    /** 잘못된 서비스: HTTP 요청과 응답을 직접 다룬다 */
    static class HttpAwareMemberService {
        private final MemberRepository repo;

        HttpAwareMemberService(MemberRepository repo) {
            this.repo = repo;
        }

        ResponseEntity<String> join(HttpServletRequest req) {
            String email = req.getParameter("email");
            if (repo.existsByEmail(email)) {
                return ResponseEntity.status(409).body("duplicate");
            }
            repo.save(new Member(email, req.getParameter("name"), "secret"));
            return ResponseEntity.ok("ok");
        }
    }

    record MemberResponse(Long id, String email, String name) {
    }

    @RestController
    static class EntityController {
        @GetMapping("/entity")
        Member get() {
            Member m = new Member("minji@a.com", "민지", "secret");
            m.id = 1L;
            return m;
        }
    }

    @RestController
    static class DtoController {
        @GetMapping("/dto")
        MemberResponse get() {
            Member m = new Member("minji@a.com", "민지", "secret");
            m.id = 1L;
            return new MemberResponse(m.getId(), m.getEmail(), m.getName());
        }
    }

    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("create table if not exists a1_member (id bigserial primary key,"
                + " email varchar(100) not null unique, name varchar(50), password varchar(50))");
        jdbc.execute("truncate a1_member restart identity");
    }

    @Test
    @DisplayName("나눈 서비스는 HTTP 없이 부를 수 있고, 같은 이메일로 두 번 가입하면 DuplicateEmail이 난다")
    void serviceWithoutHttp() {
        MemberService service = new MemberService(new MemoryMemberRepository());

        Long id = service.join("minji@a.com", "민지");

        assertThat(id).isEqualTo(1L);
        assertThatThrownBy(() -> service.join("minji@a.com", "민지2")).isInstanceOf(DuplicateEmail.class);
        CiOutput.record(LESSON, "service", "service.join(\"minji@a.com\", \"민지\") → id " + id + "\n"
                + "service.join(\"minji@a.com\", \"민지2\") → DuplicateEmail\n"
                + "(HTTP 요청 없이 서비스를 바로 불렀어요)");
    }

    @Test
    @DisplayName("같은 서비스에 메모리 구현과 JDBC 구현을 넣어도 결과가 같다")
    void swapRepository() {
        List<String> lines = new ArrayList<>();
        for (MemberRepository repo : List.of(new MemoryMemberRepository(), new JdbcMemberRepository(jdbc))) {
            MemberService service = new MemberService(repo);
            Long id = service.join("junho@a.com", "준호");
            String second;
            try {
                service.join("junho@a.com", "준호");
                second = "저장됨";
            } catch (DuplicateEmail e) {
                second = "DuplicateEmail";
            }
            assertThat(second).isEqualTo("DuplicateEmail");
            lines.add(repo.getClass().getSimpleName() + ": 첫 가입 id " + id + ", 두 번째 " + second);
        }
        assertThat(jdbc.queryForObject("select count(*) from a1_member", Integer.class)).isEqualTo(1);
        CiOutput.record(LESSON, "swap", "[같은 MemberService, 저장소만 바꿈]\n" + String.join("\n", lines));
    }

    @Test
    @DisplayName("나눈 서비스는 servlet과 spring web 패키지를 참조하지 않고, HTTP를 다루는 서비스는 참조한다")
    void serviceDoesNotReferenceWeb() throws IOException {
        String clean = classBytes(MemberService.class);
        String dirty = classBytes(HttpAwareMemberService.class);

        assertThat(clean).doesNotContain("jakarta/servlet").doesNotContain("org/springframework/http")
                .doesNotContain("org/springframework/web");
        assertThat(dirty).contains("jakarta/servlet/http/HttpServletRequest")
                .contains("org/springframework/http/ResponseEntity");
        CiOutput.record(LESSON, "imports", "[클래스 파일이 참조하는 웹 타입]\n"
                + "MemberService: 없음\n"
                + "HttpAwareMemberService: HttpServletRequest, ResponseEntity");
    }

    @Test
    @DisplayName("엔티티를 그대로 돌려주면 password가 JSON에 나가고, 응답 DTO는 정한 필드만 나간다")
    void entityLeaksPassword() {
        try (MiniWeb web = MiniWeb.start(EntityController.class, DtoController.class)) {
            String entity = web.get("/entity").body();
            String dto = web.get("/dto").body();

            assertThat(entity).contains("\"password\":\"secret\"");
            assertThat(dto).doesNotContain("password");
            CiOutput.record(LESSON, "dto", "GET /entity\n" + entity + "\n\nGET /dto\n" + dto);
        }
    }

    @Test
    @DisplayName("JdbcTemplate으로 같은 이메일을 두 번 넣으면 스프링의 DuplicateKeyException으로 받는다")
    void duplicateKeyTranslated() {
        JdbcMemberRepository repo = new JdbcMemberRepository(jdbc);
        repo.save(new Member("minji@a.com", "민지", "x"));

        assertThatThrownBy(() -> repo.save(new Member("minji@a.com", "민지", "x")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    /** 클래스 파일을 읽는다. 상수 풀에 참조하는 타입 이름이 문자열로 들어 있다 */
    private static String classBytes(Class<?> type) throws IOException {
        String resource = type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getClassLoader().getResourceAsStream(resource)) {
            return new String(in.readAllBytes(), ISO_8859_1);
        }
    }
}
