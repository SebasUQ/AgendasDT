package co.edu.uniquindio.AgendasDT.controller;

import co.edu.uniquindio.AgendasDT.dto.*;
import co.edu.uniquindio.AgendasDT.service.BillService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bills")
@PreAuthorize("hasAnyRole('Administrador', 'Recepcionista')")
public class BillController {
    private final BillService service;

    public BillController(BillService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<BillResponse> create(@Valid @RequestBody BillCreateRequest request) {
        BillResponse bill = service.create(request);
        return ResponseEntity.created(URI.create("/api/bills/" + bill.id())).body(bill);
    }

    @GetMapping("/{id}")
    public BillResponse get(@PathVariable UUID id) { return service.get(id); }

    @GetMapping("/meeting/{meetingId}")
    public BillResponse getByMeeting(@PathVariable UUID meetingId) { return service.getByMeeting(meetingId); }

    @PutMapping("/{id}")
    public BillResponse update(@PathVariable UUID id, @Valid @RequestBody BillUpdateRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/payment")
    public BillResponse pay(@PathVariable UUID id, @Valid @RequestBody BillPaymentRequest request) {
        return service.pay(id, request);
    }
}
