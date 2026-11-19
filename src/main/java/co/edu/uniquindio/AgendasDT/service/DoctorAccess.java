package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.domain.*;
import co.edu.uniquindio.AgendasDT.domain.enums.*;
import co.edu.uniquindio.AgendasDT.exception.ClinicalException;
import co.edu.uniquindio.AgendasDT.repository.*;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Component
@PreAuthorize("hasRole('Odontologo')")
public class DoctorAccess {
    private final UserRepository users;
    private final MeetingRepository meetings;
    public DoctorAccess(UserRepository users, MeetingRepository meetings) { this.users = users; this.meetings = meetings; }

    public User current() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName().strip().toLowerCase(Locale.ROOT);
        User doctor = users.findByNormalizedEmail(email)
                .orElseThrow(() -> new AccessDeniedException("El token no corresponde a un odontólogo registrado."));
        if (doctor.getRol() != UserRol.Odontologo) { throw new AccessDeniedException("El usuario no es odontólogo."); }
        return doctor;
    }

    public User patient(Long patientId, User doctor) {
        if (!meetings.existsByDoctor_IdAndPatient_IdAndStatusNot(doctor.getId(), patientId, MeetingStatus.Cancelada)) {
            throw new AccessDeniedException("El paciente no está asignado a este odontólogo.");
        }
        return users.findById(patientId).orElseThrow(() -> new ClinicalException(NOT_FOUND, "El paciente no existe."));
    }

    public Meeting meeting(UUID meetingId, User doctor, boolean lock) {
        Meeting meeting = (lock ? meetings.lockForConsultation(meetingId) : meetings.findById(meetingId))
                .orElseThrow(() -> new ClinicalException(NOT_FOUND, "La cita no existe."));
        if (!meeting.getDoctor().getId().equals(doctor.getId())) {
            throw new AccessDeniedException("La cita está asignada a otro odontólogo.");
        }
        return meeting;
    }

    public void author(ClinicalRecord record, User doctor) {
        if (!record.getDoctor().getId().equals(doctor.getId())) {
            throw new AccessDeniedException("Solo el autor puede agregar adendas o prescripciones a esta evolución.");
        }
    }
}
