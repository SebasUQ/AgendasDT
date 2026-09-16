package co.edu.uniquindio.AgendasDT.domain;

import co.edu.uniquindio.AgendasDT.domain.enums.MeetingStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

@Getter
@Setter
@Table (name = "Meeting")
@Entity
public class Meeting {

    //Atributos
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID ID;

    @OneToOne
    @JoinColumn(name = "PatientID")
    private User PatientID;

    @OneToOne
    @JoinColumn(name = "DoctorID")
    private User DoctorID;

    private Date Date;

    @Enumerated(EnumType.STRING)
    private MeetingStatus Status;

    //Constructor vacio
    public Meeting() {

    }
}
