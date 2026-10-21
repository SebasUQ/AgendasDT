package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.domain.Meeting;
import co.edu.uniquindio.AgendasDT.domain.User;
import co.edu.uniquindio.AgendasDT.domain.enums.NotificationType;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NotificationMessagesTests {
    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Test
    void confirmationShowsColombianTimeDoctorAndCode() {
        assertEquals("Hola Ana, su cita en Doc-Tooth quedó agendada para el 22/10/2026 a las 09:00"
                        + " con el odontólogo Carlos Rojas. Código: " + ID + ".",
                NotificationMessages.forMeeting(NotificationType.Confirmacion, meeting("2026-10-22T14:00:00Z", "Ana")));
    }

    @Test
    void reschedulingShowsNewTime() {
        assertEquals("Hola Ana, su cita en Doc-Tooth fue reprogramada para el 22/10/2026 a las 15:30"
                        + " con el odontólogo Carlos Rojas. Código: " + ID + ".",
                NotificationMessages.forMeeting(NotificationType.Reprogramacion, meeting("2026-10-22T20:30:00Z", "Ana")));
    }

    @Test
    void reminderAsksToContactClinic() {
        assertEquals("Hola Ana, le recordamos su cita en Doc-Tooth el 22/10/2026 a las 09:00"
                        + " con el odontólogo Carlos Rojas. Si no puede asistir, comuníquese con la clínica.",
                NotificationMessages.forMeeting(NotificationType.Recordatorio, meeting("2026-10-22T14:00:00Z", "Ana")));
    }

    @Test
    void usesClinicDateWhenUtcIsAlreadyNextDay() {
        String message = NotificationMessages.forMeeting(NotificationType.Recordatorio,
                meeting("2026-10-23T03:30:00Z", "Ana"));
        assertTrue(message.contains("el 22/10/2026 a las 22:30"), message);
    }

    @Test
    void longestNamesFitDatabaseColumn() {
        Meeting meeting = meeting("2026-10-22T14:00:00Z", "x".repeat(100));
        meeting.getDoctor().setName("y".repeat(100));
        meeting.getDoctor().setLastName("z".repeat(100));
        for (NotificationType type : NotificationType.values()) {
            assertTrue(NotificationMessages.forMeeting(type, meeting).length() <= 500);
        }
    }

    private Meeting meeting(String start, String patientName) {
        User patient = new User();
        patient.setName(patientName);
        User doctor = new User();
        doctor.setName("Carlos");
        doctor.setLastName("Rojas");
        Meeting meeting = new Meeting();
        meeting.setId(ID);
        meeting.setPatient(patient);
        meeting.setDoctor(doctor);
        meeting.setStartsAt(Instant.parse(start));
        return meeting;
    }
}
