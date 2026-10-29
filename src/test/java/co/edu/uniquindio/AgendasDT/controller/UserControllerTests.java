package co.edu.uniquindio.AgendasDT.controller;

import co.edu.uniquindio.AgendasDT.config.MethodSecurityConfig;
import co.edu.uniquindio.AgendasDT.domain.enums.UserRol;
import co.edu.uniquindio.AgendasDT.dto.*;
import co.edu.uniquindio.AgendasDT.exception.ApiExceptionHandler;
import co.edu.uniquindio.AgendasDT.exception.UserConflictException;
import co.edu.uniquindio.AgendasDT.exception.UserNotFoundException;
import co.edu.uniquindio.AgendasDT.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import java.time.LocalDate;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({UserController.class, CsrfController.class})
@Import({MethodSecurityConfig.class, ApiExceptionHandler.class})
class UserControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean UserService service;
    private static final String CREATE = """
            {"id":123,"name":"Ana","lastName":"Perez","birthDate":"1995-05-12",
            "email":"ana@example.com","phone":"+573001234567","rol":"Paciente"}
            """;
    private static final String UPDATE = """
            {"name":"Ana","lastName":"Perez","birthDate":"1995-05-12",
            "email":"ana@example.com","phone":"+573001234567","rol":"Paciente","version":0}
            """;

    @Test
    @WithMockUser(roles = "Administrador")
    void createsWithLocation() throws Exception {
        when(service.create(any())).thenReturn(response());
        mvc.perform(post("/api/users").with(csrf()).contentType("application/json").content(CREATE))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/users/123"))
                .andExpect(jsonPath("$.id").value(123));
    }

    @Test
    @WithMockUser(roles = "Administrador")
    void editsUser() throws Exception {
        when(service.update(eq(123L), any())).thenReturn(response());
        mvc.perform(put("/api/users/123").with(csrf()).contentType("application/json").content(UPDATE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
    }

    @Test
    @WithMockUser(roles = "Administrador")
    void validatesInputWithoutCallingService() throws Exception {
        mvc.perform(post("/api/users").with(csrf()).contentType("application/json")
                .content(CREATE.replace("ana@example.com", "invalid").replace("1995-05-12", "2995-05-12")
                        .replace("+573001234567", "abc").replace("\"Ana\"", "\" \"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.birthDate").exists())
                .andExpect(jsonPath("$.errors.phone").exists()).andExpect(jsonPath("$.errors.name").exists());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "Administrador")
    void rejectsInvalidRole() throws Exception {
        mvc.perform(post("/api/users").with(csrf()).contentType("application/json")
                .content(CREATE.replace("Paciente", "Superuser")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "Administrador")
    void reportsNotFound() throws Exception {
        when(service.get(123L)).thenThrow(new UserNotFoundException());
        mvc.perform(get("/api/users/123")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "Administrador")
    void reportsDuplicateConflict() throws Exception {
        when(service.create(any())).thenThrow(new UserConflictException("El correo ya está registrado."));
        mvc.perform(post("/api/users").with(csrf()).contentType("application/json").content(CREATE))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("El correo ya está registrado."));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/users").with(csrf()).contentType("application/json").content(CREATE))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "Administrador")
    void preservesCsrfProtection() throws Exception {
        mvc.perform(post("/api/users").contentType("application/json").content(CREATE))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "Paciente")
    void deniesAdministrationRoutesToPatient() throws Exception {
        mvc.perform(post("/api/users").with(csrf()).contentType("application/json").content(CREATE))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/users/123").with(csrf()).contentType("application/json").content(UPDATE))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users/123")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "Administrador")
    void csrfEndpointProvidesUsableTokenForSameSession() throws Exception {
        var tokenResult = mvc.perform(get("/api/csrf"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty()).andReturn();
        var csrfToken = (org.springframework.security.web.csrf.CsrfToken)
                tokenResult.getRequest().getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
        when(service.create(any())).thenReturn(response());
        mvc.perform(post("/api/users").session((MockHttpSession) tokenResult.getRequest().getSession())
                        .header(csrfToken.getHeaderName(), csrfToken.getToken())
                        .contentType("application/json").content(CREATE))
                .andExpect(status().isCreated());
    }

    private UserResponse response() {
        return new UserResponse(123L, "Ana", "Perez", LocalDate.of(1995, 5, 12),
                "ana@example.com", "+573001234567", UserRol.Paciente, 0L);
    }

    @Test
    @WithMockUser(roles = "Administrador")
    void acceptsPasswordWithoutReturningCredentials() throws Exception {
        when(service.create(any())).thenReturn(response());
        mvc.perform(post("/api/users").with(csrf()).contentType("application/json")
                        .content(CREATE.replace("\"rol\":\"Paciente\"", "\"rol\":\"Paciente\",\"password\":\"Example-password-123\"")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        var captured = org.mockito.ArgumentCaptor.forClass(UserCreateRequest.class);
        verify(service).create(captured.capture());
        org.junit.jupiter.api.Assertions.assertEquals("Example-password-123", captured.getValue().password());
    }

    @Test
    @WithMockUser(roles = "Administrador")
    void rejectsWeakPassword() throws Exception {
        mvc.perform(post("/api/users").with(csrf()).contentType("application/json")
                        .content(CREATE.replace("\"rol\":\"Paciente\"", "\"rol\":\"Paciente\",\"password\":\"short\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.password").exists());
        verifyNoInteractions(service);
    }
}
