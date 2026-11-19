package co.edu.uniquindio.AgendasDT.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/** A finalized evolution. Corrections are separate addenda, never updates to this row. */
@Entity @Table(name = "clinical_record") @Immutable @Getter @Setter
public class ClinicalRecord {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false, unique = true, updatable = false)
    private Meeting meeting;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false) private User patient;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false, updatable = false) private User doctor;
    @Column(name = "doctor_name", nullable = false, length = 201) private String doctorName;
    @Column(name = "patient_name", nullable = false, length = 201) private String patientName;
    @Column(name = "professional_registration", nullable = false, length = 100) private String professionalRegistration;
    @Column(name = "recorded_at", nullable = false, updatable = false) private Instant recordedAt;
    @Column(nullable = false, length = 2000) private String reason;
    @Column(nullable = false, length = 4000) private String findings;
    @Column(nullable = false, length = 2000) private String diagnosis;
    @Column(name = "performed_procedure", nullable = false, length = 4000) private String performedProcedure;
    @Column(nullable = false, length = 4000) private String plan;
    @Column(name = "medical_background", length = 4000) private String medicalBackground;
    @Column(length = 2000) private String allergies;
    @Column(name = "chronic_conditions", length = 2000) private String chronicConditions;
    @ElementCollection @CollectionTable(name = "clinical_tooth_finding", joinColumns = @JoinColumn(name = "record_id"))
    @OrderColumn(name = "position") @Immutable
    private List<ToothFinding> teeth = new ArrayList<>();

    public enum Surface { Vestibular, Lingual, Mesial, Distal, Oclusal }
    public enum FindingType { Patologia, TratamientoRealizado }

    @Embeddable @Getter @Setter
    public static class ToothFinding {
        @Column(name = "tooth_fdi", nullable = false) private Integer tooth;
        @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Surface surface;
        @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private FindingType type;
        @Column(nullable = false, length = 500) private String description;
    }
}
