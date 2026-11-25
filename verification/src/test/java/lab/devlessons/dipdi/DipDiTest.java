package lab.devlessons.dipdi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lab.devlessons.support.BrokenFiles;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.SourceChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 레슨 "의존 역전 원칙과 의존성 주입"(dip-di)의 주장을 확인한다.
 * 실행 결과는 이 파일 안의 클래스로, 패키지의 import 방향과 고칠 파일은 소스 문자열로 본다.
 * 스프링 주입은 작은 AnnotationConfigApplicationContext로 확인한다.
 */
class DipDiTest {

    private static final String LESSON = "dip-di";

    // ── 코드 direct ──
    static class SmtpMailSender {
        static int sent = 0;

        void send(String to, String msg) {
            sent++;
        }
    }

    static class DirectSignupService {
        private final SmtpMailSender mail = new SmtpMailSender();

        void signup(String email) {
            mail.send(email, "가입을 환영해요");
        }
    }

    @Test
    @DisplayName("코드 direct의 signup은 진짜 SmtpMailSender.send를 부른다")
    void directCallsRealSender() {
        SmtpMailSender.sent = 0;

        new DirectSignupService().signup("a@b.c");

        assertThat(SmtpMailSender.sent).isEqualTo(1);
        CiOutput.record(LESSON, "direct", "new SignupService().signup(\"a@b.c\")\n→ SmtpMailSender.send 호출: "
                + SmtpMailSender.sent + "번 (테스트에서도 진짜 메일 발송 코드가 돈다)");
    }

    // ── 코드 inverted와 fake ──
    interface Notifier {
        void notify(String to, String msg);
    }

    static class SignupService {
        private final Notifier notifier;

        SignupService(Notifier notifier) {
            this.notifier = notifier;
        }

        void signup(String email) {
            notifier.notify(email, "가입을 환영해요");
        }
    }

    static class FakeNotifier implements Notifier {
        final List<String> sent = new ArrayList<>();

        public void notify(String to, String msg) {
            sent.add(to);
        }
    }

    @Test
    @DisplayName("코드 fake를 넣으면 sent에 받는 사람이 남고 메일은 나가지 않는다")
    void fakeNotifier() {
        SmtpMailSender.sent = 0;
        FakeNotifier fake = new FakeNotifier();

        new SignupService(fake).signup("a@b.c");

        assertThat(fake.sent).containsExactly("a@b.c");
        assertThat(SmtpMailSender.sent).isZero();
        CiOutput.record(LESSON, "fake", "new SignupService(fake).signup(\"a@b.c\")\n→ fake.sent: " + fake.sent
                + "\n→ SmtpMailSender.send 호출: " + SmtpMailSender.sent + "번");
    }

    // ── 패키지의 import 방향 ──

    private static final String MEMBER_NOTIFIER = """
            package member;
            public interface Notifier {
              void notify(String to, String msg);
            }
            """;
    private static final String MEMBER_SERVICE = """
            package member;
            public class SignupService {
              private final Notifier notifier;
              public SignupService(Notifier notifier) { this.notifier = notifier; }
              public void signup(String email) { notifier.notify(email, "가입을 환영해요"); }
            }
            """;
    private static final String INFRA_SMTP = """
            package infra;
            import member.Notifier;
            public class SmtpNotifier implements Notifier {
              public void notify(String to, String msg) {}
            }
            """;
    private static final String DIRECT_SERVICE = """
            package member;
            import infra.SmtpMailSender;
            public class SignupService {
              private final SmtpMailSender mail = new SmtpMailSender();
              public void signup(String email) { mail.send(email, "가입을 환영해요"); }
            }
            """;
    private static final String INFRA_MAIL = """
            package infra;
            public class SmtpMailSender {
              public void send(String to, String msg) {}
            }
            """;

    @Test
    @DisplayName("코드 inverted에서 member 패키지는 infra 없이 컴파일되고, 코드 direct의 member는 infra가 있어야 컴파일된다")
    void importDirection() {
        BrokenFiles invertedMemberOnly = BrokenFiles.compile(Map.of(
                "member.Notifier", MEMBER_NOTIFIER, "member.SignupService", MEMBER_SERVICE));
        BrokenFiles invertedInfraOnly = BrokenFiles.compile(Map.of("infra.SmtpNotifier", INFRA_SMTP));
        BrokenFiles directMemberOnly = BrokenFiles.compile(Map.of("member.SignupService", DIRECT_SERVICE));
        BrokenFiles directAll = BrokenFiles.compile(Map.of(
                "member.SignupService", DIRECT_SERVICE, "infra.SmtpMailSender", INFRA_MAIL));

        assertThat(invertedMemberOnly.ok()).isTrue();
        assertThat(invertedInfraOnly.files()).containsExactly("SmtpNotifier");
        assertThat(directMemberOnly.files()).containsExactly("SignupService");
        assertThat(directAll.ok()).isTrue();

        CiOutput.record(LESSON, "packages", "[코드 direct]\nmember 패키지만 컴파일 → 오류 (SignupService가 infra를 import)\n\n"
                + "[코드 inverted]\nmember 패키지만 컴파일 → 성공\ninfra 패키지만 컴파일 → 오류 (SmtpNotifier가 member를 import)");
    }

    // ── 알림톡으로 바꿀 때 고치는 파일 ──

    @Test
    @DisplayName("알림톡으로 바꾸면 코드 direct는 SignupService를, 코드 inverted는 조립 코드를 고치고 KakaoNotifier가 생긴다")
    void switchToKakao() {
        Map<String, String> directBefore = Map.of("SignupService", """
                class SignupService {
                  private final SmtpMailSender mail = new SmtpMailSender();
                }
                """);
        Map<String, String> directAfter = Map.of("SignupService", """
                class SignupService {
                  private final KakaoAlimtalk mail = new KakaoAlimtalk();
                }
                """);
        Map<String, String> invertedBefore = new HashMap<>(Map.of(
                "Notifier", "interface Notifier { void notify(String to, String msg); }",
                "SignupService", "class SignupService { SignupService(Notifier n) {} }",
                "SmtpNotifier", "class SmtpNotifier implements Notifier { public void notify(String t, String m) {} }",
                "Wiring", "class Wiring { SignupService s = new SignupService(new SmtpNotifier()); }"));
        Map<String, String> invertedAfter = new HashMap<>(invertedBefore);
        invertedAfter.put("KakaoNotifier",
                "class KakaoNotifier implements Notifier { public void notify(String t, String m) {} }");
        invertedAfter.put("Wiring", "class Wiring { SignupService s = new SignupService(new KakaoNotifier()); }");

        assertThat(BrokenFiles.compile(invertedAfter).ok()).isTrue();
        SourceChange direct = SourceChange.between(directBefore, directAfter);
        SourceChange inverted = SourceChange.between(invertedBefore, invertedAfter);

        assertThat(direct.changed()).containsExactly("SignupService");
        assertThat(inverted.changed()).containsExactly("Wiring");
        assertThat(inverted.added()).containsExactly("KakaoNotifier");
        assertThat(inverted.same()).contains("SignupService", "Notifier");

        CiOutput.record(LESSON, "kakao", "[알림톡으로 바꾸기]  (Wiring이 조립 코드예요)\n\n코드 direct\n" + direct.describe()
                + "\n\n코드 inverted\n" + inverted.describe());
    }

    // ── 스프링 ──

    @Component
    static class SpringSmtpNotifier implements Notifier {
        public void notify(String to, String msg) {
        }
    }

    @Component
    static class SpringSignupService {
        private final Notifier notifier;

        SpringSignupService(Notifier notifier) {
            this.notifier = notifier;
        }
    }

    @Test
    @DisplayName("생성자 하나인 @Component에 스프링이 Notifier 구현을 넣는다")
    void springConstructorInjection() {
        try (var ctx = new AnnotationConfigApplicationContext(SpringSmtpNotifier.class, SpringSignupService.class)) {
            SpringSignupService service = ctx.getBean(SpringSignupService.class);

            assertThat(service.notifier).isSameAs(ctx.getBean(Notifier.class));
            CiOutput.record(LESSON, "spring", "ctx.getBean(SignupService.class).notifier\n→ "
                    + service.notifier.getClass().getSimpleName().replace("Spring", ""));
        }
    }

    static class FieldSignupService {
        @Autowired
        private Notifier notifier;

        void signup(String email) {
            notifier.notify(email, "가입을 환영해요");
        }
    }

    @Test
    @DisplayName("필드 주입 서비스를 new로 만들어 부르면 NullPointerException")
    void fieldInjectionNeedsContainer() {
        assertThatThrownBy(() -> new FieldSignupService().signup("a@b.c")).isInstanceOf(NullPointerException.class);
        CiOutput.record(LESSON, "field", "new SignupService().signup(\"a@b.c\")  // 필드 주입\n→ NullPointerException (notifier가 null)");
    }
}
