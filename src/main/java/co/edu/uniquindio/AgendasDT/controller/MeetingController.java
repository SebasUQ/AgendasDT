package co.edu.uniquindio.AgendasDT.controller;

import co.edu.uniquindio.AgendasDT.dto.*;
import co.edu.uniquindio.AgendasDT.service.MeetingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/meetings")
@PreAuthorize("hasAnyRole('Administrador', 'Recepcionista', 'Odontologo', 'Paciente')")
public class MeetingController {
    private final MeetingService service;

    public MeetingController(MeetingService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<MeetingResponse> create(@Valid @RequestBody MeetingCreateRequest request) {
        MeetingResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/meetings/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    public MeetingResponse update(@PathVariable UUID id, @Valid @RequestBody MeetingUpdateRequest request) {
        return service.update(id, request);
    }

    @GetMapping("/{id}")
    public MeetingResponse get(@PathVariable UUID id) { return service.get(id); }

    @GetMapping("/doctors/{doctorId}")
    public MeetingListResponse listByDoctor(@PathVariable @Positive Long doctorId,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.listByDoctor(doctorId, page, size);
    }

    @PatchMapping("/{id}/status")
    public MeetingResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody MeetingStatusRequest request) {
        return service.changeStatus(id, request);
    }
}
