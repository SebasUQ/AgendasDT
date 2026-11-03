package co.edu.uniquindio.AgendasDT.controller;

import co.edu.uniquindio.AgendasDT.dto.NotificationResponse;
import co.edu.uniquindio.AgendasDT.service.NotificationService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/meetings/{meetingId}/notifications")
@PreAuthorize("hasAnyRole('Administrador', 'Recepcionista')")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) { this.service = service; }

    @GetMapping
    public List<NotificationResponse> list(@PathVariable UUID meetingId) { return service.listByMeeting(meetingId); }
}
