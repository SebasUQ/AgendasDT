package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.config.MethodSecurityConfig;
import co.edu.uniquindio.AgendasDT.config.TimeConfig;
import co.edu.uniquindio.AgendasDT.domain.*;
import co.edu.uniquindio.AgendasDT.domain.enums.*;
import co.edu.uniquindio.AgendasDT.dto.*;
import co.edu.uniquindio.AgendasDT.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real Spring event delivery from the meeting service to the notification service. */
@SpringJUnitConfig({MeetingService.class, NotificationService.class, MethodSecurityConfig.class, TimeConfig.class})
@WithMockUser(roles = "Recepcionista")
class MeetingNotificationWiringTests {
    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    @Autowired MeetingService meetingService;
    @MockitoBean MeetingRepository meetings;
    @MockitoBean UserRepository users;
    @MockitoBean DentalChairRepository chairs;
    @MockitoBean MeetingAuditRepository audit;
    @MockitoBean NotificationRepository notifications;
    private final OffsetDateTime start = OffsetDateTime.now().plusDays(3).withNano(0);

    @BeforeEach
    void resources() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1L, UserRol.Paciente, "Ana")));
        when(users.findById(2L)).thenReturn(Optional.of(user(2L, UserRol.Odontologo, "Carlos")));
        DentalChair chair = new DentalChair();
        chair.setId(3L);
        when(chairs.findById(3L)).thenReturn(Optional.of(chair));
    }

    @Test
    @SuppressWarnings("unchecked")
    void creatingMeetingQueuesConfirmation() {
        savesNewMeeting();
        meetingService.create(new MeetingCreateRequest(1L, 2L, 3L, start, start.plusHours(1), "Control"));
        ArgumentCaptor<List<Notification>> saved = ArgumentCaptor.forClass(List.class);
        verify(notifications).saveAll(saved.capture());
        assertEquals(2, saved.getValue().size());
        assertTrue(saved.getValue().stream().allMatch(n -> n.getType() == NotificationType.Confirmacion
                && n.getMeeting().getId().equals(ID) && n.getMessage().startsWith("Hola Ana")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reschedulingMeetingQueuesNewTime() {
        Meeting meeting = existing();
        when(meetings.findById(ID)).thenReturn(Optional.of(meeting));
        meetingService.update(ID, new MeetingUpdateRequest(2L, 3L, start.plusHours(2), start.plusHours(3), "Control", 0L));
        verify(notifications).discardPending(ID, "La cita fue reprogramada.",
                NotificationStatus.Pendiente, NotificationStatus.Descartada);
        ArgumentCaptor<List<Notification>> saved = ArgumentCaptor.forClass(List.class);
        verify(notifications).saveAll(saved.capture());
        assertTrue(saved.getValue().stream().allMatch(n -> n.getType() == NotificationType.Reprogramacion
                && n.getMeetingStartsAt().equals(start.plusHours(2).toInstant())));
    }

    @Test
    void reasonOnlyEditSendsNothing() {
        when(meetings.findById(ID)).thenReturn(Optional.of(existing()));
        meetingService.update(ID, new MeetingUpdateRequest(2L, 3L, start, start.plusHours(1), "Otro motivo", 0L));
        verifyNoInteractions(notifications);
    }

    @Test
    void notificationFailureFailsTheMeetingOperation() {
        savesNewMeeting();
        when(notifications.saveAll(any())).thenThrow(new DataIntegrityViolationException("notification"));
        assertThrows(DataIntegrityViolationException.class, () -> meetingService.create(
                new MeetingCreateRequest(1L, 2L, 3L, start, start.plusHours(1), "Control")));
    }

    private void savesNewMeeting() {
        AtomicReference<Meeting> saved = new AtomicReference<>();
        when(meetings.saveAndFlush(any())).thenAnswer(call -> {
            Meeting meeting = call.getArgument(0);
            meeting.setId(ID);
            meeting.setVersion(0L);
            saved.set(meeting);
            return meeting;
        });
        when(meetings.findById(ID)).thenAnswer(call -> Optional.ofNullable(saved.get()));
    }

    private Meeting existing() {
        Meeting meeting = new Meeting();
        meeting.setId(ID);
        meeting.setPatient(user(1L, UserRol.Paciente, "Ana"));
        meeting.setDoctor(user(2L, UserRol.Odontologo, "Carlos"));
        DentalChair chair = new DentalChair();
        chair.setId(3L);
        meeting.setChair(chair);
        meeting.setStartsAt(start.toInstant());
        meeting.setEndsAt(start.plusHours(1).toInstant());
        meeting.setReason("Control");
        meeting.setStatus(MeetingStatus.Confirmada);
        meeting.setVersion(0L);
        return meeting;
    }

    private User user(Long id, UserRol role, String name) {
        User user = new User();
        user.setId(id);
        user.setRol(role);
        user.setName(name);
        user.setLastName("Pérez");
        user.setEmail(name.toLowerCase() + "@example.com");
        user.setPhone("+57300123456" + id);
        return user;
    }
}
