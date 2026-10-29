package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.domain.*;
import co.edu.uniquindio.AgendasDT.domain.enums.*;
import co.edu.uniquindio.AgendasDT.dto.*;
import co.edu.uniquindio.AgendasDT.exception.MeetingConflictException;
import co.edu.uniquindio.AgendasDT.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeetingAccessAndStatusTests {
    @Mock MeetingRepository meetings;
    @Mock UserRepository users;
    @Mock DentalChairRepository chairs;
    @Mock MeetingAuditRepository audit;
    @Mock ApplicationEventPublisher events;
    private final Instant now = Instant.parse("2026-10-28T12:00:00Z");
    private final UUID id = UUID.randomUUID();
    private MeetingService service;

    @BeforeEach
    void setup() {
        service = new MeetingService(meetings, users, chairs, audit, Clock.fixed(now, ZoneOffset.UTC), events);
        login("staff", UserRol.Recepcionista);
    }

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    @Test
    void patientReadsOwnMeeting() {
        scoped(1L, UserRol.Paciente);
        existing();
        assertEquals(id, service.get(id).id());
    }

    @Test
    void patientCannotReadAnotherPatientsMeeting() {
        scoped(99L, UserRol.Paciente);
        existing();
        assertThrows(AccessDeniedException.class, () -> service.get(id));
    }

    @Test
    void dentistReadsOnlyAssignedMeetings() {
        User dentist = scoped(2L, UserRol.Odontologo);
        existing();
        assertEquals(id, service.get(id).id());
        dentist.setId(99L);
        assertThrows(AccessDeniedException.class, () -> service.get(id));
    }

    @Test
    void unresolvedAuthenticatedIdentityIsDenied() {
        login("unknown@example.com", UserRol.Paciente);
        assertThrows(AccessDeniedException.class, () -> service.get(id));
        verifyNoInteractions(meetings);
    }

    @Test
    void grantedRoleMustMatchPersistedRole() {
        scoped(1L, UserRol.Paciente).setRol(UserRol.Odontologo);
        assertThrows(AccessDeniedException.class, () -> service.get(id));
        verifyNoInteractions(meetings);
    }

    @Test
    void patientCannotCreateForAnotherPatient() {
        scoped(1L, UserRol.Paciente);
        assertThrows(AccessDeniedException.class, () -> service.create(new MeetingCreateRequest(99L, 2L, 3L,
                now.plusSeconds(172800).atOffset(ZoneOffset.UTC), now.plusSeconds(176400).atOffset(ZoneOffset.UTC), "Control")));
        verifyNoInteractions(meetings, chairs, audit, events);
    }

    @Test
    void dentistCannotReassignMeetingToAnotherDentist() {
        scoped(2L, UserRol.Odontologo);
        existing();
        assertThrows(AccessDeniedException.class, () -> service.update(id, new MeetingUpdateRequest(99L, 3L,
                now.plusSeconds(172800).atOffset(ZoneOffset.UTC), now.plusSeconds(176400).atOffset(ZoneOffset.UTC), "Control", 0L)));
        verifyNoInteractions(audit, events);
    }

    @Test
    void patientDoctorListIsFilteredToOwnMeetings() {
        scoped(1L, UserRol.Paciente);
        when(users.findById(2L)).thenReturn(Optional.of(user(2L, UserRol.Odontologo)));
        when(meetings.findByDoctor_IdAndPatient_IdOrderByStartsAtAscIdAsc(2L, 1L, PageRequest.of(0, 20)))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));
        assertEquals(0, service.listByDoctor(2L, 0, 20).totalElements());
        verify(meetings, never()).findByDoctor_IdOrderByStartsAtAscIdAsc(any(), any());
    }

    @Test
    void dentistCannotListAnotherDentistsAgenda() {
        scoped(2L, UserRol.Odontologo);
        assertThrows(AccessDeniedException.class, () -> service.listByDoctor(99L, 0, 20));
        verifyNoInteractions(meetings);
    }

    @Test
    void staffListsPaginatedAgenda() {
        Meeting meeting = meeting();
        when(users.findById(2L)).thenReturn(Optional.of(user(2L, UserRol.Odontologo)));
        when(meetings.findByDoctor_IdOrderByStartsAtAscIdAsc(2L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(meeting), PageRequest.of(0, 20), 1));
        var result = service.listByDoctor(2L, 0, 20);
        assertEquals(1, result.totalElements());
        assertEquals(id, result.content().getFirst().id());
    }

    @Test
    void patientConfirmsOwnMeetingAndAuditContainsTransition() {
        scoped(1L, UserRol.Paciente);
        existing();
        assertEquals(MeetingStatus.Confirmada, service.changeStatus(id, new MeetingStatusRequest(MeetingStatus.Confirmada, 0L)).status());
        ArgumentCaptor<MeetingAudit> saved = ArgumentCaptor.forClass(MeetingAudit.class);
        verify(audit).save(saved.capture());
        assertEquals(MeetingStatus.Pendiente, saved.getValue().getPreviousStatus());
        assertEquals(MeetingStatus.Confirmada, saved.getValue().getNewStatus());
    }

    @Test
    void patientCancellationRequires24HoursAndAllowsExactBoundary() {
        scoped(1L, UserRol.Paciente);
        Meeting meeting = existing();
        meeting.setStartsAt(now.plusSeconds(86399));
        var request = new MeetingStatusRequest(MeetingStatus.Cancelada, 0L);
        assertThrows(MeetingConflictException.class, () -> service.changeStatus(id, request));
        assertEquals(MeetingStatus.Pendiente, meeting.getStatus());
        meeting.setStartsAt(now.plusSeconds(86400));
        assertEquals(MeetingStatus.Cancelada, service.changeStatus(id, request).status());
    }

    @Test
    void staffCanCancelLessThan24HoursAhead() {
        existing().setStartsAt(now.plusSeconds(3600));
        assertEquals(MeetingStatus.Cancelada, service.changeStatus(id, new MeetingStatusRequest(MeetingStatus.Cancelada, 0L)).status());
    }

    @Test
    void patientCannotMarkCompletedEvenOnOwnMeeting() {
        scoped(1L, UserRol.Paciente);
        existing();
        assertThrows(AccessDeniedException.class,
                () -> service.changeStatus(id, new MeetingStatusRequest(MeetingStatus.Completada, 0L)));
        verifyNoInteractions(audit);
    }

    @Test
    void cannotReopenCancelledOrCompletedMeeting() {
        Meeting meeting = existing();
        for (MeetingStatus terminal : List.of(MeetingStatus.Cancelada, MeetingStatus.Completada)) {
            meeting.setStatus(terminal);
            assertThrows(MeetingConflictException.class,
                    () -> service.changeStatus(id, new MeetingStatusRequest(MeetingStatus.Confirmada, 0L)));
        }
        verifyNoInteractions(audit);
    }

    @Test
    void completedRequiresConfirmedMeetingThatHasStarted() {
        Meeting meeting = existing();
        meeting.setStatus(MeetingStatus.Confirmada);
        var request = new MeetingStatusRequest(MeetingStatus.Completada, 0L);
        assertThrows(MeetingConflictException.class, () -> service.changeStatus(id, request));
        meeting.setStartsAt(now.minusSeconds(3600));
        assertEquals(MeetingStatus.Completada, service.changeStatus(id, request).status());
    }

    @Test
    void staleStatusChangeAndIdempotentRequestDoNotWriteAudit() {
        existing();
        assertThrows(MeetingConflictException.class,
                () -> service.changeStatus(id, new MeetingStatusRequest(MeetingStatus.Confirmada, 1L)));
        assertEquals(MeetingStatus.Pendiente, service.changeStatus(id, new MeetingStatusRequest(MeetingStatus.Pendiente, 0L)).status());
        verifyNoInteractions(audit);
        verify(meetings, never()).flush();
    }

    @Test
    void patientCannotRescheduleWithLessThan24Hours() {
        scoped(1L, UserRol.Paciente);
        existing().setStartsAt(now.plusSeconds(3600));
        assertThrows(MeetingConflictException.class, () -> service.update(id, new MeetingUpdateRequest(2L, 3L,
                now.plusSeconds(172800).atOffset(ZoneOffset.UTC), now.plusSeconds(176400).atOffset(ZoneOffset.UTC), "Control", 0L)));
        verifyNoInteractions(audit, events, chairs);
    }

    private void login(String name, UserRol role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(name, "unused",
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private User scoped(Long id, UserRol role) {
        User actor = user(id, role);
        login("actor@example.com", role);
        when(users.findByNormalizedEmail("actor@example.com")).thenReturn(Optional.of(actor));
        return actor;
    }

    private User user(Long id, UserRol role) {
        User user = new User(); user.setId(id); user.setRol(role); return user;
    }

    private Meeting existing() {
        Meeting meeting = meeting();
        when(meetings.findById(id)).thenReturn(Optional.of(meeting));
        return meeting;
    }

    private Meeting meeting() {
        Meeting meeting = new Meeting();
        meeting.setId(id); meeting.setVersion(0L);
        meeting.setPatient(user(1L, UserRol.Paciente)); meeting.setDoctor(user(2L, UserRol.Odontologo));
        DentalChair chair = new DentalChair(); chair.setId(3L); meeting.setChair(chair);
        meeting.setStartsAt(now.plusSeconds(172800)); meeting.setEndsAt(now.plusSeconds(176400));
        meeting.setReason("Control"); meeting.setStatus(MeetingStatus.Pendiente);
        return meeting;
    }
}
