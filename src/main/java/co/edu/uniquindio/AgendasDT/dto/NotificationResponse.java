package co.edu.uniquindio.AgendasDT.dto;

import co.edu.uniquindio.AgendasDT.domain.Notification;
import co.edu.uniquindio.AgendasDT.domain.enums.NotificationChannel;
import co.edu.uniquindio.AgendasDT.domain.enums.NotificationStatus;
import co.edu.uniquindio.AgendasDT.domain.enums.NotificationType;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, UUID meetingId, NotificationType type, NotificationChannel channel,
                                   String recipient, String message, NotificationStatus status, Instant createdAt,
                                   Instant scheduledAt, Instant sentAt, int attempts, String lastError) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getMeeting().getId(),
                notification.getType(), notification.getChannel(), notification.getRecipient(),
                notification.getMessage(), notification.getStatus(), notification.getCreatedAt(),
                notification.getScheduledAt(), notification.getSentAt(), notification.getAttempts(),
                notification.getLastError());
    }
}
