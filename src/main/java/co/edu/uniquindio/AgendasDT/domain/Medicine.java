package co.edu.uniquindio.AgendasDT.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Medicine {

    //Atributos
    private Long ID;
    private String MedicineName;
    private String Description;
    private Integer Stock;
    private Integer MinimumStock;
    private Double UnitCost;

    //Constructor
    public Medicine(Long ID, String medicineName, String description, Integer stock, Integer minimumStock, Double unitCost) {
        this.ID = ID;
        MedicineName = medicineName;
        Description = description;
        Stock = stock;
        MinimumStock = minimumStock;
        UnitCost = unitCost;
    }

    //Constructor vacio
    public Medicine() {

    }
}
