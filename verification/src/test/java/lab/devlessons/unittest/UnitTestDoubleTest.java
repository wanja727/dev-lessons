package lab.devlessons.unittest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoSession;
import org.mockito.exceptions.misusing.PotentialStubbingProblem;
import org.mockito.exceptions.misusing.UnnecessaryStubbingException;
import org.mockito.exceptions.verification.WantedButNotInvoked;
import org.mockito.quality.Strictness;

/**
 * 레슨 "단위 테스트와 테스트 더블"(T2). 같은 SignupService를 스텁, 목, 페이크로 테스트하고
 * 구현을 바꿨을 때 어느 테스트가 깨지는지 본다.
 */
class UnitTestDoubleTest {

    static final String LESSON = "unit-test-double";

    @Test
    @DisplayName("답을 정하지 않은 목은 null, false, 빈 Optional, 빈 리스트를 돌려줘서 signup의 결과는 null이다")
    void unstubbedMockReturnsDefaults() {
        MemberRepository repo = mock(MemberRepository.class);
        MailSender mail = mock(MailSender.class);
        var service = new SignupService(repo, mail);

        Member m = service.signup("a@b.com");

        assertThat(m).isNull();
        assertThat(repo.existsByEmail("x")).isFalse();
        assertThat(repo.save(new Member("x"))).isNull();
        assertThat(repo.findByEmail("x")).isEmpty();
        assertThat(repo.findAll()).isEmpty();
        CiOutput.record(LESSON, "defaults", """
                service.signup("a@b.com") → %s

                답을 정하지 않은 목의 메서드
                repo.existsByEmail("x") → %s
                repo.save(...)          → %s
                repo.findByEmail("x")   → %s
                repo.findAll()          → %s""".formatted(m, repo.existsByEmail("x"),
                repo.save(new Member("x")), repo.findByEmail("x"), repo.findAll()));
    }

    @Test
    @DisplayName("existsByEmail을 true로 스텁하면 DuplicateEmail이 나고 메일은 나가지 않는다")
    void stubMakesDuplicateSituation() {
        MemberRepository repo = mock(MemberRepository.class);
        MailSender mail = mock(MailSender.class);
        var service = new SignupService(repo, mail);
        when(repo.existsByEmail("a@b.com")).thenReturn(true);

        assertThatThrownBy(() -> service.signup("a@b.com"))
                .isInstanceOf(DuplicateEmail.class)
                .hasMessage("이미 가입한 이메일: a@b.com");
        verify(mail, never()).send(any(), any());
        verify(repo, never()).save(any());
        CiOutput.record(LESSON, "stub", """
                when(repo.existsByEmail("a@b.com")).thenReturn(true)
                service.signup("a@b.com")
                → DuplicateEmail: 이미 가입한 이메일: a@b.com

                verify(mail, never()).send(any(), any()) → 통과
                verify(repo, never()).save(any())        → 통과""");
    }

    @Test
    @DisplayName("목으로 메일이 그 인자로 한 번 불렸는지 확인하고, ArgumentCaptor로 save의 인자를 꺼낸다")
    void mockVerifiesCalls() {
        MemberRepository repo = mock(MemberRepository.class);
        MailSender mail = mock(MailSender.class);
        var service = new SignupService(repo, mail);

        service.signup("a@b.com");

        verify(mail).send("a@b.com", "가입을 환영해요");
        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().email()).isEqualTo("a@b.com");

        Throwable wrongText = catchThrowable(() -> verify(mail).send("a@b.com", "환영해요"));
        assertThat(wrongText).isNotNull();
        CiOutput.record(LESSON, "verify", """
                service.signup("a@b.com")

                verify(mail).send("a@b.com", "가입을 환영해요") → 통과
                save에 넘어간 Member의 이메일 → %s

                verify(mail).send("a@b.com", "환영해요")
                → %s""".formatted(saved.getValue().email(), firstLine(wrongText)));
    }

    @Test
    @DisplayName("구현을 findByEmail로 바꾸면 호출을 검증한 목 테스트는 깨지고 페이크로 상태를 본 테스트는 그대로 통과한다")
    void overMockingBreaksOnRefactor() {
        List<String> report = new ArrayList<>();
        for (String version : List.of("고치기 전 (existsByEmail)", "고친 뒤 (findByEmail)")) {
            boolean before = version.startsWith("고치기 전");

            MemberRepository repo = mock(MemberRepository.class);
            MailSender mail = mock(MailSender.class);
            signup(before, repo, mail, "a@b.com");
            String mockResult = result(() -> verify(repo).existsByEmail("a@b.com"));

            MemoryMemberRepository fake = new MemoryMemberRepository();
            signup(before, fake, mock(MailSender.class), "a@b.com");
            String fakeResult = result(() -> {
                assertThat(fake.findByEmail("a@b.com")).isPresent();
                assertThatThrownBy(() -> signup(before, fake, mock(MailSender.class), "a@b.com"))
                        .isInstanceOf(DuplicateEmail.class);
            });

            report.add("""
                    %s
                      목: verify(repo).existsByEmail(...) → %s
                      페이크: 가입 뒤 저장소에 회원이 있다 → %s""".formatted(version, mockResult, fakeResult));
            if (before) {
                assertThat(mockResult).isEqualTo("통과");
            } else {
                assertThat(mockResult).startsWith("실패");
                assertThatThrownBy(() -> verify(repo).existsByEmail("a@b.com"))
                        .isInstanceOf(WantedButNotInvoked.class);
            }
            assertThat(fakeResult).isEqualTo("통과");
        }
        CiOutput.record(LESSON, "refactor", String.join("\n\n", report));
    }

    @Test
    @DisplayName("엄격한 스텁에서 쓰이지 않은 스텁은 UnnecessaryStubbingException, 다른 인자로 부르면 PotentialStubbingProblem이다")
    void strictStubsCatchWrongStubs() {
        MockitoSession unused = org.mockito.Mockito.mockitoSession()
                .strictness(Strictness.STRICT_STUBS).startMocking();
        Throwable unnecessary;
        try {
            MemberRepository repo1 = mock(MemberRepository.class);
            when(repo1.findByEmail("a@b.com")).thenReturn(Optional.of(new Member("a@b.com")));
        } finally {
            unnecessary = catchThrowable(unused::finishMocking);
        }
        assertThat(unnecessary).isInstanceOf(UnnecessaryStubbingException.class);

        MockitoSession typo = org.mockito.Mockito.mockitoSession()
                .strictness(Strictness.STRICT_STUBS).startMocking();
        Throwable wrongArg;
        try {
            MemberRepository repo = mock(MemberRepository.class);
            when(repo.existsByEmail("a@b.co")).thenReturn(true);
            var service = new SignupService(repo, mock(MailSender.class));
            wrongArg = catchThrowable(() -> service.signup("a@b.com"));
        } finally {
            catchThrowable(typo::finishMocking);
        }
        assertThat(wrongArg).isInstanceOf(PotentialStubbingProblem.class);
        CiOutput.record(LESSON, "strict", """
                (스텁만 하고 한 번도 부르지 않음)
                → %s

                when(repo.existsByEmail("a@b.co")).thenReturn(true)
                service.signup("a@b.com")
                → %s""".formatted(unnecessary.getClass().getSimpleName(),
                wrongArg.getClass().getSimpleName()));
    }

    @Test
    @DisplayName("Clock.fixed를 주입하면 LocalDateTime.now(clock)이 늘 같은 시각이다")
    void fixedClock() throws InterruptedException {
        Clock clock = Clock.fixed(Instant.parse("2026-11-25T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        LocalDateTime first = LocalDateTime.now(clock);
        Thread.sleep(5);
        LocalDateTime second = LocalDateTime.now(clock);

        assertThat(first).isEqualTo(second).isEqualTo(LocalDateTime.parse("2026-11-25T10:00:00"));
        CiOutput.record(LESSON, "clock", """
                Clock clock = Clock.fixed(2026-11-25T01:00Z, Asia/Seoul)
                LocalDateTime.now(clock) → %s
                (5ms 뒤) LocalDateTime.now(clock) → %s""".formatted(first, second));
    }

    @Test
    @DisplayName("경계값 30000이 >= 대신 >로 쓴 버그를 잡고, 20000 하나로는 못 잡는다")
    void boundaryValuesCatchBugs() {
        List<String> lines = new ArrayList<>();
        for (int price : new int[] {20000, 29999, 30000}) {
            int want = ShippingFee.fee(price);
            int got = ShippingFee.buggyFee(price);
            lines.add("price %d → 기대 %d, 버그 있는 구현 %d  %s".formatted(price, want, got,
                    want == got ? "통과" : "실패"));
        }
        assertThat(ShippingFee.buggyFee(20000)).isEqualTo(ShippingFee.fee(20000));
        assertThat(ShippingFee.buggyFee(29999)).isEqualTo(ShippingFee.fee(29999));
        assertThat(ShippingFee.buggyFee(30000)).isNotEqualTo(ShippingFee.fee(30000));
        CiOutput.record(LESSON, "boundary", String.join("\n", lines));
    }

    @Test
    @DisplayName("목을 쓴 단위 테스트는 스프링도 DB도 없이 1000번을 몇 십 밀리초 안에 돈다")
    void unitTestsAreFast() {
        long start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            MemoryMemberRepository repo = new MemoryMemberRepository();
            MailSender mail = mock(MailSender.class);
            new SignupService(repo, mail).signup("u" + i + "@b.com");
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertThat(ms).isLessThan(5000);
        CiOutput.record(LESSON, "speed", "가입 테스트 1000번 (페이크 저장소, 목 메일) → %d ms".formatted(ms));
    }

    private static void signup(boolean before, MemberRepository repo, MailSender mail, String email) {
        if (before) {
            new SignupService(repo, mail).signup(email);
        } else {
            new SignupServiceV2(repo, mail).signup(email);
        }
    }

    private static String result(Runnable check) {
        try {
            check.run();
            return "통과";
        } catch (Throwable t) {
            return "실패 (" + t.getClass().getSimpleName() + ")";
        }
    }

    private static Throwable catchThrowable(org.assertj.core.api.ThrowableAssert.ThrowingCallable c) {
        return org.assertj.core.api.Assertions.catchThrowable(c);
    }

    private static String firstLine(Throwable t) {
        String msg = t.getClass().getSimpleName() + ": " + t.getMessage().strip();
        return msg.lines().limit(2).reduce((a, b) -> a + "\n" + b).orElse(msg);
    }
}
