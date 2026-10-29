package co.edu.uniquindio.AgendasDT.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import co.edu.uniquindio.AgendasDT.domain.enums.MeetingStatus;

@Entity
@Table(name = "meeting_audit")
@Getter
public class MeetingAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "meeting_id", nullable = false, updatable = false)
    private UUID meetingId;
    @Column(nullable = false, updatable = false)
    private String actor;
    @Column(nullable = false, updatable = false)
    private String action;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", updatable = false)
    private MeetingStatus previousStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", updatable = false)
    private MeetingStatus newStatus;

    protected MeetingAudit() { }

    public MeetingAudit(UUID meetingId, String actor, String action, Instant occurredAt) {
        this.meetingId = meetingId;
        this.actor = actor;
        this.action = action;
        this.occurredAt = occurredAt;
    }

    public MeetingAudit(UUID meetingId, String actor, String action, Instant occurredAt,
                        MeetingStatus previousStatus, MeetingStatus newStatus) {
        this(meetingId, actor, action, occurredAt);
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
    }
}
