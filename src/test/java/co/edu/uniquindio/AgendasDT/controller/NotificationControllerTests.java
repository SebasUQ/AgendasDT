package co.edu.uniquindio.AgendasDT.controller;

import co.edu.uniquindio.AgendasDT.config.MethodSecurityConfig;
import co.edu.uniquindio.AgendasDT.domain.enums.*;
import co.edu.uniquindio.AgendasDT.dto.NotificationResponse;
import co.edu.uniquindio.AgendasDT.exception.*;
import co.edu.uniquindio.AgendasDT.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@Import({MethodSecurityConfig.class, ApiExceptionHandler.class})
class NotificationControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean NotificationService service;
    private static final UUID MEETING = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String URL = "/api/meetings/" + MEETING + "/notifications";

    @Test
    @WithMockUser(roles = "Recepcionista")
    void listsNotificationsOfMeeting() throws Exception {
        when(service.listByMeeting(MEETING)).thenReturn(List.of(new NotificationResponse(UUID.randomUUID(), MEETING,
                NotificationType.Confirmacion, NotificationChannel.SMS, "+573001234567", "Hola Ana",
                NotificationStatus.Enviada, Instant.parse("2026-11-02T15:00:00Z"), Instant.parse("2026-11-02T15:00:00Z"),
                Instant.parse("2026-11-02T15:01:00Z"), 1, null)));
        mvc.perform(get(URL)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("Confirmacion"))
                .andExpect(jsonPath("$[0].channel").value("SMS"))
                .andExpect(jsonPath("$[0].status").value("Enviada"));
    }

    @Test
    @WithMockUser(roles = "Administrador")
    void meetingWithoutNotificationsReturnsEmptyList() throws Exception {
        when(service.listByMeeting(MEETING)).thenReturn(List.of());
        mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    @WithMockUser(roles = "Administrador")
    void unknownMeetingIsNotFoundAndInvalidIdIsBadRequest() throws Exception {
        when(service.listByMeeting(MEETING)).thenThrow(new MeetingNotFoundException("La cita no existe."));
        mvc.perform(get(URL)).andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("La cita no existe."));
        mvc.perform(get("/api/meetings/no-es-un-id/notifications")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "Paciente")
    void patientCannotListNotifications() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "Odontologo")
    void dentistCannotListNotifications() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
