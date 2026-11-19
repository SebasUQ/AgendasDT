package co.edu.uniquindio.AgendasDT.dto;

import co.edu.uniquindio.AgendasDT.domain.*;
import co.edu.uniquindio.AgendasDT.domain.ClinicalRecord.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import org.springframework.data.domain.Page;

public final class DoctorDtos {
    private DoctorDtos() { }
    public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <E,T> PageResult<T> from(Page<E> page, Function<E,T> mapper) {
            return new PageResult<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }
    public record ProfileRequest(@NotBlank @Size(max=100) String professionalRegistration,
                                 @Size(max=150) String specialty, @PositiveOrZero Long version) { }
    public record ProfileResponse(UserResponse user, String professionalRegistration, String specialty, Long version) { }
    public record ToothRequest(@NotNull Integer tooth, @NotNull Surface surface, @NotNull FindingType type,
                               @NotBlank @Size(max=500) String description) { }
    public record RecordRequest(@NotNull @PositiveOrZero Long meetingVersion,
            @NotBlank @Size(max=2000) String reason, @NotBlank @Size(max=4000) String findings,
            @NotBlank @Size(max=2000) String diagnosis, @NotBlank @Size(max=4000) String performedProcedure,
            @NotBlank @Size(max=4000) String plan, @Size(max=4000) String medicalBackground,
            @Size(max=2000) String allergies, @Size(max=2000) String chronicConditions,
            @NotNull @Size(max=260) List<@Valid @NotNull ToothRequest> teeth) { }
    public record ToothResponse(Integer tooth, Surface surface, FindingType type, String color, String description,
                                UUID recordId, Instant recordedAt) {
        public static ToothResponse from(ToothFinding f, ClinicalRecord r) {
            return new ToothResponse(f.getTooth(), f.getSurface(), f.getType(),
                    f.getType() == FindingType.Patologia ? "Rojo" : "Azul", f.getDescription(), r.getId(), r.getRecordedAt());
        }
    }
    public record RecordResponse(UUID id, UUID meetingId, Long patientId, Long doctorId, String doctorName,
            String professionalRegistration, Instant recordedAt, String reason, String findings, String diagnosis,
            String performedProcedure, String plan, String medicalBackground, String allergies, String chronicConditions,
            List<ToothResponse> teeth) {
        public static RecordResponse from(ClinicalRecord r) {
            return new RecordResponse(r.getId(), r.getMeeting().getId(), r.getPatient().getId(), r.getDoctor().getId(),
                    r.getDoctorName(), r.getProfessionalRegistration(), r.getRecordedAt(), r.getReason(), r.getFindings(),
                    r.getDiagnosis(), r.getPerformedProcedure(), r.getPlan(), r.getMedicalBackground(), r.getAllergies(),
                    r.getChronicConditions(), r.getTeeth().stream().map(t -> ToothResponse.from(t,r)).toList());
        }
    }
    public record HistoryResponse(UserResponse patient, PageResult<RecordResponse> records) { }
    public record AddendumRequest(@NotBlank @Size(max=1000) String reason,
                                  @NotBlank @Size(max=8000) String text) { }
    public record AddendumResponse(UUID id, UUID recordId, Long doctorId, String doctorName,
                                   Instant recordedAt, String reason, String text) {
        public static AddendumResponse from(ClinicalAddendum a) {
            return new AddendumResponse(a.getId(), a.getRecord().getId(), a.getDoctorId(), a.getDoctorName(),
                    a.getRecordedAt(), a.getReason(), a.getContent());
        }
    }
    public record MedicationRequest(@NotBlank @Size(max=200) String medicine, @NotBlank @Size(max=200) String dose,
            @NotBlank @Size(max=200) String frequency, @NotBlank @Size(max=200) String duration,
            @Size(max=1000) String instructions) { }
    public record PrescriptionRequest(@NotEmpty @Size(max=30) List<@NotNull @Valid MedicationRequest> items,
            UUID supersedesId, @Size(max=1000) String correctionReason) { }
    public record PrescriptionResponse(UUID id, UUID recordId, Long patientId, String patientName, String doctorName,
            String professionalRegistration, Instant issuedAt, UUID supersedesId, String correctionReason,
            List<MedicationRequest> items, UUID supersededById) {
        public PrescriptionResponse(UUID id, UUID recordId, Long patientId, String patientName, String doctorName,
                String professionalRegistration, Instant issuedAt, UUID supersedesId, String correctionReason,
                List<MedicationRequest> items) {
            this(id,recordId,patientId,patientName,doctorName,professionalRegistration,issuedAt,supersedesId,correctionReason,items,null);
        }
        public PrescriptionResponse withSupersededBy(UUID replacementId) {
            return new PrescriptionResponse(id,recordId,patientId,patientName,doctorName,professionalRegistration,
                    issuedAt,supersedesId,correctionReason,items,replacementId);
        }
        public static PrescriptionResponse from(Prescription p) {
            return new PrescriptionResponse(p.getId(), p.getRecord().getId(), p.getRecord().getPatient().getId(),
                    p.getRecord().getPatientName(), p.getDoctorName(), p.getProfessionalRegistration(), p.getIssuedAt(),
                    p.getSupersedesId(), p.getCorrectionReason(), p.getItems().stream().map(i -> new MedicationRequest(
                            i.getMedicine(), i.getDose(), i.getFrequency(), i.getDuration(), i.getInstructions())).toList());
        }
    }
    public record SupplyResponse(String code, String name, String unit, long units) { }
    public record ProcedureResponse(UUID id, String code, String name, BigDecimal price, Instant validFrom,
                                     Instant validUntil, List<SupplyResponse> supplies) {
        public static ProcedureResponse from(ClinicalProcedure p) {
            return new ProcedureResponse(p.getId(), p.getCode(), p.getName(), p.getPrice(), p.getValidFrom(), p.getValidUntil(),
                    p.getSupplies().stream().map(s -> new SupplyResponse(s.getCode(),s.getName(),s.getUnit(),s.getUnits())).toList());
        }
    }
    public record ChargeLine(@NotNull UUID procedureId, @NotNull @Min(1) @Max(1000) Integer quantity) { }
    public record ChargeRequest(@NotEmpty @Size(max=50) List<@NotNull @Valid ChargeLine> lines) { }
    public record ChargeResponse(BillResponse bill, List<SupplyResponse> supplies) { }
}
