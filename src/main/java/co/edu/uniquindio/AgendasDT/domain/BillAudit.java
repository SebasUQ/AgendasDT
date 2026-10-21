package co.edu.uniquindio.AgendasDT.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "bill_audit")
@Getter
public class BillAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "bill_id", nullable = false, updatable = false)
    private UUID billId;
    @Column(nullable = false, updatable = false)
    private String actor;
    @Column(nullable = false, updatable = false)
    private String action;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected BillAudit() { }

    public BillAudit(UUID billId, String actor, String action, Instant occurredAt) {
        this.billId = billId;
        this.actor = actor;
        this.action = action;
        this.occurredAt = occurredAt;
    }
}
