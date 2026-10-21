package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.domain.enums.NotificationChannel;

public interface NotificationSender {
    void send(NotificationChannel channel, String recipient, String message);
}
