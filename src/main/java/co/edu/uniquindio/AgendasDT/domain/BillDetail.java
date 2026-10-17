package co.edu.uniquindio.AgendasDT.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Table(name = "BillDetail")
@Entity
public class BillDetail {

    //Atributos
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "BillID", nullable = false, updatable = false)
    private Bill bill;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "SubTotal", nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    //Constructor vacio
    public BillDetail() {

    }
}
