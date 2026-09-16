package co.edu.uniquindio.AgendasDT.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Setter
@Getter
@Table (name = "BillDetail")
@Entity
public class BillDetail {

    //Atributos
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID ID;

    @ManyToOne
    @JoinColumn(name = "BillID")
    private Bill billid;

    @OneToOne
    @JoinColumn(name = "TreatmentID")
    private Treatment treatmentid;
    private Double subTotal;

    //Constructor vacio
    public BillDetail() {

    }
}
