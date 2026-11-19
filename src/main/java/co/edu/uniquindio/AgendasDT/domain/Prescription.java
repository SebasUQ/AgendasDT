package co.edu.uniquindio.AgendasDT.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

@Entity @Table(name = "clinical_prescription") @Immutable @Getter @Setter
public class Prescription {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false, updatable = false) private ClinicalRecord record;
    @Column(name = "issued_at", nullable = false) private Instant issuedAt;
    @Column(name = "doctor_name", nullable = false, length = 201) private String doctorName;
    @Column(name = "professional_registration", nullable = false, length = 100) private String professionalRegistration;
    @Column(name = "supersedes_id", unique = true) private UUID supersedesId;
    @Column(name = "correction_reason", length = 1000) private String correctionReason;
    @ElementCollection @CollectionTable(name = "clinical_prescription_item", joinColumns = @JoinColumn(name = "prescription_id"))
    @OrderColumn(name = "position") @Immutable private List<Item> items = new ArrayList<>();

    @Embeddable @Getter @Setter
    public static class Item {
        @Column(nullable = false, length = 200) private String medicine;
        @Column(nullable = false, length = 200) private String dose;
        @Column(nullable = false, length = 200) private String frequency;
        @Column(nullable = false, length = 200) private String duration;
        @Column(length = 1000) private String instructions;
    }
}
