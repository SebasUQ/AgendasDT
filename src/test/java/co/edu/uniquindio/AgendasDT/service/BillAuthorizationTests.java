package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.config.MethodSecurityConfig;
import co.edu.uniquindio.AgendasDT.config.TimeConfig;
import co.edu.uniquindio.AgendasDT.domain.enums.PaymentMethod;
import co.edu.uniquindio.AgendasDT.dto.*;
import co.edu.uniquindio.AgendasDT.exception.BillNotFoundException;
import co.edu.uniquindio.AgendasDT.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringJUnitConfig({BillService.class, MethodSecurityConfig.class, TimeConfig.class})
class BillAuthorizationTests {
    @Autowired BillService service;
    @MockitoBean BillRepository bills;
    @MockitoBean MeetingRepository meetings;
    @MockitoBean BillAuditRepository audit;

    @Test
    @WithMockUser(roles = "Paciente")
    void patientCannotBypassController() { assertDenied(); }

    @Test
    @WithMockUser(roles = "Odontologo")
    void dentistCannotBypassController() { assertDenied(); }

    @Test
    @WithMockUser(roles = "Administrador")
    void administratorCanReachService() {
        assertThrows(BillNotFoundException.class, () -> service.get(UUID.randomUUID()));
    }

    @Test
    @WithMockUser(roles = "Recepcionista")
    void receptionistCanReachService() {
        assertThrows(BillNotFoundException.class, () -> service.get(UUID.randomUUID()));
    }

    private void assertDenied() {
        var lines = List.of(new BillLineRequest("Limpieza", 1, new BigDecimal("50000")));
        assertThrows(AccessDeniedException.class, () -> service.create(new BillCreateRequest(UUID.randomUUID(), lines)));
        assertThrows(AccessDeniedException.class,
                () -> service.update(UUID.randomUUID(), new BillUpdateRequest(lines, 0L)));
        assertThrows(AccessDeniedException.class,
                () -> service.pay(UUID.randomUUID(), new BillPaymentRequest(PaymentMethod.Efectivo, 0L)));
        assertThrows(AccessDeniedException.class, () -> service.get(UUID.randomUUID()));
        assertThrows(AccessDeniedException.class, () -> service.getByMeeting(UUID.randomUUID()));
        verifyNoInteractions(bills, meetings, audit);
    }
}
