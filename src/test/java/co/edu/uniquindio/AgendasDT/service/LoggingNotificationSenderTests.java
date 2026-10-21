package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.domain.enums.NotificationChannel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LoggingNotificationSenderTests {
    @Test
    void masksEmailKeepingFirstLetterAndDomain() {
        assertEquals("a***@example.com", LoggingNotificationSender.mask("ana@example.com"));
    }

    @Test
    void masksPhoneKeepingLastFourDigits() {
        assertEquals("*********4567", LoggingNotificationSender.mask("+573001234567"));
        assertEquals("123", LoggingNotificationSender.mask("123"));
    }

    @Test
    void simulatedSendNeverFails() {
        assertDoesNotThrow(() -> new LoggingNotificationSender()
                .send(NotificationChannel.SMS, "+573001234567", "Hola Ana"));
    }
}
