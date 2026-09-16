package co.edu.uniquindio.AgendasDT.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Table (name = "TDetail")
@Entity
public class TDetail {

    //Atributos
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID ID;

    @ManyToOne
    @JoinColumn(name = "TreatmentID")
    private Treatment TreatmentID;

    @ManyToOne
    @JoinColumn(name = "MedicineID")
    private Medicine MedicineID;

    private Integer Units;

    //Constructor vacio
    public TDetail() {

    }
}
