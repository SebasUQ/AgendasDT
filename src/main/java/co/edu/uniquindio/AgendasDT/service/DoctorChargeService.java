package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.domain.*;
import co.edu.uniquindio.AgendasDT.domain.enums.*;
import co.edu.uniquindio.AgendasDT.dto.BillResponse;
import co.edu.uniquindio.AgendasDT.dto.DoctorDtos.*;
import co.edu.uniquindio.AgendasDT.exception.ClinicalException;
import co.edu.uniquindio.AgendasDT.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import static org.springframework.http.HttpStatus.*;

@Service @Validated @Transactional @PreAuthorize("hasRole('Odontologo')")
public class DoctorChargeService {
    private final DoctorAccess access;
    private final ClinicalProcedureRepository procedures;
    private final ClinicalRecordRepository records;
    private final BillRepository bills;
    private final BillAuditRepository billAudits;
    private final PlannedSupplyRepository supplies;
    private final ClinicalAuditRepository audits;
    private final Clock clock;
    public DoctorChargeService(DoctorAccess access, ClinicalProcedureRepository procedures, ClinicalRecordRepository records,
            BillRepository bills, BillAuditRepository billAudits, PlannedSupplyRepository supplies, ClinicalAuditRepository audits, Clock clock) {
        this.access = access; this.procedures = procedures; this.records = records; this.bills = bills;
        this.billAudits = billAudits; this.supplies = supplies; this.audits = audits; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResult<ProcedureResponse> procedures(@Min(0) int page, @Min(1) @Max(100) int size) {
        access.current();
        return PageResult.from(procedures.findCurrent(clock.instant(), PageRequest.of(page,size,Sort.by("code"))), ProcedureResponse::from);
    }

    public ChargeResponse create(@NotNull UUID meetingId, @Valid ChargeRequest request) {
        User doctor = access.current(); Meeting meeting = access.meeting(meetingId, doctor, true);
        if (meeting.getStatus() != MeetingStatus.Completada || !records.existsByMeeting_Id(meetingId)) {
            throw new ClinicalException(CONFLICT, "Finalice la evolución clínica antes de emitir la orden de cobro.");
        }
        if (bills.existsByMeetingId(meetingId)) { throw new ClinicalException(CONFLICT, "La cita ya tiene una orden o factura."); }
        Instant now = clock.instant();
        // Lock tariff rows in a stable order so a concurrent administrative price change cannot split this snapshot.
        Map<UUID, ClinicalProcedure> tariff = new HashMap<>();
        for (UUID id : request.lines().stream().map(ChargeLine::procedureId).distinct().sorted().toList()) {
            ClinicalProcedure p = procedures.lockForBilling(id).orElseThrow(() -> new ClinicalException(NOT_FOUND, "El procedimiento no existe."));
            if (!p.isActive() || p.getValidFrom().isAfter(now) || (p.getValidUntil() != null && !p.getValidUntil().isAfter(now))) {
                throw new ClinicalException(CONFLICT, "El procedimiento no tiene una tarifa vigente.");
            }
            tariff.put(id,p);
        }
        if (tariff.size() != request.lines().size()) {
            throw new ClinicalException(BAD_REQUEST, "No repita procedimientos; use el campo cantidad.");
        }
        Bill bill = new Bill(); bill.setMeeting(meeting); bill.setIssuedAt(now); bill.setUpdatedAt(now);
        bill.setClinicalOrder(true); bill.setStatus(BillStatus.Pendiente);
        BigDecimal total = BigDecimal.ZERO;
        Map<String, PlannedSupply> planned = new TreeMap<>();
        for (ChargeLine line : request.lines()) {
            ClinicalProcedure p = tariff.get(line.procedureId());
            BillDetail detail = new BillDetail(); detail.setBill(bill); detail.setLineNumber(bill.getDetails().size()+1);
            detail.setProcedureId(p.getId()); detail.setDescription(p.getCode() + " - " + p.getName());
            detail.setQuantity(line.quantity()); detail.setUnitPrice(p.getPrice().setScale(2));
            detail.setSubtotal(detail.getUnitPrice().multiply(BigDecimal.valueOf(line.quantity())));
            total = total.add(detail.getSubtotal()); bill.getDetails().add(detail);
            for (var supply : p.getSupplies()) {
                String key = supply.getCode() + ":" + supply.getUnit();
                PlannedSupply target = planned.computeIfAbsent(key, ignored -> {
                    PlannedSupply s = new PlannedSupply(); s.setCode(supply.getCode()); s.setName(supply.getName());
                    s.setUnit(supply.getUnit()); s.setUnits(0L); return s;
                });
                target.setUnits(Math.addExact(target.getUnits(), Math.multiplyExact((long) supply.getUnits(), line.quantity())));
            }
        }
        bill.setTotal(total); bills.saveAndFlush(bill);
        for (PlannedSupply supply : planned.values()) { supply.setBillId(bill.getId()); }
        supplies.saveAll(planned.values());
        billAudits.save(new BillAudit(bill.getId(), doctor.getEmail(), "CREATE", now));
        ClinicalAudit audit = new ClinicalAudit(); audit.setDoctorId(doctor.getId()); audit.setPatientId(meeting.getPatient().getId());
        audit.setAction("CREATE_CHARGE_ORDER"); audit.setOccurredAt(now); audits.save(audit);
        return response(bill, new ArrayList<>(planned.values()));
    }

    @Transactional(readOnly = true)
    public ChargeResponse get(@NotNull UUID meetingId) {
        User doctor = access.current(); access.meeting(meetingId,doctor,false);
        Bill bill = bills.findByMeetingId(meetingId).filter(Bill::isClinicalOrder)
                .orElseThrow(() -> new ClinicalException(NOT_FOUND, "La consulta no tiene una orden clínica de cobro."));
        return response(bill, supplies.findByBillIdOrderByCodeAsc(bill.getId()));
    }

    private ChargeResponse response(Bill bill, List<PlannedSupply> planned) {
        return new ChargeResponse(BillResponse.from(bill), planned.stream()
                .map(s -> new SupplyResponse(s.getCode(),s.getName(),s.getUnit(),s.getUnits())).toList());
    }
}
