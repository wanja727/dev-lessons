package lab.devlessons.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 아웃박스 테이블의 한 행: "보내야 할 메시지". 업무 데이터와 같은 트랜잭션으로 저장한다.
 * published_at이 비어 있으면 아직 브로커에 보내지 않은 것.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 받는 쪽이 중복을 거를 때 쓰는 이벤트 고유 id */
    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String payload;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(String type, String payload) {
        this.eventId = UUID.randomUUID().toString();
        this.type = type;
        this.payload = payload;
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
    }

    public FakeBroker.Message toMessage() {
        return new FakeBroker.Message(eventId, type, payload);
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
