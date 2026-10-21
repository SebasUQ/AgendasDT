package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.domain.Meeting;
import co.edu.uniquindio.AgendasDT.domain.User;
import co.edu.uniquindio.AgendasDT.domain.enums.NotificationType;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

final class NotificationMessages {
    static final ZoneId CLINIC_ZONE = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private NotificationMessages() { }

    static String forMeeting(NotificationType type, Meeting meeting) {
        ZonedDateTime start = meeting.getStartsAt().atZone(CLINIC_ZONE);
        User doctor = meeting.getDoctor();
        String when = "el " + DATE.format(start) + " a las " + TIME.format(start)
                + " con el odontólogo " + doctor.getName() + " " + doctor.getLastName();
        String greeting = "Hola " + meeting.getPatient().getName() + ", ";
        return switch (type) {
            case Confirmacion -> greeting + "su cita en Doc-Tooth quedó agendada para " + when
                    + ". Código: " + meeting.getId() + ".";
            case Reprogramacion -> greeting + "su cita en Doc-Tooth fue reprogramada para " + when
                    + ". Código: " + meeting.getId() + ".";
            case Recordatorio -> greeting + "le recordamos su cita en Doc-Tooth " + when
                    + ". Si no puede asistir, comuníquese con la clínica.";
        };
    }
}
