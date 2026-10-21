package co.edu.uniquindio.AgendasDT.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationScheduler {
    private final NotificationService service;
    private final NotificationDispatcher dispatcher;

    public NotificationScheduler(NotificationService service, NotificationDispatcher dispatcher) {
        this.service = service;
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${notifications.reminder-delay-ms:300000}")
    public void createReminders() { service.createReminders(); }

    @Scheduled(fixedDelayString = "${notifications.dispatch-delay-ms:60000}")
    public void dispatchDue() { dispatcher.dispatchDue(); }
}
