package lab.devlessons.outbox;

import org.springframework.stereotype.Component;

/**
 * 폴링 릴레이: 아웃박스 테이블에서 아직 안 보낸 행을 읽어 브로커로 보내고, 보낸 행에 표시한다.
 * 실무에서는 @Scheduled로 짧은 간격마다 돌린다. 이 검증 코드는 테스트가 직접 부른다.
 */
@Component
public class OutboxRelay {

    private final OutboxEventRepository outbox;
    private final FakeBroker broker;
    /** 테스트에서 "보낸 뒤, 표시하기 전에 죽음"을 재현하려고 끼워 넣는 지점 */
    private volatile Runnable afterSend = () -> { };

    public OutboxRelay(OutboxEventRepository outbox, FakeBroker broker) {
        this.outbox = outbox;
        this.broker = broker;
    }

    public void setAfterSend(Runnable afterSend) {
        this.afterSend = afterSend;
    }

    /** 보낸 메시지 수를 돌려준다. 브로커가 죽어 있으면 멈추고 다음 실행에 다시 시도한다 */
    public int publishPending() {
        int sent = 0;
        for (OutboxEvent event : outbox.findTop100ByPublishedAtIsNullOrderByIdAsc()) {
            try {
                broker.send(event.toMessage());
            } catch (FakeBroker.BrokerUnavailableException e) {
                return sent; // 표시하지 않았으니 다음 실행에서 이 행부터 다시 보낸다
            }
            afterSend.run();
            event.markPublished();
            outbox.save(event);
            sent++;
        }
        return sent;
    }
}
