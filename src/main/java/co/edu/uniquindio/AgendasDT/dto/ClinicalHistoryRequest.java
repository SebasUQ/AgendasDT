package co.edu.uniquindio.AgendasDT.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ClinicalHistoryRequest(@NotNull UUID treatmentId,
                                     @NotBlank String diagnosis,
                                     String observations) {
}
