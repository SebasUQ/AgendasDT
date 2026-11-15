package co.edu.uniquindio.AgendasDT.service;

import java.util.List;

import co.edu.uniquindio.AgendasDT.domain.ClinicalHistory;
import co.edu.uniquindio.AgendasDT.domain.Treatment;
import co.edu.uniquindio.AgendasDT.domain.User;
import co.edu.uniquindio.AgendasDT.domain.enums.UserRol;
import co.edu.uniquindio.AgendasDT.dto.ClinicalHistoryRequest;
import co.edu.uniquindio.AgendasDT.dto.ClinicalHistoryResponse;
import co.edu.uniquindio.AgendasDT.repository.ClinicalHistoryRepository;
import co.edu.uniquindio.AgendasDT.repository.TreatmentRepository;
import co.edu.uniquindio.AgendasDT.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClinicalHistoryService {

    private final ClinicalHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final TreatmentRepository treatmentRepository;

    public ClinicalHistoryService(ClinicalHistoryRepository historyRepository,
                                  UserRepository userRepository,
                                  TreatmentRepository treatmentRepository) {
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.treatmentRepository = treatmentRepository;
    }

    @Transactional(readOnly = true)
    public List<ClinicalHistoryResponse> getByPatientId(Long patientId) {
        User patient = findPatient(patientId);
        return historyRepository.findByPatient(patient).stream()
                .map(ClinicalHistoryResponse::from)
                .toList();
    }

    @Transactional
    public ClinicalHistoryResponse addRecord(Long patientId, ClinicalHistoryRequest request) {
        User patient = findPatient(patientId);

        Treatment treatment = treatmentRepository.findById(request.treatmentId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Tratamiento no encontrado: " + request.treatmentId()));

        if (historyRepository.existsByTreatment(treatment)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "El tratamiento ya está asociado a un registro del historial");
        }

        ClinicalHistory history = new ClinicalHistory();
        history.setPatientID(patient);
        history.setTreatmentID(treatment);
        history.setDiagnosis(request.diagnosis());
        history.setObservations(request.observations());

        return ClinicalHistoryResponse.from(historyRepository.save(history));
    }

    private User findPatient(Long patientId) {
        User user = userRepository.findById(patientId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Paciente no encontrado: " + patientId));
        if (user.getRol() != UserRol.Paciente) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "El usuario indicado no es un paciente");
        }
        return user;
    }
}
