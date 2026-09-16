package co.edu.uniquindio.AgendasDT.domain;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

@Getter
@Setter
@Table (name = "Bill")
@Entity
public class Bill {

    // Atributos
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID ID;

    @ManyToOne
    @JoinColumn(name = "RecepcionistID")
    private User recepcionistID;

    @OneToOne
    @JoinColumn(name = "MeetingID")
    private Meeting meetingid;

    private Date date;

    private Double total;

    //Constructor vacio
    public Bill() {

    }
}
