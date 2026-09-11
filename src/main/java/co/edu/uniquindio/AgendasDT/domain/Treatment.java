package co.edu.uniquindio.AgendasDT.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Treatment {

    //Atributos
    private Long ID;
    private String Description;

    //Constructor
    public Treatment(Long ID, String description) {
        this.ID = ID;
        Description = description;
    }

    //Constructor vacio
    public Treatment() {

    }
}
