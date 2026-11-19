package co.edu.uniquindio.AgendasDT.controller;

import co.edu.uniquindio.AgendasDT.config.MethodSecurityConfig;
import co.edu.uniquindio.AgendasDT.dto.DoctorDtos.*;
import co.edu.uniquindio.AgendasDT.exception.*;
import co.edu.uniquindio.AgendasDT.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DoctorController.class)
@Import({MethodSecurityConfig.class,ApiExceptionHandler.class})
class DoctorControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean DoctorService doctor;
    @MockitoBean ClinicalService clinical;
    @MockitoBean DoctorChargeService charges;
    @MockitoBean PrescriptionPdfService pdf;

    @Test @WithMockUser(roles="Odontologo")
    void agendaIsPaginatedAndNotCached() throws Exception {
        when(doctor.agenda(null,null,0,20)).thenReturn(new PageResult<>(List.of(),0,20,0,0));
        mvc.perform(get("/api/doctor/agenda")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.content").isArray());
    }

    @Test @WithMockUser(roles="Odontologo")
    void rejectsInvalidDatePageAndProfileFields() throws Exception {
        mvc.perform(get("/api/doctor/agenda?date=not-a-date")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/doctor/patients?size=101")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/doctor/profile").with(csrf()).contentType("application/json").content("{\"professionalRegistration\":\" \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(doctor,clinical,charges);
    }

    @Test @WithMockUser(roles="Odontologo")
    void clinicalFinalizationRequiresMandatoryFields() throws Exception {
        mvc.perform(post("/api/doctor/meetings/"+UUID.randomUUID()+"/records").with(csrf())
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.diagnosis").exists())
                .andExpect(jsonPath("$.errors.meetingVersion").exists());
        verifyNoInteractions(clinical);
    }

    @Test @WithMockUser(roles="Paciente")
    void patientsCannotReadClinicalOrFinancialRoutes() throws Exception {
        mvc.perform(get("/api/doctor/patients/1/history")).andExpect(status().isForbidden());
        mvc.perform(get("/api/doctor/procedures")).andExpect(status().isForbidden());
        verifyNoInteractions(doctor,clinical,charges);
    }

    @Test @WithMockUser(roles="Administrador")
    void administrativeRoleDoesNotGrantClinicalAccess() throws Exception {
        mvc.perform(get("/api/doctor/patients/1/odontogram")).andExpect(status().isForbidden());
        verifyNoInteractions(clinical);
    }

    @Test @WithMockUser(roles="Odontologo")
    void noUpdateOrDeleteRouteExistsForFinalizedClinicalRecords() throws Exception {
        String path="/api/doctor/records/"+UUID.randomUUID();
        mvc.perform(put(path).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete(path).with(csrf())).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(clinical);
    }

    @Test @WithMockUser(roles="Odontologo")
    void handlesClinicalConflictsAndMissingDocuments() throws Exception {
        UUID id=UUID.randomUUID();
        when(clinical.getRecord(id)).thenThrow(new ClinicalException(HttpStatus.NOT_FOUND,"La evolución no existe."));
        mvc.perform(get("/api/doctor/records/"+id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("La evolución no existe."));
    }

    @Test void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/doctor/agenda")).andExpect(status().isUnauthorized());
    }
}
