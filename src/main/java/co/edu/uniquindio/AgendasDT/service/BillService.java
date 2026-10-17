package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.domain.*;
import co.edu.uniquindio.AgendasDT.domain.enums.*;
import co.edu.uniquindio.AgendasDT.dto.*;
import co.edu.uniquindio.AgendasDT.exception.*;
import co.edu.uniquindio.AgendasDT.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
@PreAuthorize("hasAnyRole('Administrador', 'Recepcionista')")
public class BillService {
    private final BillRepository bills;
    private final MeetingRepository meetings;
    private final BillAuditRepository audit;
    private final Clock clock;

    public BillService(BillRepository bills, MeetingRepository meetings, BillAuditRepository audit, Clock clock) {
        this.bills = bills;
        this.meetings = meetings;
        this.audit = audit;
        this.clock = clock;
    }

    public BillResponse create(@Valid BillCreateRequest request) {
        Meeting meeting = meetings.findById(request.meetingId())
                .orElseThrow(() -> new BillNotFoundException("La cita no existe."));
        if (meeting.getStatus() == MeetingStatus.Cancelada) {
            throw new InvalidBillException("No se puede facturar una cita cancelada.");
        }
        if (bills.existsByMeetingId(meeting.getId())) {
            throw new BillConflictException("La cita ya tiene una factura.");
        }
        Instant now = clock.instant();
        Bill bill = new Bill();
        bill.setMeeting(meeting);
        bill.setIssuedAt(now);
        bill.setStatus(BillStatus.Pendiente);
        replaceLines(bill, request.lines(), now);
        // The unique index on the meeting is authoritative when two bills are created at the same time.
        bills.saveAndFlush(bill);
        recordAudit(bill, "CREATE");
        return BillResponse.from(bill);
    }

    public BillResponse update(@NotNull UUID id, @Valid BillUpdateRequest request) {
        Bill bill = pending(id, request.version());
        replaceLines(bill, request.lines(), clock.instant());
        bills.flush();
        recordAudit(bill, "UPDATE");
        return BillResponse.from(bill);
    }

    public BillResponse pay(@NotNull UUID id, @Valid BillPaymentRequest request) {
        Bill bill = pending(id, request.version());
        Instant now = clock.instant();
        bill.setPaymentMethod(request.paymentMethod());
        bill.setPaidAt(now);
        bill.setUpdatedAt(now);
        bill.setStatus(BillStatus.Pagada);
        bills.flush();
        recordAudit(bill, "PAY");
        return BillResponse.from(bill);
    }

    @Transactional(readOnly = true)
    public BillResponse get(@NotNull UUID id) { return BillResponse.from(find(id)); }

    @Transactional(readOnly = true)
    public BillResponse getByMeeting(@NotNull UUID meetingId) {
        return bills.findByMeetingId(meetingId).map(BillResponse::from)
                .orElseThrow(() -> new BillNotFoundException("La cita no tiene factura."));
    }

    private Bill find(UUID id) {
        return bills.findById(id).orElseThrow(() -> new BillNotFoundException("La factura no existe."));
    }

    private Bill pending(UUID id, Long version) {
        Bill bill = find(id);
        if (!Objects.equals(bill.getVersion(), version)) {
            throw new BillConflictException("La factura cambió. Consúltela antes de volver a modificarla.");
        }
        if (bill.getStatus() != BillStatus.Pendiente) {
            throw new BillConflictException("Solo se pueden modificar facturas pendientes de pago.");
        }
        return bill;
    }

    // Subtotals and total are always calculated here; the client never sends them.
    private void replaceLines(Bill bill, List<BillLineRequest> lines, Instant now) {
        bill.getDetails().clear();
        BigDecimal total = BigDecimal.ZERO;
        int lineNumber = 1;
        for (BillLineRequest line : lines) {
            BillDetail detail = new BillDetail();
            detail.setBill(bill);
            detail.setLineNumber(lineNumber++);
            detail.setDescription(line.description().strip());
            detail.setQuantity(line.quantity());
            detail.setUnitPrice(line.unitPrice().setScale(2));
            detail.setSubtotal(detail.getUnitPrice().multiply(BigDecimal.valueOf(line.quantity())));
            total = total.add(detail.getSubtotal());
            bill.getDetails().add(detail);
        }
        bill.setTotal(total);
        bill.setUpdatedAt(now);
    }

    private void recordAudit(Bill bill, String action) {
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        audit.save(new BillAudit(bill.getId(), actor, action, clock.instant()));
    }
}
