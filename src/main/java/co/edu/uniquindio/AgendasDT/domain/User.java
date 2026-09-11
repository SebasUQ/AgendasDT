package co.edu.uniquindio.AgendasDT.domain;

import co.edu.uniquindio.AgendasDT.domain.enums.UserRol;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class User {

    //Atributos
    private Long ID;
    private String Name;
    private String LastName;
    private Date BirthDate;
    private String Email;
    private Long Phone;
    private UserRol Rol;

    //Constructor
    public User(Long ID, String name, String lastName, Date birthDate, String email, Long phone, UserRol rol) {
        this.ID = ID;
        Name = name;
        LastName = lastName;
        BirthDate = birthDate;
        Email = email;
        Phone = phone;
        Rol = rol;
    }

    //Constructor vacio
    public User() {

    }
}
