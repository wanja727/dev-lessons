package lab.devlessons.observer;

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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 레슨 "옵저버 패턴과 이벤트"(observer)의 주장을 확인한다.
 */
class ObserverTest {

    private static final String LESSON = "observer";

    record SignedUp(String email) {
    }

    interface SignupListener {
        void on(SignedUp event);
    }

    // ── 코드 observer ──
    static class SignupService {
        private final List<SignupListener> listeners;

        SignupService(List<SignupListener> listeners) {
            this.listeners = listeners;
        }

        void signup(String email) {
            var event = new SignedUp(email);
            for (var l : listeners) {
                l.on(event);
            }
        }
    }

    @Test
    @DisplayName("추천인 포인트를 더하면 코드 direct는 SignupService를, 코드 observer는 조립 코드를 고치고 리스너가 새로 생긴다")
    void addingListener() {
        Map<String, String> directBefore = Map.of("SignupService", """
                class SignupService {
                  MailSender mail; CouponService coupon; Stats stats;
                  void signup(String email) {
                    mail.welcome(email);
                    coupon.give(email);
                    stats.increase();
                  }
                }
                """);
        Map<String, String> directAfter = Map.of("SignupService", """
                class SignupService {
                  MailSender mail; CouponService coupon; Stats stats; ReferralService referral;
                  void signup(String email) {
                    mail.welcome(email);
                    coupon.give(email);
                    stats.increase();
                    referral.give(email);
                  }
                }
                """);
        Map<String, String> before = new HashMap<>(Map.of(
                "SignedUp", "record SignedUp(String email) {}",
                "SignupListener", "interface SignupListener { void on(SignedUp e); }",
                "SignupService", """
                        import java.util.List;
                        class SignupService {
                          List<SignupListener> listeners;
                          SignupService(List<SignupListener> l) { listeners = l; }
                          void signup(String email) {
                            var e = new SignedUp(email);
                            for (var l : listeners) l.on(e);
                          }
                        }
                        """,
                "WelcomeMail", "class WelcomeMail implements SignupListener { public void on(SignedUp e) {} }",
                "Wiring", "class Wiring { SignupService s = new SignupService(java.util.List.of(new WelcomeMail())); }"));
        Map<String, String> after = new HashMap<>(before);
        after.put("ReferralPoint", "class ReferralPoint implements SignupListener { public void on(SignedUp e) {} }");
        after.put("Wiring", "class Wiring { SignupService s = new SignupService(java.util.List.of(new WelcomeMail(), new ReferralPoint())); }");

        assertThat(BrokenFiles.compile(after).ok()).isTrue();
        SourceChange d = SourceChange.between(directBefore, directAfter);
        SourceChange o = SourceChange.between(before, after);

        assertThat(d.changed()).containsExactly("SignupService");
        assertThat(o.changed()).containsExactly("Wiring");
        assertThat(o.added()).containsExactly("ReferralPoint");
        assertThat(o.same()).contains("SignupService");
        CiOutput.record(LESSON, "add", "[추천인 포인트 더하기]  (Wiring이 조립 코드예요)\n\n코드 direct\n" + d.describe()
                + "\n\n코드 observer\n" + o.describe());
    }

    @Test
    @DisplayName("코드 failing: 메일만 실행되고 쿠폰의 예외가 signup까지 올라와 통계는 실행되지 않는다")
    void listenerExceptionStopsTheRest() {
        List<String> ran = new ArrayList<>();
        SignupListener mail = e -> ran.add("환영 메일");
        SignupListener coupon = e -> {
            ran.add("쿠폰 지급 (예외)");
            throw new IllegalStateException("쿠폰 서버 오류");
        };
        SignupListener stats = e -> ran.add("가입 통계");
        SignupService service = new SignupService(List.of(mail, coupon, stats));

        String result;
        try {
            service.signup("a@b.c");
            result = "성공";
        } catch (IllegalStateException ex) {
            result = "IllegalStateException: " + ex.getMessage();
        }

        assertThat(ran).containsExactly("환영 메일", "쿠폰 지급 (예외)");
        assertThat(result).isEqualTo("IllegalStateException: 쿠폰 서버 오류");
        CiOutput.record(LESSON, "failing", "실행된 리스너 → " + ran + "\nsignup(\"a@b.c\")의 결과 → " + result);
    }

    @Test
    @DisplayName("0.5초 걸리는 리스너가 있으면 signup이 0.5초 넘게 걸린다")
    void slowListenerSlowsPublisher() {
        SignupListener slowStats = e -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        };
        SignupService service = new SignupService(List.of(e -> { }, slowStats));

        long start = System.nanoTime();
        service.signup("a@b.c");
        long ms = (System.nanoTime() - start) / 1_000_000;

        assertThat(ms).isGreaterThanOrEqualTo(500);
        CiOutput.record(LESSON, "slow", "통계 리스너가 0.5초 걸릴 때\nsignup(\"a@b.c\") → " + ms + "ms");
    }

    // ── 스프링 ──
    @Component
    static class SpringSignupService {
        private final ApplicationEventPublisher events;
        String thread;

        SpringSignupService(ApplicationEventPublisher events) {
            this.events = events;
        }

        void signup(String email) {
            thread = Thread.currentThread().getName();
            events.publishEvent(new SignedUp(email));
        }
    }

    @Component
    static class WelcomeMail {
        String thread;

        @EventListener
        void on(SignedUp e) {
            thread = Thread.currentThread().getName();
        }
    }

    @Component
    static class FailingCoupon {
        @EventListener
        void on(SignedUp e) {
            throw new IllegalStateException("쿠폰 서버 오류");
        }
    }

    @Test
    @DisplayName("@EventListener는 publishEvent를 부른 스레드에서 돌고, 리스너의 예외가 부른 쪽으로 올라간다")
    void springEventsAreSynchronous() {
        try (var ctx = new AnnotationConfigApplicationContext(SpringSignupService.class, WelcomeMail.class)) {
            SpringSignupService service = ctx.getBean(SpringSignupService.class);
            service.signup("a@b.c");
            WelcomeMail mail = ctx.getBean(WelcomeMail.class);

            assertThat(mail.thread).isEqualTo(service.thread);
        }
        String thread;
        String listenerThread;
        try (var ctx = new AnnotationConfigApplicationContext(SpringSignupService.class, WelcomeMail.class)) {
            ctx.getBean(SpringSignupService.class).signup("a@b.c");
            thread = ctx.getBean(SpringSignupService.class).thread;
            listenerThread = ctx.getBean(WelcomeMail.class).thread;
        }
        try (var ctx = new AnnotationConfigApplicationContext(SpringSignupService.class, FailingCoupon.class)) {
            SpringSignupService service = ctx.getBean(SpringSignupService.class);

            assertThatThrownBy(() -> service.signup("a@b.c"))
                    .isInstanceOf(IllegalStateException.class).hasMessage("쿠폰 서버 오류");
        }
        CiOutput.record(LESSON, "spring", "signup을 부른 스레드  → " + thread + "\n@EventListener 스레드 → " + listenerThread
                + "\n\n리스너가 예외를 던지면\nservice.signup(\"a@b.c\") → IllegalStateException: 쿠폰 서버 오류");
    }
}
