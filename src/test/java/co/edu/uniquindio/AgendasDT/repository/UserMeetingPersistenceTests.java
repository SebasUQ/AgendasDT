package co.edu.uniquindio.AgendasDT.repository;

import co.edu.uniquindio.AgendasDT.domain.*;
import co.edu.uniquindio.AgendasDT.domain.enums.*;
import co.edu.uniquindio.AgendasDT.dto.*;
import co.edu.uniquindio.AgendasDT.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "Administrador")
class UserMeetingPersistenceTests {
    @Autowired UserService userService;
    @Autowired MeetingService meetingService;
    @Autowired UserRepository users;
    @Autowired DentalChairRepository chairs;
    @Autowired MeetingAuditRepository audit;
    @Autowired PasswordEncoder passwords;

    @Test
    void persistsHashQueriesAgendaAndAuditsStatusUsingRealRepositories() {
        userService.create(new UserCreateRequest(1001L, "Ana", "Perez", LocalDate.of(1990, 1, 1),
                "patient@example.test", "+573001234567", UserRol.Paciente, "Example-password-123"));
        userService.create(new UserCreateRequest(1002L, "Carlos", "Perez", LocalDate.of(1980, 1, 1),
                "doctor@example.test", "+573001234568", UserRol.Odontologo));
        assertTrue(passwords.matches("Example-password-123", users.findById(1001L).orElseThrow().getPasswordHash()));
        assertEquals(1001L, users.findByNormalizedEmail("patient@example.test").orElseThrow().getId());
        DentalChair chair = new DentalChair(); chair.setId(1003L); chair.setName("Sillón de pruebas");
        chairs.saveAndFlush(chair);
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(2);
        var created = meetingService.create(new MeetingCreateRequest(1001L, 1002L, 1003L, start, start.plusHours(1), "Control"));
        assertEquals(created.id(), meetingService.listByDoctor(1002L, 0, 20).content().getFirst().id());
        var confirmed = meetingService.changeStatus(created.id(), new MeetingStatusRequest(MeetingStatus.Confirmada, created.version()));
        assertEquals(MeetingStatus.Confirmada, confirmed.status());
        assertTrue(confirmed.version() > created.version());
        assertTrue(audit.findAll().stream().anyMatch(a -> a.getMeetingId().equals(created.id())
                && a.getPreviousStatus() == MeetingStatus.Pendiente && a.getNewStatus() == MeetingStatus.Confirmada));
    }
}
