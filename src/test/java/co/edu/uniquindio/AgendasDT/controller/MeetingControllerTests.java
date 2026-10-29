package co.edu.uniquindio.AgendasDT.controller;

import co.edu.uniquindio.AgendasDT.config.MethodSecurityConfig;
import co.edu.uniquindio.AgendasDT.domain.enums.MeetingStatus;
import co.edu.uniquindio.AgendasDT.dto.MeetingResponse;
import co.edu.uniquindio.AgendasDT.exception.*;
import co.edu.uniquindio.AgendasDT.service.MeetingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MeetingController.class)
@Import({MethodSecurityConfig.class, ApiExceptionHandler.class})
class MeetingControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean MeetingService service;
    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String CREATE = """
            {"patientId":1,"doctorId":2,"chairId":3,"startsAt":"2030-01-10T09:00:00-05:00",
             "endsAt":"2030-01-10T10:00:00-05:00","reason":"Control"}
            """;
    private static final String UPDATE = """
            {"doctorId":2,"chairId":3,"startsAt":"2030-01-10T09:00:00-05:00",
             "endsAt":"2030-01-10T10:00:00-05:00","reason":"Control","version":0}
            """;

    @Test
    @WithMockUser(roles = "Recepcionista")
    void receptionistCreatesMeetingWithLocationAndConfirmationCode() throws Exception {
        when(service.create(any())).thenReturn(response());
        mvc.perform(post("/api/meetings").with(csrf()).contentType("application/json").content(CREATE))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/meetings/" + ID))
                .andExpect(jsonPath("$.confirmationCode").value(ID.toString()))
                .andExpect(jsonPath("$.status").value("Pendiente"));
    }

    @Test
    @WithMockUser(roles = "Administrador")
    void administratorEditsMeeting() throws Exception {
        when(service.update(eq(ID), any())).thenReturn(response());
        mvc.perform(put("/api/meetings/" + ID).with(csrf()).contentType("application/json").content(UPDATE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.patientId").value(1));
    }

    @Test
    @WithMockUser(roles = "Recepcionista")
    void readsMeetingForEditing() throws Exception {
        when(service.get(ID)).thenReturn(response());
        mvc.perform(get("/api/meetings/" + ID)).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    @WithMockUser(roles = "Recepcionista")
    void rejectsMissingRequiredFields() throws Exception {
        mvc.perform(post("/api/meetings").with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.patientId").exists())
                .andExpect(jsonPath("$.errors.startsAt").exists()).andExpect(jsonPath("$.errors.reason").exists());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "Recepcionista")
    void requiresVersionForEditing() throws Exception {
        mvc.perform(put("/api/meetings/" + ID).with(csrf()).contentType("application/json")
                .content(UPDATE.replace(",\"version\":0", "")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.version").exists());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "Recepcionista")
    void rejectsNonPositiveIdsAndBlankReason() throws Exception {
        mvc.perform(post("/api/meetings").with(csrf()).contentType("application/json")
                .content(CREATE.replace("\"patientId\":1", "\"patientId\":0").replace("Control", " ")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.patientId").exists())
                .andExpect(jsonPath("$.errors.reason").exists());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "Recepcionista")
    void requiresOffsetInTimestamps() throws Exception {
        mvc.perform(post("/api/meetings").with(csrf()).contentType("application/json")
                .content(CREATE.replace("2030-01-10T09:00:00-05:00", "2030-01-10T09:00:00")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "Recepcionista")
    void rejectsInvalidUuid() throws Exception {
        mvc.perform(get("/api/meetings/invalid")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "Recepcionista")
    void returnsNotFoundAndBusinessValidationErrors() throws Exception {
        when(service.get(ID)).thenThrow(new MeetingNotFoundException("La cita no existe."));
        mvc.perform(get("/api/meetings/" + ID)).andExpect(status().isNotFound());
        when(service.create(any())).thenThrow(new InvalidMeetingException("La cita debe iniciar en el futuro."));
        mvc.perform(post("/api/meetings").with(csrf()).contentType("application/json").content(CREATE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("La cita debe iniciar en el futuro."));
    }

    @Test
    @WithMockUser(roles = "Recepcionista")
    void returnsConflictForOverlapAndConcurrentWrites() throws Exception {
        when(service.create(any())).thenThrow(new MeetingConflictException("Horario ocupado."))
                .thenThrow(new DataIntegrityViolationException("private SQL detail"));
        mvc.perform(post("/api/meetings").with(csrf()).contentType("application/json").content(CREATE))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/meetings").with(csrf()).contentType("application/json").content(CREATE))
                .andExpect(status().isConflict())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));
        when(service.update(eq(ID), any())).thenThrow(new OptimisticLockingFailureException("stale version"));
        mvc.perform(put("/api/meetings/" + ID).with(csrf()).contentType("application/json").content(UPDATE))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "Paciente")
    void patientCanReachMeetingRoutesWithOwnershipEnforcedByService() throws Exception {
        when(service.create(any())).thenReturn(response());
        when(service.update(eq(ID), any())).thenReturn(response());
        when(service.get(ID)).thenReturn(response());
        mvc.perform(post("/api/meetings").with(csrf()).contentType("application/json").content(CREATE))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/meetings/" + ID).with(csrf()).contentType("application/json").content(UPDATE))
                .andExpect(status().isOk());
        mvc.perform(get("/api/meetings/" + ID)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "Odontologo")
    void dentistCanReachMeetingRoutes() throws Exception {
        when(service.create(any())).thenReturn(response());
        mvc.perform(post("/api/meetings").with(csrf()).contentType("application/json").content(CREATE))
                .andExpect(status().isCreated());
    }

    @Test
    void anonymousCannotAccessMeetings() throws Exception {
        mvc.perform(get("/api/meetings/" + ID)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "Recepcionista")
    void preservesCsrfProtection() throws Exception {
        mvc.perform(post("/api/meetings").contentType("application/json").content(CREATE))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    private MeetingResponse response() {
        return new MeetingResponse(ID, ID.toString(), 1L, 2L, 3L,
                Instant.parse("2030-01-10T14:00:00Z"), Instant.parse("2030-01-10T15:00:00Z"),
                "Control", MeetingStatus.Pendiente, 0L);
    }

    @Test
    @WithMockUser(roles = "Odontologo")
    void listsDoctorMeetingsWithPagination() throws Exception {
        when(service.listByDoctor(2L, 0, 20)).thenReturn(new co.edu.uniquindio.AgendasDT.dto.MeetingListResponse(
                java.util.List.of(response()), 0, 20, 1, 1));
        mvc.perform(get("/api/meetings/doctors/2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].doctorId").value(2))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "Paciente")
    void changesStatusThroughDedicatedRoute() throws Exception {
        when(service.changeStatus(eq(ID), any())).thenReturn(response());
        mvc.perform(patch("/api/meetings/" + ID + "/status").with(csrf()).contentType("application/json")
                        .content("{\"status\":\"Confirmada\",\"version\":0}"))
                .andExpect(status().isOk());
        verify(service).changeStatus(ID, new co.edu.uniquindio.AgendasDT.dto.MeetingStatusRequest(MeetingStatus.Confirmada, 0L));
    }

    @Test
    @WithMockUser(roles = "Recepcionista")
    void validatesPaginationAndStatusRequest() throws Exception {
        mvc.perform(get("/api/meetings/doctors/2?page=-1&size=101")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/meetings/" + ID + "/status").with(csrf()).contentType("application/json")
                        .content("{\"status\":\"INVALID\",\"version\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/meetings/" + ID + "/status").with(csrf()).contentType("application/json")
                        .content("{\"status\":\"Confirmada\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
