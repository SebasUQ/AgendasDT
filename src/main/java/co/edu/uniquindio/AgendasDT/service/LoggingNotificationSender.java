package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.domain.enums.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingNotificationSender implements NotificationSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(NotificationChannel channel, String recipient, String message) {
        log.info("Notificación simulada por {} a {}: {}", channel, mask(recipient), message);
    }

    static String mask(String recipient) {
        int at = recipient.indexOf('@');
        if (at > 0) {
            return recipient.charAt(0) + "***" + recipient.substring(at);
        }
        int visible = Math.min(4, recipient.length());
        return "*".repeat(recipient.length() - visible) + recipient.substring(recipient.length() - visible);
    }
}
