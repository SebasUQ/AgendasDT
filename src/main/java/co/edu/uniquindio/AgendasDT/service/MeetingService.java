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
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
@PreAuthorize("hasAnyRole('Administrador', 'Recepcionista')")
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
        Meeting meeting = find(id);
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
        apply(meeting, doctor, chair, start, end, request.reason());
        if (rescheduled) {
            meeting.setStatus(MeetingStatus.Pendiente);
        }
        meetings.flush();
        recordAudit(meeting, "UPDATE");
        if (rescheduled) {
            events.publishEvent(new MeetingNotificationEvent(meeting.getId(), NotificationType.Reprogramacion));
        }
        return MeetingResponse.from(meeting);
    }

    @Transactional(readOnly = true)
    public MeetingResponse get(@NotNull UUID id) { return MeetingResponse.from(find(id)); }

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
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        audit.save(new MeetingAudit(meeting.getId(), actor, action, clock.instant()));
    }
}
