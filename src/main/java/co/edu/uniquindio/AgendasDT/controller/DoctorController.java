package co.edu.uniquindio.AgendasDT.controller;

import co.edu.uniquindio.AgendasDT.domain.enums.MeetingStatus;
import co.edu.uniquindio.AgendasDT.dto.*;
import co.edu.uniquindio.AgendasDT.dto.DoctorDtos.*;
import co.edu.uniquindio.AgendasDT.service.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/doctor") @PreAuthorize("hasRole('Odontologo')")
public class DoctorController {
    private final DoctorService doctor;
    private final ClinicalService clinical;
    private final DoctorChargeService charges;
    private final PrescriptionPdfService pdf;
    public DoctorController(DoctorService doctor, ClinicalService clinical, DoctorChargeService charges, PrescriptionPdfService pdf) {
        this.doctor = doctor; this.clinical = clinical; this.charges = charges; this.pdf = pdf;
    }
    @ModelAttribute
    void noCache(HttpServletResponse response) { response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store"); }

    @GetMapping("/profile") public ProfileResponse profile() { return doctor.profile(); }
    @PutMapping("/profile") public ProfileResponse profile(@Valid @RequestBody ProfileRequest request) { return doctor.updateProfile(request); }

    @GetMapping("/agenda")
    public PageResult<MeetingResponse> agenda(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required=false) MeetingStatus status, @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return doctor.agenda(date,status,page,size); }

    @GetMapping("/patients")
    public PageResult<UserResponse> patients(@RequestParam(defaultValue="") @Size(max=100) String search,
            @RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return doctor.patients(search,page,size);
    }
    @GetMapping("/patients/{patientId}/history")
    public HistoryResponse history(@PathVariable @Positive Long patientId, @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(50) int size) { return clinical.history(patientId,page,size); }

    @GetMapping("/patients/{patientId}/odontogram")
    public List<ToothResponse> odontogram(@PathVariable @Positive Long patientId) { return clinical.odontogram(patientId); }

    @PostMapping("/meetings/{meetingId}/records")
    public ResponseEntity<RecordResponse> record(@PathVariable UUID meetingId, @Valid @RequestBody RecordRequest request) {
        var result = clinical.finalizeConsultation(meetingId,request);
        return ResponseEntity.created(URI.create("/api/doctor/records/"+result.id())).body(result);
    }
    @GetMapping("/records/{id}") public RecordResponse record(@PathVariable UUID id) { return clinical.getRecord(id); }

    @PostMapping("/records/{id}/addenda")
    public ResponseEntity<AddendumResponse> addendum(@PathVariable UUID id, @Valid @RequestBody AddendumRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinical.addendum(id,request));
    }
    @GetMapping("/records/{id}/addenda")
    public PageResult<AddendumResponse> addenda(@PathVariable UUID id, @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(50) int size) { return clinical.addenda(id,page,size); }

    @PostMapping("/records/{id}/prescriptions")
    public ResponseEntity<PrescriptionResponse> prescribe(@PathVariable UUID id, @Valid @RequestBody PrescriptionRequest request) {
        var result = clinical.prescribe(id,request);
        return ResponseEntity.created(URI.create("/api/doctor/prescriptions/"+result.id())).body(result);
    }
    @GetMapping("/records/{id}/prescriptions")
    public PageResult<PrescriptionResponse> prescriptions(@PathVariable UUID id, @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(50) int size) { return clinical.prescriptions(id,page,size); }

    @GetMapping("/prescriptions/{id}") public PrescriptionResponse prescription(@PathVariable UUID id) { return clinical.prescription(id,false); }
    @GetMapping(value="/prescriptions/{id}/pdf", produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> prescriptionPdf(@PathVariable UUID id) {
        byte[] bytes = pdf.render(clinical.prescription(id,true));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("prescripcion-"+id+".pdf").build().toString())
                .body(bytes);
    }
    @GetMapping("/procedures")
    public PageResult<ProcedureResponse> procedures(@RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return charges.procedures(page,size); }

    @PostMapping("/meetings/{meetingId}/charge-order")
    public ResponseEntity<ChargeResponse> charge(@PathVariable UUID meetingId, @Valid @RequestBody ChargeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(charges.create(meetingId,request));
    }
    @GetMapping("/meetings/{meetingId}/charge-order")
    public ChargeResponse charge(@PathVariable UUID meetingId) { return charges.get(meetingId); }
}
