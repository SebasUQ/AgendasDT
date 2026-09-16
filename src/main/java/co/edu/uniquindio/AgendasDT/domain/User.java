package co.edu.uniquindio.AgendasDT.domain;

import co.edu.uniquindio.AgendasDT.domain.enums.UserRol;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


import java.util.Date;

@Getter
@Setter
@Table (name = "User")
@Entity
public class User {

    //Atributos
    @Id
    private Long ID;
    private String Name;
    private String LastName;
    private Date BirthDate;
    private String Email;
    private Long Phone;

    @Enumerated(EnumType.STRING)
    private UserRol Rol;

    //Constructor vacio
    public User() {

    }
}
