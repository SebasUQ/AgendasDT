package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.config.MethodSecurityConfig;
import co.edu.uniquindio.AgendasDT.domain.enums.UserRol;
import co.edu.uniquindio.AgendasDT.dto.UserCreateRequest;
import co.edu.uniquindio.AgendasDT.dto.UserUpdateRequest;
import co.edu.uniquindio.AgendasDT.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringJUnitConfig({UserService.class, MethodSecurityConfig.class, co.edu.uniquindio.AgendasDT.config.PasswordConfig.class})
class UserAuthorizationTests {
    @Autowired UserService service;
    @MockitoBean UserRepository users;
    @MockitoBean UserAuditRepository audit;

    @Test
    @WithMockUser(roles = "Paciente")
    void patientCannotCreateEvenWhenRequestAssignsAdministratorRole() {
        var request = new UserCreateRequest(123L, "Ana", "Perez", LocalDate.of(1995, 5, 12),
                "ana@example.com", "+573001234567", UserRol.Administrador);
        assertThrows(AccessDeniedException.class, () -> service.create(request));
        verifyNoInteractions(users, audit);
    }

    @Test
    @WithMockUser(roles = "Recepcionista")
    void receptionistCannotEditRoles() {
        var request = new UserUpdateRequest("Ana", "Perez", LocalDate.of(1995, 5, 12),
                "ana@example.com", "+573001234567", UserRol.Administrador, 0L);
        assertThrows(AccessDeniedException.class, () -> service.update(123L, request));
        verifyNoInteractions(users, audit);
    }

    @Test
    @WithMockUser(roles = "Odontologo")
    void dentistCannotReadUsersThroughAdministrationModule() {
        assertThrows(AccessDeniedException.class, () -> service.get(123L));
        verifyNoInteractions(users, audit);
    }
}
