package co.edu.uniquindio.AgendasDT.controller;

import co.edu.uniquindio.AgendasDT.dto.ClinicalHistoryRequest;
import co.edu.uniquindio.AgendasDT.dto.ClinicalHistoryResponse;
import co.edu.uniquindio.AgendasDT.service.ClinicalHistoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clinical-history")
public class ClinicalHistoryController {

    private final ClinicalHistoryService service;

    public ClinicalHistoryController(ClinicalHistoryService service) {
        this.service = service;
    }

    // Ajusta hasAnyRole/hasAnyAuthority a como mapearon UserRol en su UserDetailsService
    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('Administrador', 'Odontologo', 'Paciente')")
    public List<ClinicalHistoryResponse> getByPatient(@PathVariable Long patientId) {
        return service.getByPatientId(patientId);
    }

    @PostMapping("/patient/{patientId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('Administrador', 'Odontologo')")
    public ClinicalHistoryResponse addRecord(@PathVariable Long patientId,
                                             @Valid @RequestBody ClinicalHistoryRequest request) {
        return service.addRecord(patientId, request);
    }
}
