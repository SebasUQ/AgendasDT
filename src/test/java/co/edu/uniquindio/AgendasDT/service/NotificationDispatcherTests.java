package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.domain.*;
import co.edu.uniquindio.AgendasDT.domain.enums.*;
import co.edu.uniquindio.AgendasDT.repository.NotificationRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTests {
    @Mock NotificationRepository notifications;
    @Mock NotificationSender sender;
    private final Instant now = Instant.parse("2026-10-20T15:00:00Z");
    private final Instant start = Instant.parse("2026-10-21T14:00:00Z");
    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setup() {
        dispatcher = new NotificationDispatcher(notifications, sender, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void sendsDueNotificationAndMarksItSent() {
        Notification notification = pending(meeting(MeetingStatus.Confirmada, start), 0);
        notification.setLastError("Error anterior");
        due(notification);
        assertEquals(1, dispatcher.dispatchDue());
        verify(sender).send(NotificationChannel.Correo, "ana@example.com", "Hola Ana");
        assertEquals(NotificationStatus.Enviada, notification.getStatus());
        assertEquals(now, notification.getSentAt());
        assertEquals(1, notification.getAttempts());
        assertNull(notification.getLastError());
        verify(notifications).lockDue(NotificationStatus.Pendiente, now, PageRequest.of(0, 50));
    }

    @Test
    void failedSendIsRetriedLaterWithGrowingDelay() {
        doThrow(new IllegalStateException("Servidor de correo caído")).when(sender).send(any(), any(), any());
        Notification first = pending(meeting(MeetingStatus.Pendiente, start), 0);
        Notification second = pending(meeting(MeetingStatus.Pendiente, start), 1);
        due(first, second);
        dispatcher.dispatchDue();
        assertEquals(NotificationStatus.Pendiente, first.getStatus());
        assertEquals(1, first.getAttempts());
        assertEquals(now.plus(Duration.ofMinutes(5)), first.getScheduledAt());
        assertEquals("Servidor de correo caído", first.getLastError());
        assertNull(first.getSentAt());
        assertEquals(2, second.getAttempts());
        assertEquals(now.plus(Duration.ofMinutes(10)), second.getScheduledAt());
    }

    @Test
    void thirdFailureMarksNotificationFailed() {
        doThrow(new IllegalStateException("Número inválido")).when(sender).send(any(), any(), any());
        Notification notification = pending(meeting(MeetingStatus.Confirmada, start), 2);
        Instant scheduled = notification.getScheduledAt();
        due(notification);
        dispatcher.dispatchDue();
        assertEquals(NotificationStatus.Fallida, notification.getStatus());
        assertEquals(3, notification.getAttempts());
        assertEquals(scheduled, notification.getScheduledAt());
        assertEquals("Número inválido", notification.getLastError());
    }

    @Test
    void storesErrorTypeOrTruncatedMessage() {
        Notification noMessage = pending(meeting(MeetingStatus.Confirmada, start), 0);
        Notification longMessage = pending(meeting(MeetingStatus.Confirmada, start), 0);
        longMessage.setRecipient("+573001234567");
        doThrow(new RuntimeException()).when(sender).send(any(), eq("ana@example.com"), any());
        doThrow(new RuntimeException("x".repeat(600))).when(sender).send(any(), eq("+573001234567"), any());
        due(noMessage, longMessage);
        dispatcher.dispatchDue();
        assertEquals("RuntimeException", noMessage.getLastError());
        assertEquals("x".repeat(500), longMessage.getLastError());
    }

    @Test
    void oneFailureDoesNotStopTheOthers() {
        Notification failing = pending(meeting(MeetingStatus.Confirmada, start), 0);
        Notification working = pending(meeting(MeetingStatus.Confirmada, start), 0);
        working.setRecipient("+573001234567");
        doThrow(new IllegalStateException("Fallo")).when(sender).send(any(), eq("ana@example.com"), any());
        due(failing, working);
        assertEquals(2, dispatcher.dispatchDue());
        assertEquals(NotificationStatus.Pendiente, failing.getStatus());
        assertEquals(NotificationStatus.Enviada, working.getStatus());
    }

    @Test
    void discardsNotificationsOfInactiveMeetings() {
        for (MeetingStatus status : List.of(MeetingStatus.Cancelada, MeetingStatus.Completada)) {
            Notification notification = pending(meeting(status, start), 0);
            due(notification);
            dispatcher.dispatchDue();
            assertDiscarded(notification, "La cita ya no está activa.");
        }
        verifyNoInteractions(sender);
    }

    @Test
    void discardsNotificationsForPreviousTime() {
        Notification notification = pending(meeting(MeetingStatus.Pendiente, start.plus(Duration.ofHours(2))), 0);
        due(notification);
        dispatcher.dispatchDue();
        assertDiscarded(notification, "La cita fue reprogramada.");
        verifyNoInteractions(sender);
    }

    @Test
    void discardsNotificationsOfMeetingsThatAlreadyStarted() {
        Meeting meeting = meeting(MeetingStatus.Confirmada, now);
        Notification notification = pending(meeting, 0);
        notification.setMeetingStartsAt(now);
        due(notification);
        dispatcher.dispatchDue();
        assertDiscarded(notification, "La cita ya comenzó.");
        verifyNoInteractions(sender);
    }

    @Test
    void nothingDueSendsNothing() {
        due();
        assertEquals(0, dispatcher.dispatchDue());
        verifyNoInteractions(sender);
    }

    private void assertDiscarded(Notification notification, String reason) {
        assertEquals(NotificationStatus.Descartada, notification.getStatus());
        assertEquals(reason, notification.getLastError());
        assertEquals(0, notification.getAttempts());
        assertNull(notification.getSentAt());
    }

    private void due(Notification... due) {
        when(notifications.lockDue(NotificationStatus.Pendiente, now, PageRequest.of(0, 50))).thenReturn(List.of(due));
    }

    private Notification pending(Meeting meeting, int attempts) {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setMeeting(meeting);
        notification.setType(NotificationType.Confirmacion);
        notification.setChannel(NotificationChannel.Correo);
        notification.setRecipient("ana@example.com");
        notification.setMessage("Hola Ana");
        notification.setMeetingStartsAt(start);
        notification.setCreatedAt(now.minusSeconds(600));
        notification.setScheduledAt(now.minusSeconds(60));
        notification.setStatus(NotificationStatus.Pendiente);
        notification.setAttempts(attempts);
        return notification;
    }

    private Meeting meeting(MeetingStatus status, Instant startsAt) {
        Meeting meeting = new Meeting();
        meeting.setId(UUID.randomUUID());
        meeting.setStatus(status);
        meeting.setStartsAt(startsAt);
        return meeting;
    }
}
