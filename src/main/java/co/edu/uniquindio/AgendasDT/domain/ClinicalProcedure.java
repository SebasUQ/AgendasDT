package co.edu.uniquindio.AgendasDT.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "clinical_procedure") @Getter @Setter
public class ClinicalProcedure {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, unique = true, length = 40) private String code;
    @Column(nullable = false, length = 150) private String name;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal price;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "valid_from", nullable = false) private Instant validFrom;
    @Column(name = "valid_until") private Instant validUntil;
    @Version private Long version;
    @ElementCollection @CollectionTable(name = "clinical_procedure_supply", joinColumns = @JoinColumn(name = "procedure_id"))
    @OrderColumn(name = "position") private List<Supply> supplies = new ArrayList<>();

    @Embeddable @Getter @Setter
    public static class Supply {
        @Column(name = "supply_code", nullable = false, length = 80) private String code;
        @Column(nullable = false, length = 150) private String name;
        @Column(nullable = false, length = 40) private String unit;
        @Column(nullable = false) private Integer units;
    }
}
