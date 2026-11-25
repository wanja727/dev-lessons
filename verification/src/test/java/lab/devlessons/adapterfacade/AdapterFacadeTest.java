package lab.devlessons.adapterfacade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lab.devlessons.support.BrokenFiles;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.SourceChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 레슨 "어댑터와 퍼사드 패턴"(adapter-facade)의 주장을 확인한다.
 */
class AdapterFacadeTest {

    private static final String LESSON = "adapter-facade";

    // ── 코드 ours ──
    interface PaymentGateway {
        Approval approve(long orderId, int amount);
    }

    record Approval(boolean ok, String txId) {
    }

    // ── 코드 sdk: 결제 대행사 SDK의 모양 (가짜) ──
    static class TossSdk {
        Map<String, Object> lastParams;
        String code = "0000";

        Map<String, String> requestPayment(Map<String, Object> params) {
            lastParams = params;
            Map<String, String> res = new HashMap<>();
            res.put("code", code);
            res.put("tid", "0000".equals(code) ? "T1" : null);
            return res;
        }
    }

    // ── 코드 adapter ──
    static class TossAdapter implements PaymentGateway {
        private final TossSdk sdk;

        TossAdapter(TossSdk sdk) {
            this.sdk = sdk;
        }

        public Approval approve(long orderId, int amount) {
            var res = sdk.requestPayment(Map.of(
                    "orderNo", String.valueOf(orderId),
                    "amount", (long) amount,
                    "currency", "KRW"));
            return new Approval("0000".equals(res.get("code")), res.get("tid"));
        }
    }

    @Test
    @DisplayName("TossAdapter는 주문 번호를 문자열로, 금액을 long으로 바꾸고 KRW를 넣으며, 0000은 ok=true, 그 밖은 ok=false")
    void adapterTranslates() {
        TossSdk sdk = new TossSdk();
        PaymentGateway gateway = new TossAdapter(sdk);

        Approval ok = gateway.approve(42L, 30000);
        Map<String, Object> params = sdk.lastParams;
        sdk.code = "1001";
        Approval fail = gateway.approve(43L, 30000);

        assertThat(params.get("orderNo")).isEqualTo("42");
        assertThat(params.get("amount")).isEqualTo(30000L);
        assertThat(params.get("currency")).isEqualTo("KRW");
        assertThat(ok).isEqualTo(new Approval(true, "T1"));
        assertThat(fail).isEqualTo(new Approval(false, null));
        CiOutput.record(LESSON, "adapter", "gateway.approve(42, 30000)\n→ SDK가 받은 값: orderNo=\"" + params.get("orderNo")
                + "\" (String), amount=" + params.get("amount") + " (" + params.get("amount").getClass().getSimpleName()
                + "), currency=" + params.get("currency") + "\n→ 돌려받은 것: " + ok
                + "\n\nSDK 결과 코드가 1001일 때\n→ 돌려받은 것: " + fail);
    }

    @Test
    @DisplayName("세 서비스가 PaymentGateway만 쓰면 나이스로 바꿀 때 서비스는 그대로이고 어댑터와 조립 코드만 바뀐다")
    void switchingVendor() {
        String[] services = {"OrderService", "RefundService", "SettlementJob"};
        Map<String, String> before = new HashMap<>();
        before.put("PaymentGateway", "interface PaymentGateway { boolean approve(long orderId, int amount); }");
        before.put("TossAdapter", "class TossAdapter implements PaymentGateway { public boolean approve(long o, int a) { return true; } }");
        before.put("Wiring", "class Wiring { PaymentGateway g = new TossAdapter(); }");
        for (String s : services) {
            before.put(s, "class %s { PaymentGateway gateway; }".formatted(s));
        }
        Map<String, String> after = new HashMap<>(before);
        after.put("NiceAdapter", "class NiceAdapter implements PaymentGateway { public boolean approve(long o, int a) { return true; } }");
        after.put("Wiring", "class Wiring { PaymentGateway g = new NiceAdapter(); }");

        assertThat(BrokenFiles.compile(after).ok()).isTrue();
        SourceChange c = SourceChange.between(before, after);

        assertThat(c.changed()).containsExactly("Wiring");
        assertThat(c.added()).containsExactly("NiceAdapter");
        assertThat(c.same()).contains(services);
        CiOutput.record(LESSON, "vendor", "[결제 대행사를 나이스로 바꾸기]  (Wiring이 조립 코드예요)\n" + c.describe());
    }

    // ── 코드 facade ──
    static class OrderFacade {
        private final List<String> calls;

        OrderFacade(List<String> calls) {
            this.calls = calls;
        }

        long placeOrder(String cart) {
            calls.add("재고 예약");
            calls.add("결제 승인");
            calls.add("배송 요청");
            calls.add("알림");
            return 1L;
        }
    }

    @Test
    @DisplayName("OrderFacade.placeOrder 한 번이 재고, 결제, 배송, 알림 순서로 부른다")
    void facadeOrder() {
        List<String> calls = new ArrayList<>();
        new OrderFacade(calls).placeOrder("장바구니");

        assertThat(calls).containsExactly("재고 예약", "결제 승인", "배송 요청", "알림");
        CiOutput.record(LESSON, "facade", "orderFacade.placeOrder(cart)\n→ 부른 순서: " + String.join(", ", calls));
    }

    @Test
    @DisplayName("Arrays.asList는 배열을 감싼 리스트라 set이 배열을 바꾸고 add는 UnsupportedOperationException")
    void arraysAsListIsAdapter() {
        String[] array = {"a", "b", "c"};
        List<String> list = Arrays.asList(array);
        list.set(0, "X");

        assertThat(array[0]).isEqualTo("X");
        assertThatThrownBy(() -> list.add("d")).isInstanceOf(UnsupportedOperationException.class);
        CiOutput.record(LESSON, "aslist", "List<String> list = Arrays.asList(array);\nlist.set(0, \"X\");\n→ array[0]: "
                + array[0] + "\n\nlist.add(\"d\")\n→ UnsupportedOperationException");
    }

    @Test
    @DisplayName("스프링 부트에서 SLF4J LoggerFactory가 돌려준 객체는 Logback의 Logger다")
    void slf4jIsFacade() {
        Logger log = LoggerFactory.getLogger(AdapterFacadeTest.class);
        String impl = log.getClass().getName();

        assertThat(impl).isEqualTo("ch.qos.logback.classic.Logger");
        CiOutput.record(LESSON, "slf4j", "org.slf4j.Logger log = LoggerFactory.getLogger(...);\nlog.getClass().getName()\n→ " + impl);
    }
}
