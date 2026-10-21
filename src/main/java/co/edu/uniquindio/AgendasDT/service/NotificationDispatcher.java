package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.domain.Meeting;
import co.edu.uniquindio.AgendasDT.domain.Notification;
import co.edu.uniquindio.AgendasDT.domain.enums.NotificationStatus;
import co.edu.uniquindio.AgendasDT.repository.NotificationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificationDispatcher {
    static final int MAX_ATTEMPTS = 3;
    static final int BATCH_SIZE = 50;
    static final Duration RETRY_DELAY = Duration.ofMinutes(5);
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private final NotificationRepository notifications;
    private final NotificationSender sender;
    private final Clock clock;

    public NotificationDispatcher(NotificationRepository notifications, NotificationSender sender, Clock clock) {
        this.notifications = notifications;
        this.sender = sender;
        this.clock = clock;
    }

    public int dispatchDue() {
        Instant now = clock.instant();
        var due = notifications.lockDue(NotificationStatus.Pendiente, now, PageRequest.of(0, BATCH_SIZE));
        for (Notification notification : due) {
            String obsolete = obsoleteReason(notification, now);
            if (obsolete != null) {
                notification.setStatus(NotificationStatus.Descartada);
                notification.setLastError(obsolete);
            } else {
                deliver(notification, now);
            }
        }
        return due.size();
    }

    private String obsoleteReason(Notification notification, Instant now) {
        Meeting meeting = notification.getMeeting();
        if (!NotificationService.ACTIVE.contains(meeting.getStatus())) {
            return "La cita ya no está activa.";
        }
        if (!meeting.getStartsAt().equals(notification.getMeetingStartsAt())) {
            return "La cita fue reprogramada.";
        }
        if (!meeting.getStartsAt().isAfter(now)) {
            return "La cita ya comenzó.";
        }
        return null;
    }

    private void deliver(Notification notification, Instant now) {
        notification.setAttempts(notification.getAttempts() + 1);
        try {
            sender.send(notification.getChannel(), notification.getRecipient(), notification.getMessage());
            notification.setStatus(NotificationStatus.Enviada);
            notification.setSentAt(now);
            notification.setLastError(null);
        } catch (RuntimeException ex) {
            notification.setLastError(describe(ex));
            if (notification.getAttempts() >= MAX_ATTEMPTS) {
                notification.setStatus(NotificationStatus.Fallida);
            } else {
                notification.setScheduledAt(now.plus(RETRY_DELAY.multipliedBy(notification.getAttempts())));
            }
            log.warn("No se pudo enviar la notificación {} (intento {} de {})",
                    notification.getId(), notification.getAttempts(), MAX_ATTEMPTS, ex);
        }
    }

    private String describe(RuntimeException ex) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
