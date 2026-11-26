package lab.devlessons.springevents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 레슨 "스프링 이벤트"(S13)의 주장을 PostgreSQL 컨테이너로 확인한다. 테스트에는 트랜잭션이 없다.
 * 레슨용 빈은 테스트의 중첩 클래스로 두고 @Import로만 넣는다.
 */
@SpringBootTest
@Import({PostgresContainerConfig.class, SpringEventsTest.MemberService.class, SpringEventsTest.Listeners.class,
        SpringEventsTest.CouponService.class})
class SpringEventsTest {

    private static final String LESSON = "spring-events";
    static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());

    /** 이벤트. mode로 어느 리스너가 반응할지 고른다 */
    public record MemberJoined(String email, String mode) {
    }

    static String where() {
        return Thread.currentThread().getName().replaceAll("Test worker", "main")
                + ", 트랜잭션 " + (TransactionSynchronizationManager.isActualTransactionActive() ? "있음" : "없음");
    }

    public static class MemberService {
        private final JdbcTemplate jdbc;
        private final ApplicationEventPublisher events;

        public MemberService(JdbcTemplate jdbc, ApplicationEventPublisher events) {
            this.jdbc = jdbc;
            this.events = events;
        }

        /** 레슨의 코드 predict */
        @Transactional
        public void join(String email, String mode) {
            jdbc.update("insert into ev_member(email) values (?)", email);
            LOG.add("join: publishEvent 앞 (" + where() + ")");
            events.publishEvent(new MemberJoined(email, mode));
            LOG.add("join: publishEvent 뒤");
        }

        @Transactional
        public void joinThenFail(String email, String mode) {
            join(email, mode);
            throw new IllegalStateException("가입 검증 실패");
        }

        /** 트랜잭션 없이 이벤트를 낸다 */
        public void publishOutside(String email, String mode) {
            events.publishEvent(new MemberJoined(email, mode));
        }
    }

    public static class CouponService {
        private final JdbcTemplate jdbc;

        public CouponService(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        public void issuePlain(String email) {
            jdbc.update("insert into ev_coupon(email) values (?)", email);
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void issueNew(String email) {
            jdbc.update("insert into ev_coupon(email) values (?)", email);
        }
    }

    public static class Listeners {
        private final CouponService coupons;

        public Listeners(CouponService coupons) {
            this.coupons = coupons;
        }

        @EventListener
        public void plain(MemberJoined e) {
            if (e.mode().startsWith("plain")) {
                LOG.add("@EventListener 실행 (" + where() + ")");
                if (e.mode().equals("plain-fail")) {
                    throw new IllegalStateException("메일 서버 응답 없음");
                }
            }
        }

        @TransactionalEventListener
        public void afterCommit(MemberJoined e) {
            if (e.mode().startsWith("after")) {
                LOG.add("@TransactionalEventListener 실행 (" + where() + ")");
                switch (e.mode()) {
                    case "after-fail" -> throw new IllegalStateException("메일 서버 응답 없음");
                    case "after-coupon-plain" -> coupons.issuePlain(e.email());
                    case "after-coupon-new" -> coupons.issueNew(e.email());
                    default -> {
                    }
                }
            }
        }
    }

    @Autowired MemberService members;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void tables() {
        jdbc.execute("create table if not exists ev_member(id serial primary key, email text not null)");
        jdbc.execute("create table if not exists ev_coupon(id serial primary key, email text not null)");
        jdbc.update("delete from ev_member");
        jdbc.update("delete from ev_coupon");
        LOG.clear();
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    @DisplayName("@EventListener는 publishEvent를 부른 스레드에서 그 자리에서 바로 돌고, 같은 트랜잭션 안에 있다")
    void plainListenerRunsInline() {
        members.join("kim@a.com", "plain");

        assertThat(LOG).containsExactly(
                "join: publishEvent 앞 (main, 트랜잭션 있음)",
                "@EventListener 실행 (main, 트랜잭션 있음)",
                "join: publishEvent 뒤");
        CiOutput.record(LESSON, "inline", String.join("\n", LOG));
    }

    @Test
    @DisplayName("@EventListener가 예외를 던지면 가입 트랜잭션이 롤백되고 예외가 join을 부른 쪽으로 간다")
    void plainListenerFailureRollsBack() {
        assertThatThrownBy(() -> members.join("kim@a.com", "plain-fail")).isInstanceOf(IllegalStateException.class);

        assertThat(count("ev_member")).isZero();
        CiOutput.record(LESSON, "plain-fail", String.join("\n", LOG)
                + "\njoin을 부른 쪽이 받은 예외: IllegalStateException\n남은 회원: " + count("ev_member") + "명");
    }

    @Test
    @DisplayName("@TransactionalEventListener는 커밋된 뒤에 돌고, 그 예외는 가입을 되돌리지 않는다")
    void afterCommitListener() {
        members.join("kim@a.com", "after");
        List<String> ok = new ArrayList<>(LOG);
        LOG.clear();
        String thrown;
        try {
            members.join("lee@a.com", "after-fail");
            thrown = "없음";
        } catch (Exception e) {
            thrown = e.getClass().getSimpleName() + ": " + e.getMessage();
        }

        assertThat(ok).containsExactly("join: publishEvent 앞 (main, 트랜잭션 있음)", "join: publishEvent 뒤",
                "@TransactionalEventListener 실행 (main, 트랜잭션 있음)");
        assertThat(count("ev_member")).isEqualTo(2);
        CiOutput.record(LESSON, "after-commit", "성공한 가입:\n" + String.join("\n", ok)
                + "\n\n메일이 실패한 가입:\njoin을 부른 쪽이 받은 예외: " + thrown + "\n남은 회원: " + count("ev_member") + "명");
    }

    @Test
    @DisplayName("가입이 롤백되면 @TransactionalEventListener는 돌지 않는다")
    void rolledBackSkipsListener() {
        assertThatThrownBy(() -> members.joinThenFail("kim@a.com", "after")).isInstanceOf(IllegalStateException.class);

        assertThat(LOG).noneMatch(l -> l.startsWith("@TransactionalEventListener"));
        assertThat(count("ev_member")).isZero();
        CiOutput.record(LESSON, "rollback", String.join("\n", LOG) + "\n(가입 검증 실패로 롤백)\n리스너 실행: 없음\n남은 회원: 0명");
    }

    @Test
    @DisplayName("커밋 뒤 리스너에서 REQUIRES_NEW 없이 쓴 행과 REQUIRES_NEW로 쓴 행")
    void writeAfterCommit() {
        members.join("kim@a.com", "after-coupon-plain");
        int plain = count("ev_coupon");
        members.join("lee@a.com", "after-coupon-new");
        int withNew = count("ev_coupon") - plain;

        assertThat(withNew).isEqualTo(1);
        CiOutput.record(LESSON, "write-after-commit", "커밋 뒤 리스너에서 그냥 insert → 남은 쿠폰 " + plain + "장"
                + "\n커밋 뒤 리스너에서 REQUIRES_NEW로 insert → 남은 쿠폰 " + withNew + "장");
        CiOutput.record(LESSON, "write-after-commit-plain", String.valueOf(plain));
    }

    @Test
    @DisplayName("트랜잭션 밖에서 낸 이벤트는 @TransactionalEventListener가 기본으로 듣지 않는다")
    void outsideTransaction() {
        members.publishOutside("kim@a.com", "after");

        assertThat(LOG).isEmpty();
        CiOutput.record(LESSON, "outside", "트랜잭션 없이 publishEvent → @TransactionalEventListener 실행: 없음");
    }
}
