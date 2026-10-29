package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.config.MethodSecurityConfig;
import co.edu.uniquindio.AgendasDT.config.TimeConfig;
import co.edu.uniquindio.AgendasDT.dto.*;
import co.edu.uniquindio.AgendasDT.exception.MeetingNotFoundException;
import co.edu.uniquindio.AgendasDT.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringJUnitConfig({MeetingService.class, MethodSecurityConfig.class, TimeConfig.class})
class MeetingAuthorizationTests {
    @Autowired MeetingService service;
    @MockitoBean MeetingRepository meetings;
    @MockitoBean UserRepository users;
    @MockitoBean DentalChairRepository chairs;
    @MockitoBean MeetingAuditRepository audit;

    @Test
    @WithMockUser(roles = "Paciente")
    void patientCannotBypassController() { assertDenied(); }

    @Test
    @WithMockUser(roles = "Odontologo")
    void dentistCannotBypassController() { assertDenied(); }

    @Test
    @WithMockUser(roles = "Administrador")
    void administratorCanReachService() {
        assertThrows(MeetingNotFoundException.class, () -> service.get(UUID.randomUUID()));
    }

    @Test
    @WithMockUser(roles = "Recepcionista")
    void receptionistCanReachService() {
        assertThrows(MeetingNotFoundException.class, () -> service.get(UUID.randomUUID()));
    }

    private void assertDenied() {
        OffsetDateTime start = OffsetDateTime.now().plusDays(1);
        var create = new MeetingCreateRequest(1L, 2L, 3L, start, start.plusHours(1), "Control");
        var update = new MeetingUpdateRequest(2L, 3L, start, start.plusHours(1), "Control", 0L);
        assertThrows(AccessDeniedException.class, () -> service.create(create));
        assertThrows(AccessDeniedException.class, () -> service.update(UUID.randomUUID(), update));
        assertThrows(AccessDeniedException.class, () -> service.get(UUID.randomUUID()));
        verifyNoInteractions(meetings, chairs, audit);
    }
}
