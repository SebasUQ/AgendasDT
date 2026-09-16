package co.edu.uniquindio.AgendasDT.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Table (name = "Treatment")
@Entity
public class Treatment {

    //Atributos
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID ID;
    private String Description;

    //Constructor vacio
    public Treatment() {

    }
}
