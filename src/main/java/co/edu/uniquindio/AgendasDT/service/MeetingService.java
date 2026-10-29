package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.domain.*;
import co.edu.uniquindio.AgendasDT.domain.enums.*;
import co.edu.uniquindio.AgendasDT.dto.*;
import co.edu.uniquindio.AgendasDT.exception.*;
import co.edu.uniquindio.AgendasDT.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Locale;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
@PreAuthorize("hasAnyRole('Administrador', 'Recepcionista', 'Odontologo', 'Paciente')")
public class MeetingService {
    private static final List<MeetingStatus> RESERVED = List.of(MeetingStatus.Pendiente, MeetingStatus.Confirmada);
    private static final UUID NO_MEETING = new UUID(0, 0);
    private final MeetingRepository meetings;
    private final UserRepository users;
    private final DentalChairRepository chairs;
    private final MeetingAuditRepository audit;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public MeetingService(MeetingRepository meetings, UserRepository users, DentalChairRepository chairs,
                          MeetingAuditRepository audit, Clock clock, ApplicationEventPublisher events) {
        this.meetings = meetings;
        this.users = users;
        this.chairs = chairs;
        this.audit = audit;
        this.clock = clock;
        this.events = events;
    }

    public MeetingResponse create(@Valid MeetingCreateRequest request) {
        User actor = scopedUser();
        checkCreateScope(actor, request.patientId(), request.doctorId());
        Instant start = request.startsAt().toInstant();
        Instant end = request.endsAt().toInstant();
        validateTime(start, end);
        User patient = userWithRole(request.patientId(), UserRol.Paciente);
        User doctor = userWithRole(request.doctorId(), UserRol.Odontologo);
        DentalChair chair = activeChair(request.chairId());
        validateAvailability(NO_MEETING, patient.getId(), doctor.getId(), chair.getId(), start, end);
        Meeting meeting = new Meeting();
        meeting.setPatient(patient);
        apply(meeting, doctor, chair, start, end, request.reason());
        meeting.setStatus(MeetingStatus.Pendiente);
        // PostgreSQL exclusion constraints are authoritative when simultaneous requests race.
        meetings.saveAndFlush(meeting);
        recordAudit(meeting, "CREATE");
        events.publishEvent(new MeetingNotificationEvent(meeting.getId(), NotificationType.Confirmacion));
        return MeetingResponse.from(meeting);
    }

    public MeetingResponse update(@NotNull UUID id, @Valid MeetingUpdateRequest request) {
        User actor = scopedUser();
        Meeting meeting = find(id);
        checkAccess(actor, meeting);
        if (actor != null && actor.getRol() == UserRol.Odontologo
                && !actor.getId().equals(request.doctorId())) {
            throw new AccessDeniedException("El odontólogo no puede reasignar su cita a otro profesional.");
        }
        checkPatientAdvance(actor, meeting);
        if (!Objects.equals(meeting.getVersion(), request.version())) {
            throw new MeetingConflictException("La cita cambió. Consúltela antes de volver a editar.");
        }
        if (!RESERVED.contains(meeting.getStatus()) || !meeting.getStartsAt().isAfter(clock.instant())) {
            throw new MeetingConflictException("Solo se pueden editar citas pendientes o confirmadas que no hayan iniciado.");
        }
        Instant start = request.startsAt().toInstant();
        Instant end = request.endsAt().toInstant();
        validateTime(start, end);
        User doctor = userWithRole(request.doctorId(), UserRol.Odontologo);
        DentalChair chair = activeChair(request.chairId());
        validateAvailability(id, meeting.getPatient().getId(), doctor.getId(), chair.getId(), start, end);
        boolean rescheduled = !meeting.getStartsAt().equals(start) || !meeting.getEndsAt().equals(end)
                || !meeting.getDoctor().getId().equals(doctor.getId()) || !meeting.getChair().getId().equals(chair.getId());
        MeetingStatus previous = meeting.getStatus();
        apply(meeting, doctor, chair, start, end, request.reason());
        if (rescheduled) {
            meeting.setStatus(MeetingStatus.Pendiente);
        }
        meetings.flush();
        recordAudit(meeting, "UPDATE", previous);
        if (rescheduled) {
            events.publishEvent(new MeetingNotificationEvent(meeting.getId(), NotificationType.Reprogramacion));
        }
        return MeetingResponse.from(meeting);
    }

    @Transactional(readOnly = true)
    public MeetingResponse get(@NotNull UUID id) {
        User actor = scopedUser();
        Meeting meeting = find(id);
        checkAccess(actor, meeting);
        return MeetingResponse.from(meeting);
    }

    @Transactional(readOnly = true)
    public MeetingListResponse listByDoctor(@NotNull @Positive Long doctorId,
                                            @Min(0) int page, @Min(1) @Max(100) int size) {
        User actor = scopedUser();
        if (actor != null && actor.getRol() == UserRol.Odontologo && !actor.getId().equals(doctorId)) {
            throw new AccessDeniedException("Solo puede consultar su propia agenda.");
        }
        userWithRole(doctorId, UserRol.Odontologo);
        var pageable = PageRequest.of(page, size);
        var result = actor != null && actor.getRol() == UserRol.Paciente
                ? meetings.findByDoctor_IdAndPatient_IdOrderByStartsAtAscIdAsc(doctorId, actor.getId(), pageable)
                : meetings.findByDoctor_IdOrderByStartsAtAscIdAsc(doctorId, pageable);
        return MeetingListResponse.from(result);
    }

    public MeetingResponse changeStatus(@NotNull UUID id, @Valid MeetingStatusRequest request) {
        User actor = scopedUser();
        Meeting meeting = find(id);
        checkAccess(actor, meeting);
        if (actor != null && actor.getRol() == UserRol.Paciente
                && request.status() != MeetingStatus.Confirmada && request.status() != MeetingStatus.Cancelada) {
            throw new AccessDeniedException("El paciente solo puede confirmar o cancelar su cita.");
        }
        if (!Objects.equals(meeting.getVersion(), request.version())) {
            throw new MeetingConflictException("La cita cambió. Consúltela antes de volver a editar.");
        }
        MeetingStatus previous = meeting.getStatus();
        if (previous == request.status()) { return MeetingResponse.from(meeting); }
        boolean allowed = switch (previous) {
            case Pendiente -> request.status() == MeetingStatus.Confirmada || request.status() == MeetingStatus.Cancelada;
            case Confirmada -> request.status() == MeetingStatus.Cancelada || request.status() == MeetingStatus.Completada;
            case Cancelada, Completada -> false;
        };
        if (!allowed) {
            throw new MeetingConflictException("Transición de estado no permitida: " + previous + " a " + request.status() + ".");
        }
        if (request.status() == MeetingStatus.Cancelada) { checkPatientAdvance(actor, meeting); }
        if (request.status() == MeetingStatus.Confirmada && !meeting.getStartsAt().isAfter(clock.instant())) {
            throw new MeetingConflictException("No se puede confirmar una cita que ya inició.");
        }
        if (request.status() == MeetingStatus.Completada && meeting.getStartsAt().isAfter(clock.instant())) {
            throw new MeetingConflictException("No se puede completar una cita antes de su inicio.");
        }
        meeting.setStatus(request.status());
        meetings.flush();
        recordAudit(meeting, "UPDATE", previous);
        return MeetingResponse.from(meeting);
    }

    // Until the authentication module is integrated, non-staff principals must use their persisted email as name.
    // IDs supplied by the client are never accepted as proof of identity.
    private User scopedUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_Administrador")
                || a.getAuthority().equals("ROLE_Recepcionista"))) { return null; }
        User actor = users.findByNormalizedEmail(authentication.getName().strip().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new AccessDeniedException("La identidad autenticada no está vinculada a un usuario."));
        if ((actor.getRol() != UserRol.Paciente && actor.getRol() != UserRol.Odontologo)
                || authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_" + actor.getRol().name()))) {
            throw new AccessDeniedException("El rol autenticado no corresponde al usuario.");
        }
        return actor;
    }

    private void checkCreateScope(User actor, Long patientId, Long doctorId) {
        if (actor == null) { return; }
        Long owner = actor.getRol() == UserRol.Paciente ? patientId : doctorId;
        if (!actor.getId().equals(owner)) { throw new AccessDeniedException("Solo puede gestionar sus propias citas."); }
    }

    private void checkAccess(User actor, Meeting meeting) {
        checkCreateScope(actor, meeting.getPatient().getId(), meeting.getDoctor().getId());
    }

    private void checkPatientAdvance(User actor, Meeting meeting) {
        if (actor != null && actor.getRol() == UserRol.Paciente
                && Duration.between(clock.instant(), meeting.getStartsAt()).compareTo(Duration.ofHours(24)) < 0) {
            throw new MeetingConflictException("Con menos de 24 horas de anticipación debe contactar a recepción.");
        }
    }

    private Meeting find(UUID id) {
        return meetings.findById(id).orElseThrow(() -> new MeetingNotFoundException("La cita no existe."));
    }

    private void validateTime(Instant start, Instant end) {
        if (!start.isAfter(clock.instant())) {
            throw new InvalidMeetingException("La cita debe iniciar en el futuro.");
        }
        if (!end.isAfter(start)) {
            throw new InvalidMeetingException("La hora de fin debe ser posterior al inicio.");
        }
    }

    private User userWithRole(Long id, UserRol role) {
        User user = users.findById(id).orElseThrow(() -> new MeetingNotFoundException("El usuario indicado no existe."));
        if (user.getRol() != role) {
            throw new InvalidMeetingException("El usuario indicado debe tener el rol " + role + ".");
        }
        return user;
    }

    private DentalChair activeChair(Long id) {
        DentalChair chair = chairs.findById(id).orElseThrow(() -> new MeetingNotFoundException("El sillón no existe."));
        if (!chair.isActive()) {
            throw new InvalidMeetingException("El sillón está inactivo.");
        }
        return chair;
    }

    private void validateAvailability(UUID excludedId, Long patientId, Long doctorId, Long chairId,
                                      Instant start, Instant end) {
        if (meetings.overlaps(excludedId, RESERVED, start, end, patientId, doctorId, chairId)) {
            throw new MeetingConflictException("El paciente, odontólogo o sillón ya tiene una cita en ese horario.");
        }
    }

    private void apply(Meeting meeting, User doctor, DentalChair chair, Instant start, Instant end, String reason) {
        meeting.setDoctor(doctor);
        meeting.setChair(chair);
        meeting.setStartsAt(start);
        meeting.setEndsAt(end);
        meeting.setReason(reason.strip());
    }

    private void recordAudit(Meeting meeting, String action) {
        recordAudit(meeting, action, null);
    }

    private void recordAudit(Meeting meeting, String action, MeetingStatus previous) {
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        audit.save(new MeetingAudit(meeting.getId(), actor, action, clock.instant(), previous, meeting.getStatus()));
    }
}
