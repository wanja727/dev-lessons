package lab.devlessons.srpocp.a;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "단일 책임과 개방 폐쇄 원칙"의 코드 A. 할인 계산, 영수증 문구, 메일 보내기가 pay() 하나에 있다.
 * 이 패키지의 클래스는 레슨의 코드 A와 같은 모양이다.
 */
class CodeATest {

    /** send()가 받은 글을 적어 두는 가짜 MailSender. 불린 횟수는 줄 수로 센다 */
    static class FakeMailSender implements MailSender {
        final List<String> sent = new ArrayList<>();

        @Override
        public void send(String to, String text) {
            sent.add(to + " <- " + text);
        }
    }

    @Test
    @DisplayName("코드 A: VIP 10,000원 주문으로 pay()를 한 번 부르면 9000이 나오고 send()가 1번 불린다")
    void payCalculatesWritesAndSends() {
        FakeMailSender mail = new FakeMailSender();
        OrderService service = new OrderService();
        service.mail = mail;

        int paid = service.pay(new Order(Grade.VIP, 10_000, "kim@a.com"));

        assertThat(paid).isEqualTo(9000);
        assertThat(mail.sent).containsExactly("kim@a.com <- 결제 금액: 9000원");

        CiOutput.record("srp-ocp", "a-pay", "[코드 A] 영수증 문구를 확인하려고 pay()를 불렀어요\n"
                + "pay(VIP 10,000원 주문)  → " + paid + "\n"
                + "mail.send() 호출        → " + mail.sent.size() + "번\n"
                + "보낸 글                 → " + mail.sent.get(0).split(" <- ")[1]);
    }

    @Test
    @DisplayName("코드 A: 10,000원 주문은 VIP 9000, GOLD 9500, BASIC 10000이다")
    void payByGrade() {
        OrderService service = new OrderService();
        service.mail = new FakeMailSender();

        assertThat(service.pay(new Order(Grade.VIP, 10_000, "kim@a.com"))).isEqualTo(9000);
        assertThat(service.pay(new Order(Grade.GOLD, 10_000, "lee@a.com"))).isEqualTo(9500);
        assertThat(service.pay(new Order(Grade.BASIC, 10_000, "park@a.com"))).isEqualTo(10_000);
    }
}
