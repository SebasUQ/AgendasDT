package co.edu.uniquindio.AgendasDT.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

@Entity @Table(name = "clinical_audit") @Immutable @Getter @Setter
public class ClinicalAudit {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "doctor_id", nullable = false) private Long doctorId;
    @Column(name = "patient_id") private Long patientId;
    @Column(name = "record_id") private UUID recordId;
    @Column(nullable = false, length = 50) private String action;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
}
