package lab.devlessons.srpocp.b;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "단일 책임과 개방 폐쇄 원칙"의 코드 B. 코드 A를 바뀌는 이유마다 나눴다.
 * 이 패키지의 클래스는 레슨의 코드 B와 같은 모양이다.
 */
class CodeBTest {

    /** send()가 받은 글을 적어 두는 가짜 MailSender. 불린 횟수는 줄 수로 센다 */
    static class FakeMailSender implements MailSender {
        final List<String> sent = new ArrayList<>();

        @Override
        public void send(String to, String text) {
            sent.add(to + " <- " + text);
        }
    }

    private static OrderService service(FakeMailSender mail) {
        OrderService service = new OrderService();
        service.calculator = new DiscountCalculator();
        service.writer = new ReceiptWriter();
        service.mail = mail;
        return service;
    }

    @Test
    @DisplayName("코드 B: ReceiptWriter.write(9000)은 \"결제 금액: 9000원\"을 돌려주고 send()는 0번 불린다")
    void receiptIsTestedAlone() {
        FakeMailSender mail = new FakeMailSender();
        OrderService service = service(mail);

        String text = service.writer.write(9000);

        assertThat(text).isEqualTo("결제 금액: 9000원");
        assertThat(mail.sent).isEmpty();

        CiOutput.record("srp-ocp", "b-write", "[코드 B] 영수증 문구를 확인하려고 write()만 불렀어요\n"
                + "write(9000)             → " + text + "\n"
                + "mail.send() 호출        → " + mail.sent.size() + "번");
    }

    @Test
    @DisplayName("코드 B: pay()는 코드 A와 같은 값을 돌려준다. VIP 9000, GOLD 9500, BASIC 10000")
    void payReturnsSameAsCodeA() {
        FakeMailSender mail = new FakeMailSender();
        OrderService service = service(mail);

        assertThat(service.pay(new Order(Grade.VIP, 10_000, "kim@a.com"))).isEqualTo(9000);
        assertThat(service.pay(new Order(Grade.GOLD, 10_000, "lee@a.com"))).isEqualTo(9500);
        assertThat(service.pay(new Order(Grade.BASIC, 10_000, "park@a.com"))).isEqualTo(10_000);
        assertThat(mail.sent).containsExactly(
                "kim@a.com <- 결제 금액: 9000원",
                "lee@a.com <- 결제 금액: 9500원",
                "park@a.com <- 결제 금액: 10000원");
    }
}
