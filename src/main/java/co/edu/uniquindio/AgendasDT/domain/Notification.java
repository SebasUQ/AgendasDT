package co.edu.uniquindio.AgendasDT.domain;

import co.edu.uniquindio.AgendasDT.domain.enums.NotificationStatus;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class Notification {

    //Atributos
    private Long ID;
    private Long MeetingID;
    private String Type;
    private Date SendDate;
    private NotificationStatus Status;

    //Constructor
    public Notification(Long ID, Long meetingID, String type, Date sendDate, NotificationStatus status) {
        this.ID = ID;
        MeetingID = meetingID;
        Type = type;
        SendDate = sendDate;
        Status = status;
    }

    //Constructor vacio
    public Notification() {

    }
}
