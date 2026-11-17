package lab.devlessons.outbox;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/**
 * 메시지 브로커(Kafka, RabbitMQ 등) 대신 쓰는 가짜. 받은 메시지를 목록에 쌓는다.
 * DB 트랜잭션과 상관없이 send 하는 순간 "밖으로 나간" 것으로 본다. 진짜 브로커도 그렇다.
 */
@Component
public class FakeBroker {

    public record Message(String eventId, String type, String payload) {
    }

    public static class BrokerUnavailableException extends RuntimeException {
        public BrokerUnavailableException() {
            super("브로커에 연결할 수 없음");
        }
    }

    private final List<Message> received = new CopyOnWriteArrayList<>();
    private volatile boolean down;

    public void send(Message message) {
        if (down) {
            throw new BrokerUnavailableException();
        }
        received.add(message);
    }

    public List<Message> received() {
        return List.copyOf(received);
    }

    public void setDown(boolean down) {
        this.down = down;
    }

    public void clear() {
        received.clear();
        down = false;
    }
}
