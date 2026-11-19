package co.edu.uniquindio.AgendasDT.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

@Entity @Table(name = "clinical_addendum") @Immutable @Getter @Setter
public class ClinicalAddendum {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false) private ClinicalRecord record;
    @Column(name = "doctor_id", nullable = false) private Long doctorId;
    @Column(name = "doctor_name", nullable = false, length = 201) private String doctorName;
    @Column(name = "recorded_at", nullable = false) private Instant recordedAt;
    @Column(nullable = false, length = 1000) private String reason;
    @Column(nullable = false, length = 8000) private String content;
}
