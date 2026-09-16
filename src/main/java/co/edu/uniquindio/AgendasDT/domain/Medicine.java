package co.edu.uniquindio.AgendasDT.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Table(name = "Medicine")
@Entity
public class Medicine {

    //Atributos
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID ID;
    private String MedicineName;
    private String Description;
    private Integer Stock;
    private Integer MinimumStock;
    private Double UnitCost;

    //Constructor vacio
    public Medicine() {

    }
}
