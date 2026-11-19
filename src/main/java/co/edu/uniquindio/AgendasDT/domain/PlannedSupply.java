package co.edu.uniquindio.AgendasDT.domain;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

@Entity @Table(name = "clinical_bill_supply") @Immutable @Getter @Setter
public class PlannedSupply {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "bill_id", nullable = false) private UUID billId;
    @Column(name = "supply_code", nullable = false, length = 80) private String code;
    @Column(nullable = false, length = 150) private String name;
    @Column(nullable = false, length = 40) private String unit;
    @Column(nullable = false) private Long units;
}
