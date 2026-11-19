package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.domain.*;
import co.edu.uniquindio.AgendasDT.domain.enums.*;
import co.edu.uniquindio.AgendasDT.dto.*;
import co.edu.uniquindio.AgendasDT.dto.DoctorDtos.*;
import co.edu.uniquindio.AgendasDT.exception.ClinicalException;
import co.edu.uniquindio.AgendasDT.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import static org.springframework.http.HttpStatus.CONFLICT;

@Service @Validated @Transactional @PreAuthorize("hasRole('Odontologo')")
public class DoctorService {
    private final DoctorAccess access;
    private final DoctorProfileRepository profiles;
    private final MeetingRepository meetings;
    private final UserRepository users;
    private final ClinicalAuditRepository audits;
    private final Clock clock;
    public DoctorService(DoctorAccess access, DoctorProfileRepository profiles, MeetingRepository meetings,
                         UserRepository users, ClinicalAuditRepository audits, Clock clock) {
        this.access = access; this.profiles = profiles; this.meetings = meetings;
        this.users = users; this.audits = audits; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProfileResponse profile() {
        User doctor = access.current();
        DoctorProfile p = profiles.findById(doctor.getId()).orElse(null);
        return new ProfileResponse(UserResponse.from(doctor), p == null ? null : p.getProfessionalRegistration(),
                p == null ? null : p.getSpecialty(), p == null ? null : p.getVersion());
    }

    public ProfileResponse updateProfile(@Valid ProfileRequest request) {
        User doctor = access.current();
        DoctorProfile p = profiles.findById(doctor.getId()).orElseGet(DoctorProfile::new);
        if (request.version() != null && !Objects.equals(p.getVersion(), request.version())) {
            throw new ClinicalException(CONFLICT, "El perfil profesional cambió. Consúltelo antes de editar.");
        }
        p.setId(doctor.getId()); p.setProfessionalRegistration(request.professionalRegistration().strip());
        p.setSpecialty(request.specialty() == null ? null : request.specialty().strip());
        p.setUpdatedAt(clock.instant()); profiles.saveAndFlush(p);
        ClinicalAudit audit = new ClinicalAudit(); audit.setDoctorId(doctor.getId());
        audit.setAction("UPDATE_PROFILE"); audit.setOccurredAt(clock.instant()); audits.save(audit);
        return new ProfileResponse(UserResponse.from(doctor), p.getProfessionalRegistration(), p.getSpecialty(), p.getVersion());
    }

    @Transactional(readOnly = true)
    public PageResult<MeetingResponse> agenda(LocalDate date, MeetingStatus status,
                                              @Min(0) int page, @Min(1) @Max(100) int size) {
        User doctor = access.current();
        ZoneId zone = ZoneId.of("America/Bogota");
        LocalDate day = date == null ? LocalDate.now(clock.withZone(zone)) : date;
        var result = meetings.findDoctorAgenda(doctor.getId(), day.atStartOfDay(zone).toInstant(),
                day.plusDays(1).atStartOfDay(zone).toInstant(),
                status == null ? List.of(MeetingStatus.values()) : List.of(status),
                PageRequest.of(page, size, Sort.by("startsAt", "id")));
        return PageResult.from(result, MeetingResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResult<UserResponse> patients(@NotNull @Size(max=100) String search,
                                             @Min(0) int page, @Min(1) @Max(100) int size) {
        User doctor = access.current();
        return PageResult.from(users.findAssignedPatients(doctor.getId(), "%" + search.strip().toLowerCase(Locale.ROOT) + "%",
                PageRequest.of(page, size, Sort.by("lastName", "name", "id"))), UserResponse::from);
    }
}
