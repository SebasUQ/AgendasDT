package co.edu.uniquindio.AgendasDT.domain;


import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class Bill {

    // Atributos
    private Long id;
    private Long recepcionistID;
    private Long meetingid;
    private Date date;
    private Double total;

    // Constructor
    public Bill(Long meetingid, Long recepcionistID, Long id, Date date, Double total) {
        this.meetingid = meetingid;
        this.recepcionistID = recepcionistID;
        this.id = id;
        this.date = date;
        this.total = total;
    }

    //Constructor vacio
    public Bill() {

    }
}
