package co.edu.uniquindio.AgendasDT.dto;

import co.edu.uniquindio.AgendasDT.domain.ClinicalHistory;

import java.util.UUID;

public record ClinicalHistoryResponse(UUID id, Long patientId, UUID treatmentId,
                                      String diagnosis, String observations) {

    public static ClinicalHistoryResponse from(ClinicalHistory h) {
        return new ClinicalHistoryResponse(
                h.getID(),
                h.getPatientID().getId(),
                h.getTreatmentID().getID(),
                h.getDiagnosis(),
                h.getObservations());
    }
}
