package co.edu.uniquindio.AgendasDT.domain;

import co.edu.uniquindio.AgendasDT.domain.enums.NotificationChannel;
import co.edu.uniquindio.AgendasDT.domain.enums.NotificationStatus;
import co.edu.uniquindio.AgendasDT.domain.enums.NotificationType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Table(name = "notification")
@Entity
public class Notification {

    //Atributos
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false, updatable = false)
    private Meeting meeting;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private NotificationChannel channel;

    @Column(nullable = false, updatable = false, length = 254)
    private String recipient;

    @Column(nullable = false, updatable = false, length = 500)
    private String message;

    @Column(name = "meeting_starts_at", nullable = false, updatable = false)
    private Instant meetingStartsAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Version
    @Column(nullable = false)
    private Long version;

    //Constructor vacio
    public Notification() {

    }
}
