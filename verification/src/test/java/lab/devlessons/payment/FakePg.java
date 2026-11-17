package lab.devlessons.payment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 가짜 PG사. 승인 요청을 받으면 먼저 승인을 기록하고(카드사에 돈이 묶임), 그다음 응답을 늦게 보낼 수 있다.
 * 망 취소는 주문 번호만으로 "방금 그 승인"을 취소한다.
 */
class FakePg {

    enum Status { APPROVED, CANCELED }

    static final class Txn {
        final String orderId;
        final long amount;
        volatile Status status = Status.APPROVED;

        Txn(String orderId, long amount) {
            this.orderId = orderId;
            this.amount = amount;
        }
    }

    record SettlementLine(String orderId, long amount, Status status) {
    }

    private final Map<String, Txn> txns = new ConcurrentHashMap<>();
    volatile long responseDelayMillis;

    /** 승인: PG 쪽에서는 이미 처리된 뒤 응답이 늦게 나갈 수 있다 */
    String approve(String orderId, long amount) {
        txns.putIfAbsent(orderId, new Txn(orderId, amount)); // 같은 주문 번호는 한 번만 (PG도 멱등)
        sleep(responseDelayMillis);
        return "APPROVED";
    }

    /** 망 취소: 결과를 모르는 승인을 주문 번호로 취소한다. 승인이 없으면 아무 일도 없다 */
    void netCancel(String orderId) {
        Txn t = txns.get(orderId);
        if (t != null) {
            t.status = Status.CANCELED;
        }
    }

    Status status(String orderId) {
        Txn t = txns.get(orderId);
        return t == null ? null : t.status;
    }

    /** 대사용 거래 내역 (실제로는 PG가 매일 파일이나 API로 준다) */
    List<SettlementLine> settlement() {
        List<SettlementLine> out = new ArrayList<>();
        txns.values().forEach(t -> out.add(new SettlementLine(t.orderId, t.amount, t.status)));
        return out;
    }

    void put(String orderId, long amount, Status status) {
        Txn t = new Txn(orderId, amount);
        t.status = status;
        txns.put(orderId, t);
    }

    void clear() {
        txns.clear();
        responseDelayMillis = 0;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
