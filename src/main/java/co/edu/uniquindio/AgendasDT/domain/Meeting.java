package co.edu.uniquindio.AgendasDT.domain;

import co.edu.uniquindio.AgendasDT.domain.enums.MeetingStatus;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class Meeting {

    //Atributos
    private Long ID;
    private Long PatientID;
    private Long DoctorID;
    private Date Date;
    private MeetingStatus Status;

    //Constructor
    public Meeting(Long ID, Long patientID, Long doctorID, Date date, MeetingStatus status) {
        this.ID = ID;
        PatientID = patientID;
        DoctorID = doctorID;
        Date = date;
        Status = status;
    }

    //Constructor vacio
    public Meeting() {

    }
}
