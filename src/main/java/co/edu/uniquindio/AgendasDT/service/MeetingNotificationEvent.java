package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.domain.enums.NotificationType;
import java.util.UUID;

public record MeetingNotificationEvent(UUID meetingId, NotificationType type) { }
