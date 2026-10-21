package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.domain.*;
import co.edu.uniquindio.AgendasDT.domain.enums.*;
import co.edu.uniquindio.AgendasDT.dto.NotificationResponse;
import co.edu.uniquindio.AgendasDT.exception.MeetingNotFoundException;
import co.edu.uniquindio.AgendasDT.repository.*;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class NotificationService {
    static final List<MeetingStatus> ACTIVE = List.of(MeetingStatus.Pendiente, MeetingStatus.Confirmada);
    static final Duration REMINDER_WINDOW = Duration.ofHours(24);
    private final NotificationRepository notifications;
    private final MeetingRepository meetings;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, MeetingRepository meetings, Clock clock) {
        this.notifications = notifications;
        this.meetings = meetings;
        this.clock = clock;
    }

    // Runs inside the meeting transaction: the meeting and its notifications are saved together or not at all.
    @EventListener
    public void onMeetingScheduled(MeetingNotificationEvent event) {
        Meeting meeting = find(event.meetingId());
        if (event.type() == NotificationType.Reprogramacion) {
            notifications.discardPending(meeting.getId(), "La cita fue reprogramada.",
                    NotificationStatus.Pendiente, NotificationStatus.Descartada);
        }
        enqueue(meeting, event.type());
    }

    public int createReminders() {
        Instant now = clock.instant();
        int created = 0;
        for (Meeting meeting : notifications.findMeetingsStartingBetween(ACTIVE, now, now.plus(REMINDER_WINDOW))) {
            if (!alreadyNotified(meeting)) {
                created += enqueue(meeting, NotificationType.Recordatorio);
            }
        }
        return created;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('Administrador', 'Recepcionista')")
    public List<NotificationResponse> listByMeeting(@NotNull UUID meetingId) {
        find(meetingId);
        return notifications.findByMeeting(meetingId).stream().map(NotificationResponse::from).toList();
    }

    // A reminder is redundant if one exists, or if the patient was notified less than 24 hours before the start.
    private boolean alreadyNotified(Meeting meeting) {
        Instant windowStart = meeting.getStartsAt().minus(REMINDER_WINDOW);
        return notifications.findByMeetingStart(meeting.getId(), meeting.getStartsAt()).stream()
                .filter(n -> n.getStatus() != NotificationStatus.Descartada)
                .anyMatch(n -> n.getType() == NotificationType.Recordatorio
                        || (n.getStatus() != NotificationStatus.Fallida && !n.getCreatedAt().isBefore(windowStart)));
    }

    private int enqueue(Meeting meeting, NotificationType type) {
        Instant now = clock.instant();
        String message = NotificationMessages.forMeeting(type, meeting);
        User patient = meeting.getPatient();
        List<Notification> created = new ArrayList<>();
        for (NotificationChannel channel : NotificationChannel.values()) {
            Notification notification = new Notification();
            notification.setMeeting(meeting);
            notification.setType(type);
            notification.setChannel(channel);
            notification.setRecipient(switch (channel) {
                case Correo -> patient.getEmail();
                case SMS -> patient.getPhone();
            });
            notification.setMessage(message);
            notification.setMeetingStartsAt(meeting.getStartsAt());
            notification.setCreatedAt(now);
            notification.setScheduledAt(now);
            notification.setStatus(NotificationStatus.Pendiente);
            created.add(notification);
        }
        notifications.saveAll(created);
        return created.size();
    }

    private Meeting find(UUID meetingId) {
        return meetings.findById(meetingId).orElseThrow(() -> new MeetingNotFoundException("La cita no existe."));
    }
}
