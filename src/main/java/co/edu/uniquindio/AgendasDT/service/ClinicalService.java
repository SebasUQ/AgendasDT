package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.domain.*;
import co.edu.uniquindio.AgendasDT.domain.enums.*;
import co.edu.uniquindio.AgendasDT.dto.*;
import co.edu.uniquindio.AgendasDT.dto.DoctorDtos.*;
import co.edu.uniquindio.AgendasDT.exception.ClinicalException;
import co.edu.uniquindio.AgendasDT.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Clock;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import static org.springframework.http.HttpStatus.*;

@Service @Validated @Transactional @PreAuthorize("hasRole('Odontologo')")
public class ClinicalService {
    private final DoctorAccess access;
    private final DoctorProfileRepository profiles;
    private final ClinicalRecordRepository records;
    private final ClinicalAddendumRepository addenda;
    private final PrescriptionRepository prescriptions;
    private final ClinicalAuditRepository audits;
    private final MeetingRepository meetings;
    private final MeetingAuditRepository meetingAudits;
    private final Clock clock;
    public ClinicalService(DoctorAccess access, DoctorProfileRepository profiles, ClinicalRecordRepository records,
            ClinicalAddendumRepository addenda, PrescriptionRepository prescriptions, ClinicalAuditRepository audits,
            MeetingRepository meetings, MeetingAuditRepository meetingAudits, Clock clock) {
        this.access = access; this.profiles = profiles; this.records = records; this.addenda = addenda;
        this.prescriptions = prescriptions; this.audits = audits; this.meetings = meetings;
        this.meetingAudits = meetingAudits; this.clock = clock;
    }

    public HistoryResponse history(@NotNull @Positive Long patientId, @Min(0) int page, @Min(1) @Max(50) int size) {
        User doctor = access.current(); User patient = access.patient(patientId, doctor);
        var result = records.findByPatient_IdOrderByRecordedAtDescIdDesc(patientId, PageRequest.of(page, size));
        audit(doctor, patientId, null, "READ_HISTORY");
        return new HistoryResponse(UserResponse.from(patient), PageResult.from(result, RecordResponse::from));
    }

    public RecordResponse getRecord(@NotNull UUID id) {
        User doctor = access.current(); ClinicalRecord record = readable(id, doctor);
        audit(doctor, record.getPatient().getId(), id, "READ_RECORD");
        return RecordResponse.from(record);
    }

    public RecordResponse finalizeConsultation(@NotNull UUID meetingId, @Valid RecordRequest request) {
        User doctor = access.current();
        Meeting meeting = access.meeting(meetingId, doctor, true);
        if (!Objects.equals(meeting.getVersion(), request.meetingVersion())) {
            throw new ClinicalException(CONFLICT, "La cita cambió. Consulte la versión vigente.");
        }
        if (meeting.getStatus() != MeetingStatus.Confirmada || meeting.getStartsAt().isAfter(clock.instant())) {
            throw new ClinicalException(CONFLICT, "La consulta debe estar confirmada y haber iniciado.");
        }
        if (records.existsByMeeting_Id(meetingId)) { throw new ClinicalException(CONFLICT, "La consulta ya tiene una evolución finalizada."); }
        DoctorProfile profile = registered(doctor);
        Set<String> seen = new HashSet<>();
        List<ClinicalRecord.ToothFinding> teeth = new ArrayList<>();
        for (ToothRequest tooth : request.teeth()) {
            int quadrant = tooth.tooth() / 10, unit = tooth.tooth() % 10;
            if (!((quadrant >= 1 && quadrant <= 4 && unit >= 1 && unit <= 8)
                    || (quadrant >= 5 && quadrant <= 8 && unit >= 1 && unit <= 5))) {
                throw new ClinicalException(BAD_REQUEST, "Pieza FDI inválida: " + tooth.tooth());
            }
            if (!seen.add(tooth.tooth() + ":" + tooth.surface())) {
                throw new ClinicalException(BAD_REQUEST, "Una pieza y cara no pueden repetirse en la misma evolución.");
            }
            var finding = new ClinicalRecord.ToothFinding(); finding.setTooth(tooth.tooth());
            finding.setSurface(tooth.surface()); finding.setType(tooth.type()); finding.setDescription(tooth.description().strip());
            teeth.add(finding);
        }
        ClinicalRecord record = new ClinicalRecord();
        record.setMeeting(meeting); record.setPatient(meeting.getPatient()); record.setDoctor(doctor);
        record.setDoctorName(fullName(doctor)); record.setPatientName(fullName(meeting.getPatient()));
        record.setProfessionalRegistration(profile.getProfessionalRegistration()); record.setRecordedAt(clock.instant());
        record.setReason(request.reason().strip()); record.setFindings(request.findings().strip());
        record.setDiagnosis(request.diagnosis().strip()); record.setPerformedProcedure(request.performedProcedure().strip());
        record.setPlan(request.plan().strip()); record.setMedicalBackground(request.medicalBackground());
        record.setAllergies(request.allergies()); record.setChronicConditions(request.chronicConditions()); record.setTeeth(teeth);
        records.saveAndFlush(record);
        meeting.setStatus(MeetingStatus.Completada); meetings.flush();
        meetingAudits.save(new MeetingAudit(meetingId, doctor.getEmail(), "UPDATE", clock.instant(),
                MeetingStatus.Confirmada, MeetingStatus.Completada));
        audit(doctor, meeting.getPatient().getId(), record.getId(), "FINALIZE_RECORD");
        return RecordResponse.from(record);
    }

    public List<ToothResponse> odontogram(@NotNull @Positive Long patientId) {
        User doctor = access.current(); access.patient(patientId, doctor);
        Map<String, ToothResponse> current = new TreeMap<>();
        for (ClinicalRecord record : records.findByPatient_IdOrderByRecordedAtAscIdAsc(patientId)) {
            for (var finding : record.getTeeth()) {
                current.put(finding.getTooth() + ":" + finding.getSurface(), ToothResponse.from(finding, record));
            }
        }
        audit(doctor, patientId, null, "READ_ODONTOGRAM");
        return List.copyOf(current.values());
    }

    public AddendumResponse addendum(@NotNull UUID recordId, @Valid AddendumRequest request) {
        User doctor = access.current(); ClinicalRecord record = readable(recordId, doctor); access.author(record, doctor);
        ClinicalAddendum a = new ClinicalAddendum(); a.setRecord(record); a.setDoctorId(doctor.getId()); a.setDoctorName(fullName(doctor));
        a.setRecordedAt(clock.instant()); a.setReason(request.reason().strip()); a.setContent(request.text().strip());
        addenda.saveAndFlush(a); audit(doctor, record.getPatient().getId(), recordId, "ADD_ADDENDUM");
        return AddendumResponse.from(a);
    }

    public PageResult<AddendumResponse> addenda(@NotNull UUID recordId, @Min(0) int page, @Min(1) @Max(50) int size) {
        User doctor = access.current(); ClinicalRecord record = readable(recordId, doctor);
        audit(doctor, record.getPatient().getId(), recordId, "READ_ADDENDA");
        return PageResult.from(addenda.findByRecord_IdOrderByRecordedAtAscIdAsc(recordId, PageRequest.of(page,size)), AddendumResponse::from);
    }

    public PrescriptionResponse prescribe(@NotNull UUID recordId, @Valid PrescriptionRequest request) {
        User doctor = access.current(); ClinicalRecord record = readable(recordId, doctor); access.author(record, doctor);
        DoctorProfile profile = registered(doctor);
        if (request.supersedesId() != null) {
            Prescription previous = prescriptions.findById(request.supersedesId())
                    .orElseThrow(() -> new ClinicalException(NOT_FOUND, "La prescripción anterior no existe."));
            if (!previous.getRecord().getId().equals(recordId)) {
                throw new ClinicalException(BAD_REQUEST, "La receta anterior debe pertenecer a la misma evolución.");
            }
            if (request.correctionReason() == null || request.correctionReason().isBlank()) {
                throw new ClinicalException(BAD_REQUEST, "Indique el motivo de corrección de la receta.");
            }
            if (prescriptions.existsBySupersedesId(previous.getId())) {
                throw new ClinicalException(CONFLICT, "La receta anterior ya tiene una corrección.");
            }
        }
        Prescription p = new Prescription(); p.setRecord(record); p.setDoctorName(fullName(doctor));
        p.setProfessionalRegistration(profile.getProfessionalRegistration()); p.setIssuedAt(clock.instant());
        p.setSupersedesId(request.supersedesId()); p.setCorrectionReason(request.correctionReason());
        for (MedicationRequest item : request.items()) {
            var line = new Prescription.Item(); line.setMedicine(item.medicine().strip()); line.setDose(item.dose().strip());
            line.setFrequency(item.frequency().strip()); line.setDuration(item.duration().strip()); line.setInstructions(item.instructions());
            p.getItems().add(line);
        }
        prescriptions.saveAndFlush(p); audit(doctor, record.getPatient().getId(), recordId, "ISSUE_PRESCRIPTION");
        return PrescriptionResponse.from(p);
    }

    public PageResult<PrescriptionResponse> prescriptions(@NotNull UUID recordId, @Min(0) int page, @Min(1) @Max(50) int size) {
        User doctor = access.current(); ClinicalRecord record = readable(recordId,doctor);
        audit(doctor, record.getPatient().getId(), recordId, "READ_PRESCRIPTIONS");
        return PageResult.from(prescriptions.findByRecord_IdOrderByIssuedAtDescIdDesc(recordId, PageRequest.of(page,size)), this::prescriptionResponse);
    }

    public PrescriptionResponse prescription(@NotNull UUID id, boolean pdf) {
        User doctor = access.current();
        Prescription p = prescriptions.findById(id).orElseThrow(() -> new ClinicalException(NOT_FOUND, "La receta no existe."));
        access.patient(p.getRecord().getPatient().getId(), doctor);
        audit(doctor, p.getRecord().getPatient().getId(), p.getRecord().getId(), pdf ? "DOWNLOAD_PRESCRIPTION" : "READ_PRESCRIPTION");
        return prescriptionResponse(p);
    }

    private ClinicalRecord readable(UUID id, User doctor) {
        ClinicalRecord r = records.findById(id).orElseThrow(() -> new ClinicalException(NOT_FOUND, "La evolución no existe."));
        access.patient(r.getPatient().getId(), doctor); return r;
    }
    private PrescriptionResponse prescriptionResponse(Prescription prescription) {
        return PrescriptionResponse.from(prescription).withSupersededBy(prescriptions.findBySupersedesId(prescription.getId())
                .map(Prescription::getId).orElse(null));
    }
    private DoctorProfile registered(User doctor) {
        return profiles.findById(doctor.getId()).orElseThrow(() -> new ClinicalException(BAD_REQUEST,
                "Complete su registro profesional en /api/doctor/profile antes de emitir documentos clínicos."));
    }
    private String fullName(User user) { return user.getName() + " " + user.getLastName(); }
    private void audit(User doctor, Long patientId, UUID recordId, String action) {
        ClinicalAudit a = new ClinicalAudit(); a.setDoctorId(doctor.getId()); a.setPatientId(patientId);
        a.setRecordId(recordId); a.setAction(action); a.setOccurredAt(clock.instant()); audits.save(a);
    }
}
