package co.edu.uniquindio.AgendasDT.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Table (name = "ClinicalHistory")
@Entity
public class ClinicalHistory {

    //Atributos
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID ID;

    @ManyToOne
    @JoinColumn(name = "PatientID")
    private User PatientID;

    @OneToOne
    @JoinColumn(name = "TreatmentID")
    private Treatment TreatmentID;

    private String Diagnosis;

    private String Observations;

    //Constructor vacio
    public ClinicalHistory() {

    }
}
