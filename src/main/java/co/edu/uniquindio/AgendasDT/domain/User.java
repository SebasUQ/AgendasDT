package co.edu.uniquindio.AgendasDT.domain;

import co.edu.uniquindio.AgendasDT.domain.enums.UserRol;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnore;


import java.time.LocalDate;

@Getter
@Setter
@Table(name = "\"User\"")
@Entity
public class User {

    //Atributos
    @Id
    @Column(name = "ID")
    private Long id;
    @Column(name = "Name", nullable = false, length = 100)
    private String name;
    @Column(name = "LastName", nullable = false, length = 100)
    private String lastName;
    @Column(name = "BirthDate", nullable = false)
    private LocalDate birthDate;
    @Column(name = "Email", nullable = false, length = 254)
    private String email;
    @Column(name = "Phone", nullable = false, length = 20)
    private String phone;

    @JsonIgnore
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "Rol", nullable = false)
    private UserRol rol;

    @Version
    @Column(name = "Version", nullable = false)
    private Long version;

    //Constructor vacio
    public User() {

    }
}
