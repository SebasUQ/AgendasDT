package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.domain.*;
import co.edu.uniquindio.AgendasDT.domain.enums.*;
import co.edu.uniquindio.AgendasDT.dto.*;
import co.edu.uniquindio.AgendasDT.exception.*;
import co.edu.uniquindio.AgendasDT.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTests {
    @Mock MeetingRepository meetings;
    @Mock UserRepository users;
    @Mock DentalChairRepository chairs;
    @Mock MeetingAuditRepository audit;
    @Mock ApplicationEventPublisher events;
    private final Instant now = Instant.parse("2026-10-14T15:00:00Z");
    private final UUID id = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private final OffsetDateTime start = OffsetDateTime.parse("2026-10-15T09:00:00-05:00");
    private final OffsetDateTime end = start.plusHours(1);
    private MeetingService service;

    @BeforeEach
    void setup() {
        service = new MeetingService(meetings, users, chairs, audit, Clock.fixed(now, ZoneOffset.UTC), events);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("reception", "unused",
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_Recepcionista"))));
    }

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    @Test
    void createsPendingMeetingWithUtcTimesAndAudit() {
        resources();
        when(meetings.saveAndFlush(any())).thenAnswer(call -> {
            Meeting meeting = call.getArgument(0);
            meeting.setId(id);
            meeting.setVersion(0L);
            return meeting;
        });
        MeetingResponse response = service.create(create(start, end));
        assertEquals(id.toString(), response.confirmationCode());
        assertEquals(Instant.parse("2026-10-15T14:00:00Z"), response.startsAt());
        assertEquals(MeetingStatus.Pendiente, response.status());
        assertEquals("Control", response.reason());
        ArgumentCaptor<MeetingAudit> entry = ArgumentCaptor.forClass(MeetingAudit.class);
        verify(audit).save(entry.capture());
        assertEquals(id, entry.getValue().getMeetingId());
        assertEquals("reception", entry.getValue().getActor());
        assertEquals("CREATE", entry.getValue().getAction());
        assertEquals(now, entry.getValue().getOccurredAt());
        verify(events).publishEvent(new MeetingNotificationEvent(id, NotificationType.Confirmacion));
    }

    @Test
    void rejectsPastStart() {
        assertThrows(InvalidMeetingException.class, () -> service.create(create(start.minusDays(2), end)));
        verifyNoInteractions(users, chairs, meetings, audit);
    }

    @Test
    void rejectsStartExactlyNow() {
        assertThrows(InvalidMeetingException.class,
                () -> service.create(create(now.atOffset(ZoneOffset.UTC), end)));
        verifyNoInteractions(users, chairs, meetings, audit);
    }

    @Test
    void rejectsZeroOrNegativeDuration() {
        assertThrows(InvalidMeetingException.class, () -> service.create(create(start, start)));
        assertThrows(InvalidMeetingException.class, () -> service.create(create(start, start.minusMinutes(1))));
        verifyNoInteractions(users, chairs, meetings, audit);
    }

    @Test
    void rejectsMissingPatient() {
        assertThrows(MeetingNotFoundException.class, () -> service.create(create(start, end)));
        verifyNoInteractions(chairs, audit);
        verify(meetings, never()).saveAndFlush(any());
    }

    @Test
    void rejectsNonPatientRole() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1L, UserRol.Administrador)));
        assertThrows(InvalidMeetingException.class, () -> service.create(create(start, end)));
        verifyNoInteractions(chairs, audit);
    }

    @Test
    void rejectsNonDentistRole() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1L, UserRol.Paciente)));
        when(users.findById(2L)).thenReturn(Optional.of(user(2L, UserRol.Recepcionista)));
        assertThrows(InvalidMeetingException.class, () -> service.create(create(start, end)));
        verifyNoInteractions(chairs, audit);
    }

    @Test
    void rejectsMissingDentist() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1L, UserRol.Paciente)));
        assertThrows(MeetingNotFoundException.class, () -> service.create(create(start, end)));
        verifyNoInteractions(chairs, audit);
    }

    @Test
    void rejectsMissingChair() {
        userResources();
        assertThrows(MeetingNotFoundException.class, () -> service.create(create(start, end)));
        verifyNoInteractions(audit);
    }

    @Test
    void rejectsInactiveChair() {
        userResources();
        DentalChair chair = chair();
        chair.setActive(false);
        when(chairs.findById(3L)).thenReturn(Optional.of(chair));
        assertThrows(InvalidMeetingException.class, () -> service.create(create(start, end)));
        verifyNoInteractions(audit);
    }

    @Test
    void rejectsResourceOverlapBeforeSaving() {
        resources();
        when(meetings.overlaps(any(), anyCollection(), any(), any(), eq(1L), eq(2L), eq(3L))).thenReturn(true);
        assertThrows(MeetingConflictException.class, () -> service.create(create(start, end)));
        verify(meetings, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void databaseConflictDoesNotWriteAudit() {
        resources();
        when(meetings.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("exclusion violation"));
        assertThrows(DataIntegrityViolationException.class, () -> service.create(create(start, end)));
        verifyNoInteractions(audit, events);
    }

    @Test
    void reschedulesConfirmedMeetingAndPreservesPatientAndId() {
        Meeting meeting = existing();
        when(meetings.findById(id)).thenReturn(Optional.of(meeting));
        when(users.findById(2L)).thenReturn(Optional.of(user(2L, UserRol.Odontologo)));
        when(chairs.findById(3L)).thenReturn(Optional.of(chair()));
        doAnswer(call -> { meeting.setVersion(1L); return null; }).when(meetings).flush();
        var response = service.update(id, update(start.plusHours(2), end.plusHours(2), 0L));
        assertEquals(id, response.id());
        assertEquals(1L, response.patientId());
        assertEquals(1L, response.version());
        assertEquals(MeetingStatus.Pendiente, response.status());
        verify(meetings).overlaps(eq(id), anyCollection(), eq(start.plusHours(2).toInstant()),
                eq(end.plusHours(2).toInstant()), eq(1L), eq(2L), eq(3L));
        verify(audit).save(any());
        verify(events).publishEvent(new MeetingNotificationEvent(id, NotificationType.Reprogramacion));
    }

    @Test
    void reasonOnlyEditPreservesConfirmation() {
        Meeting meeting = existing();
        when(meetings.findById(id)).thenReturn(Optional.of(meeting));
        when(users.findById(2L)).thenReturn(Optional.of(user(2L, UserRol.Odontologo)));
        when(chairs.findById(3L)).thenReturn(Optional.of(chair()));
        assertEquals(MeetingStatus.Confirmada, service.update(id, update(start, end, 0L)).status());
        verifyNoInteractions(events);
    }

    @Test
    void rejectsStaleEditBeforeChangingData() {
        Meeting meeting = existing();
        when(meetings.findById(id)).thenReturn(Optional.of(meeting));
        assertThrows(MeetingConflictException.class, () -> service.update(id, update(start, end, 2L)));
        assertEquals(MeetingStatus.Confirmada, meeting.getStatus());
        verify(meetings, never()).flush();
        verifyNoInteractions(users, chairs, audit, events);
    }

    @Test
    void rejectsCancelledOrCompletedMeeting() {
        Meeting meeting = existing();
        when(meetings.findById(id)).thenReturn(Optional.of(meeting));
        for (MeetingStatus status : List.of(MeetingStatus.Cancelada, MeetingStatus.Completada)) {
            meeting.setStatus(status);
            assertThrows(MeetingConflictException.class, () -> service.update(id, update(start, end, 0L)));
        }
        verifyNoInteractions(users, chairs, audit);
    }

    @Test
    void rejectsEditingMeetingThatAlreadyStarted() {
        Meeting meeting = existing();
        meeting.setStartsAt(now);
        when(meetings.findById(id)).thenReturn(Optional.of(meeting));
        assertThrows(MeetingConflictException.class, () -> service.update(id, update(start, end, 0L)));
        verifyNoInteractions(users, chairs, audit);
    }

    @Test
    void editingExcludesCurrentMeetingButRejectsAnotherConflict() {
        Meeting meeting = existing();
        when(meetings.findById(id)).thenReturn(Optional.of(meeting));
        when(users.findById(2L)).thenReturn(Optional.of(user(2L, UserRol.Odontologo)));
        when(chairs.findById(3L)).thenReturn(Optional.of(chair()));
        when(meetings.overlaps(eq(id), anyCollection(), any(), any(), eq(1L), eq(2L), eq(3L))).thenReturn(true);
        assertThrows(MeetingConflictException.class,
                () -> service.update(id, update(start.plusHours(1), end.plusHours(1), 0L)));
        assertEquals(start.toInstant(), meeting.getStartsAt());
        verify(meetings, never()).flush();
        verifyNoInteractions(audit, events);
    }

    @Test
    void rejectsUnknownMeetingForEditAndRead() {
        assertThrows(MeetingNotFoundException.class, () -> service.update(id, update(start, end, 0L)));
        assertThrows(MeetingNotFoundException.class, () -> service.get(id));
        verifyNoInteractions(users, chairs, audit);
    }

    private MeetingCreateRequest create(OffsetDateTime start, OffsetDateTime end) {
        return new MeetingCreateRequest(1L, 2L, 3L, start, end, " Control ");
    }

    private MeetingUpdateRequest update(OffsetDateTime start, OffsetDateTime end, Long version) {
        return new MeetingUpdateRequest(2L, 3L, start, end, "Control actualizado", version);
    }

    private void userResources() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1L, UserRol.Paciente)));
        when(users.findById(2L)).thenReturn(Optional.of(user(2L, UserRol.Odontologo)));
    }

    private void resources() {
        userResources();
        when(chairs.findById(3L)).thenReturn(Optional.of(chair()));
    }

    private User user(Long id, UserRol role) {
        User user = new User();
        user.setId(id);
        user.setRol(role);
        return user;
    }

    private DentalChair chair() {
        DentalChair chair = new DentalChair();
        chair.setId(3L);
        chair.setName("Sillón 3");
        return chair;
    }

    private Meeting existing() {
        Meeting meeting = new Meeting();
        meeting.setId(id);
        meeting.setPatient(user(1L, UserRol.Paciente));
        meeting.setDoctor(user(2L, UserRol.Odontologo));
        meeting.setChair(chair());
        meeting.setStartsAt(start.toInstant());
        meeting.setEndsAt(end.toInstant());
        meeting.setReason("Control");
        meeting.setStatus(MeetingStatus.Confirmada);
        meeting.setVersion(0L);
        return meeting;
    }
}
