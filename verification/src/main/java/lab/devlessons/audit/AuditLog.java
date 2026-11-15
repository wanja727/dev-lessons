package lab.devlessons.audit;

import jakarta.persistence.*;
import java.time.Instant;

/** 누가, 언제, 무엇을, 변경 전후 값, 성공 여부 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String actor;
    private String action;
    private Long targetId;
    private String beforeValue;
    private String afterValue;
    private String status;
    private String error;
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(String actor, String action, Long targetId, String beforeValue, String afterValue, String status, String error) {
        this.actor = actor;
        this.action = action;
        this.targetId = targetId;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
        this.status = status;
        this.error = error;
        this.createdAt = Instant.now();
    }

    public String getActor() { return actor; }
    public String getAction() { return action; }
    public Long getTargetId() { return targetId; }
    public String getBeforeValue() { return beforeValue; }
    public String getAfterValue() { return afterValue; }
    public String getStatus() { return status; }
    public String getError() { return error; }
    public Instant getCreatedAt() { return createdAt; }
}
