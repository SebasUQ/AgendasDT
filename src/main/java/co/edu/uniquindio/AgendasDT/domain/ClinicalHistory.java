package co.edu.uniquindio.AgendasDT.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClinicalHistory {

    //Atributos
    private Long ID;
    private Long PatientID;
    private Long TreatmentID;
    private String Diagnosis;
    private String Observations;

    //Constructor
    public ClinicalHistory(Long ID, Long patientID, Long treatmentID, String diagnosis, String observations) {
        this.ID = ID;
        PatientID = patientID;
        TreatmentID = treatmentID;
        Diagnosis = diagnosis;
        Observations = observations;
    }

    //Constructor vacio
    public ClinicalHistory() {

    }
}
