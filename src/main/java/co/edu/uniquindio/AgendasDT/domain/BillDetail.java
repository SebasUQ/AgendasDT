package co.edu.uniquindio.AgendasDT.domain;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class BillDetail {

    //Atributos
    private Long id;
    private Long billid;
    private Long treatmentid;
    private Double subTotal;

    // Constructor
    public BillDetail(Long id, Long billid, Long treatmentid, Double subTotal) {
        this.id = id;
        this.billid = billid;
        this.treatmentid = treatmentid;
        this.subTotal = subTotal;
    }

    //Constructor vacio
    public BillDetail() {

    }
}
