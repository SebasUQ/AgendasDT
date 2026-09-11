package co.edu.uniquindio.AgendasDT.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TDetail {

    //Atributos
    private Long ID;
    private Long TreatmentID;
    private Long MedicineID;
    private Integer Units;

    //Constructor
    public TDetail(Long ID, Long treatmentID, Long medicineID, Integer units) {
        this.ID = ID;
        TreatmentID = treatmentID;
        MedicineID = medicineID;
        Units = units;
    }

    //Constructor vacio
    public TDetail() {

    }
}
