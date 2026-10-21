package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.config.MethodSecurityConfig;
import co.edu.uniquindio.AgendasDT.config.TimeConfig;
import co.edu.uniquindio.AgendasDT.domain.enums.NotificationType;
import co.edu.uniquindio.AgendasDT.exception.MeetingNotFoundException;
import co.edu.uniquindio.AgendasDT.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringJUnitConfig({NotificationService.class, MethodSecurityConfig.class, TimeConfig.class})
class NotificationAuthorizationTests {
    @Autowired NotificationService service;
    @MockitoBean NotificationRepository notifications;
    @MockitoBean MeetingRepository meetings;

    @Test
    @WithMockUser(roles = "Paciente")
    void patientCannotListNotifications() { assertDenied(); }

    @Test
    @WithMockUser(roles = "Odontologo")
    void dentistCannotListNotifications() { assertDenied(); }

    @Test
    @WithMockUser(roles = "Administrador")
    void administratorCanListNotifications() {
        assertThrows(MeetingNotFoundException.class, () -> service.listByMeeting(UUID.randomUUID()));
    }

    @Test
    @WithMockUser(roles = "Recepcionista")
    void receptionistCanListNotifications() {
        assertThrows(MeetingNotFoundException.class, () -> service.listByMeeting(UUID.randomUUID()));
    }

    @Test
    void schedulerRunsWithoutLoggedUser() {
        assertEquals(0, service.createReminders());
        assertThrows(MeetingNotFoundException.class, () -> service.onMeetingScheduled(
                new MeetingNotificationEvent(UUID.randomUUID(), NotificationType.Confirmacion)));
    }

    private void assertDenied() {
        assertThrows(AccessDeniedException.class, () -> service.listByMeeting(UUID.randomUUID()));
        verifyNoInteractions(notifications, meetings);
    }
}
