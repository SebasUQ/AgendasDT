package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.domain.*;
import co.edu.uniquindio.AgendasDT.domain.enums.*;
import co.edu.uniquindio.AgendasDT.dto.NotificationResponse;
import co.edu.uniquindio.AgendasDT.exception.MeetingNotFoundException;
import co.edu.uniquindio.AgendasDT.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTests {
    @Mock NotificationRepository notifications;
    @Mock MeetingRepository meetings;
    @Captor ArgumentCaptor<List<Notification>> saved;
    private final Instant now = Instant.parse("2026-10-20T15:00:00Z");
    private final Instant start = Instant.parse("2026-10-21T14:00:00Z");
    private final UUID meetingId = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private NotificationService service;

    @BeforeEach
    void setup() {
        service = new NotificationService(notifications, meetings, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void confirmationQueuesEmailAndSmsForPatient() {
        Meeting meeting = meeting();
        when(meetings.findById(meetingId)).thenReturn(Optional.of(meeting));
        service.onMeetingScheduled(new MeetingNotificationEvent(meetingId, NotificationType.Confirmacion));
        verify(notifications).saveAll(saved.capture());
        List<Notification> queued = saved.getValue();
        assertEquals(List.of(NotificationChannel.Correo, NotificationChannel.SMS),
                queued.stream().map(Notification::getChannel).toList());
        assertEquals(List.of("ana@example.com", "+573001234567"),
                queued.stream().map(Notification::getRecipient).toList());
        for (Notification notification : queued) {
            assertSame(meeting, notification.getMeeting());
            assertEquals(NotificationType.Confirmacion, notification.getType());
            assertEquals(NotificationStatus.Pendiente, notification.getStatus());
            assertEquals(NotificationMessages.forMeeting(NotificationType.Confirmacion, meeting), notification.getMessage());
            assertEquals(start, notification.getMeetingStartsAt());
            assertEquals(now, notification.getCreatedAt());
            assertEquals(now, notification.getScheduledAt());
            assertEquals(0, notification.getAttempts());
            assertNull(notification.getSentAt());
        }
        verify(notifications, never()).discardPending(any(), any(), any(), any());
    }

    @Test
    void reschedulingDiscardsPendingNotificationsBeforeQueuingNewOnes() {
        when(meetings.findById(meetingId)).thenReturn(Optional.of(meeting()));
        service.onMeetingScheduled(new MeetingNotificationEvent(meetingId, NotificationType.Reprogramacion));
        InOrder order = inOrder(notifications);
        order.verify(notifications).discardPending(meetingId, "La cita fue reprogramada.",
                NotificationStatus.Pendiente, NotificationStatus.Descartada);
        order.verify(notifications).saveAll(saved.capture());
        assertTrue(saved.getValue().stream().allMatch(n -> n.getType() == NotificationType.Reprogramacion));
    }

    @Test
    void unknownMeetingFailsSoTheMeetingChangeRollsBack() {
        assertThrows(MeetingNotFoundException.class,
                () -> service.onMeetingScheduled(new MeetingNotificationEvent(meetingId, NotificationType.Confirmacion)));
        verifyNoInteractions(notifications);
    }

    @Test
    void queuesReminderForUpcomingMeetingWithoutNotifications() {
        upcoming(List.of());
        assertEquals(2, service.createReminders());
        verify(notifications).saveAll(saved.capture());
        assertTrue(saved.getValue().stream().allMatch(n -> n.getType() == NotificationType.Recordatorio));
        verify(notifications).findMeetingsStartingBetween(NotificationService.ACTIVE, now, now.plus(Duration.ofHours(24)));
    }

    @Test
    void doesNotRepeatReminder() {
        for (NotificationStatus status : List.of(NotificationStatus.Pendiente, NotificationStatus.Enviada,
                NotificationStatus.Fallida)) {
            upcoming(List.of(existing(NotificationType.Recordatorio, status, now.minusSeconds(60))));
            assertEquals(0, service.createReminders(), status.name());
        }
        verify(notifications, never()).saveAll(any());
    }

    @Test
    void skipsReminderWhenPatientWasNotifiedWithinLast24Hours() {
        upcoming(List.of(existing(NotificationType.Confirmacion, NotificationStatus.Enviada, start.minus(Duration.ofHours(23)))));
        assertEquals(0, service.createReminders());
        upcoming(List.of(existing(NotificationType.Reprogramacion, NotificationStatus.Pendiente, start.minus(Duration.ofHours(24)))));
        assertEquals(0, service.createReminders());
        verify(notifications, never()).saveAll(any());
    }

    @Test
    void remindsMeetingConfirmedMoreThan24HoursBefore() {
        upcoming(List.of(existing(NotificationType.Confirmacion, NotificationStatus.Enviada,
                start.minus(Duration.ofHours(24)).minusSeconds(1))));
        assertEquals(2, service.createReminders());
    }

    @Test
    void failedOrDiscardedNotificationsDoNotReplaceReminder() {
        upcoming(List.of(
                existing(NotificationType.Confirmacion, NotificationStatus.Fallida, now),
                existing(NotificationType.Reprogramacion, NotificationStatus.Descartada, now),
                existing(NotificationType.Recordatorio, NotificationStatus.Descartada, now)));
        assertEquals(2, service.createReminders());
    }

    @Test
    void noUpcomingMeetingsCreatesNothing() {
        when(notifications.findMeetingsStartingBetween(any(), any(), any())).thenReturn(List.of());
        assertEquals(0, service.createReminders());
        verify(notifications, never()).saveAll(any());
    }

    @Test
    void listsNotificationsOfMeeting() {
        Meeting meeting = meeting();
        Notification notification = existing(NotificationType.Confirmacion, NotificationStatus.Enviada, now);
        notification.setMeeting(meeting);
        when(meetings.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(notifications.findByMeeting(meetingId)).thenReturn(List.of(notification));
        List<NotificationResponse> responses = service.listByMeeting(meetingId);
        assertEquals(1, responses.size());
        assertEquals(meetingId, responses.getFirst().meetingId());
        assertEquals(NotificationStatus.Enviada, responses.getFirst().status());
    }

    @Test
    void listingUnknownMeetingFails() {
        assertThrows(MeetingNotFoundException.class, () -> service.listByMeeting(meetingId));
        verifyNoInteractions(notifications);
    }

    private void upcoming(List<Notification> alreadyQueued) {
        when(notifications.findMeetingsStartingBetween(any(), any(), any())).thenReturn(List.of(meeting()));
        when(notifications.findByMeetingStart(meetingId, start)).thenReturn(alreadyQueued);
    }

    private Notification existing(NotificationType type, NotificationStatus status, Instant createdAt) {
        Notification notification = new Notification();
        notification.setType(type);
        notification.setStatus(status);
        notification.setCreatedAt(createdAt);
        notification.setMeetingStartsAt(start);
        return notification;
    }

    private Meeting meeting() {
        User patient = new User();
        patient.setId(1L);
        patient.setName("Ana");
        patient.setEmail("ana@example.com");
        patient.setPhone("+573001234567");
        User doctor = new User();
        doctor.setId(2L);
        doctor.setName("Carlos");
        doctor.setLastName("Rojas");
        Meeting meeting = new Meeting();
        meeting.setId(meetingId);
        meeting.setPatient(patient);
        meeting.setDoctor(doctor);
        meeting.setStartsAt(start);
        meeting.setStatus(MeetingStatus.Confirmada);
        return meeting;
    }
}
