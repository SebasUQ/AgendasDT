package co.edu.uniquindio.AgendasDT.controller;

import co.edu.uniquindio.AgendasDT.config.MethodSecurityConfig;
import co.edu.uniquindio.AgendasDT.domain.enums.BillStatus;
import co.edu.uniquindio.AgendasDT.domain.enums.PaymentMethod;
import co.edu.uniquindio.AgendasDT.dto.*;
import co.edu.uniquindio.AgendasDT.exception.*;
import co.edu.uniquindio.AgendasDT.service.BillService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BillController.class)
@Import({MethodSecurityConfig.class, ApiExceptionHandler.class})
class BillControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean BillService service;
    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000010");
    private static final UUID MEETING = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String CREATE = """
            {"meetingId":"00000000-0000-4000-8000-000000000001",
             "lines":[{"description":"Limpieza","quantity":1,"unitPrice":80000}]}
            """;
    private static final String UPDATE = """
            {"lines":[{"description":"Resina","quantity":2,"unitPrice":45000.50}],"version":0}
            """;
    private static final String PAY = """
            {"paymentMethod":"Tarjeta","version":1}
            """;

    @Test
    @WithMockUser(roles = "Recepcionista")
    void receptionistCreatesBillWithLocation() throws Exception {
        when(service.create(any())).thenReturn(bill(BillStatus.Pendiente));
        mvc.perform(post("/api/bills").with(csrf()).contentType("application/json").content(CREATE))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/bills/" + ID))
                .andExpect(jsonPath("$.total").value(80000.00))
                .andExpect(jsonPath("$.lines[0].description").value("Limpieza"))
                .andExpect(jsonPath("$.status").value("Pendiente"));
        verify(service).create(new BillCreateRequest(MEETING,
                List.of(new BillLineRequest("Limpieza", 1, new BigDecimal("80000")))));
    }

    @Test
    @WithMockUser(roles = "Administrador")
    void readsBillByIdAndByMeeting() throws Exception {
        when(service.get(ID)).thenReturn(bill(BillStatus.Pendiente));
        when(service.getByMeeting(MEETING)).thenReturn(bill(BillStatus.Pendiente));
        mvc.perform(get("/api/bills/" + ID)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(ID.toString()));
        mvc.perform(get("/api/bills/meeting/" + MEETING)).andExpect(status().isOk())
                .andExpect(jsonPath("$.meetingId").value(MEETING.toString()));
    }

    @Test
    @WithMockUser(roles = "Recepcionista")
    void editsBillLines() throws Exception {
        when(service.update(eq(ID), any())).thenReturn(bill(BillStatus.Pendiente));
        mvc.perform(put("/api/bills/" + ID).with(csrf()).contentType("application/json").content(UPDATE))
                .andExpect(status().isOk());
        verify(service).update(ID, new BillUpdateRequest(
                List.of(new BillLineRequest("Resina", 2, new BigDecimal("45000.50"))), 0L));
    }

    @Test
    @WithMockUser(roles = "Recepcionista")
    void registersPayment() throws Exception {
        when(service.pay(eq(ID), any())).thenReturn(bill(BillStatus.Pagada));
        mvc.perform(post("/api/bills/" + ID + "/payment").with(csrf()).contentType("application/json").content(PAY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("Pagada"))
                .andExpect(jsonPath("$.paymentMethod").value("Tarjeta"));
        verify(service).pay(ID, new BillPaymentRequest(PaymentMethod.Tarjeta, 1L));
    }

    @Test
    @WithMockUser(roles = "Recepcionista")
    void rejectsInvalidRequestsBeforeCallingService() throws Exception {
        mvc.perform(post("/api/bills").with(csrf()).contentType("application/json").content("{\"lines\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.meetingId").exists())
                .andExpect(jsonPath("$.errors.lines").exists());
        mvc.perform(put("/api/bills/" + ID).with(csrf()).contentType("application/json")
                        .content("{\"lines\":[{\"description\":\"Resina\",\"quantity\":0,\"unitPrice\":1}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.version").exists());
        mvc.perform(post("/api/bills/" + ID + "/payment").with(csrf()).contentType("application/json")
                        .content("{\"paymentMethod\":\"Cheque\",\"version\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/bills/no-es-un-id")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "Recepcionista")
    void mapsBusinessErrorsToHttpStatus() throws Exception {
        when(service.get(ID)).thenThrow(new BillNotFoundException("La factura no existe."));
        mvc.perform(get("/api/bills/" + ID)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("La factura no existe."));
        when(service.create(any())).thenThrow(new InvalidBillException("No se puede facturar una cita cancelada."));
        mvc.perform(post("/api/bills").with(csrf()).contentType("application/json").content(CREATE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("No se puede facturar una cita cancelada."));
        when(service.pay(eq(ID), any())).thenThrow(new BillConflictException("Solo se pueden modificar facturas pendientes de pago."));
        mvc.perform(post("/api/bills/" + ID + "/payment").with(csrf()).contentType("application/json").content(PAY))
                .andExpect(status().isConflict());
        when(service.update(eq(ID), any())).thenThrow(new DataIntegrityViolationException("private SQL detail"));
        mvc.perform(put("/api/bills/" + ID).with(csrf()).contentType("application/json").content(UPDATE))
                .andExpect(status().isConflict())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));
    }

    @Test
    @WithMockUser(roles = "Paciente")
    void patientCannotUseBilling() throws Exception { assertDenied(); }

    @Test
    @WithMockUser(roles = "Odontologo")
    void dentistCannotUseBilling() throws Exception { assertDenied(); }

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mvc.perform(get("/api/bills/" + ID)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    private void assertDenied() throws Exception {
        mvc.perform(post("/api/bills").with(csrf()).contentType("application/json").content(CREATE))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/bills/" + ID)).andExpect(status().isForbidden());
        mvc.perform(get("/api/bills/meeting/" + MEETING)).andExpect(status().isForbidden());
        mvc.perform(put("/api/bills/" + ID).with(csrf()).contentType("application/json").content(UPDATE))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/bills/" + ID + "/payment").with(csrf()).contentType("application/json").content(PAY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    private BillResponse bill(BillStatus status) {
        boolean paid = status == BillStatus.Pagada;
        return new BillResponse(ID, MEETING, 1L, Instant.parse("2026-11-02T15:00:00Z"),
                List.of(new BillResponse.Line(1, "Limpieza", 1, new BigDecimal("80000.00"), new BigDecimal("80000.00"))),
                new BigDecimal("80000.00"), status, paid ? PaymentMethod.Tarjeta : null,
                paid ? Instant.parse("2026-11-02T15:30:00Z") : null, paid ? 2L : 0L);
    }
}
