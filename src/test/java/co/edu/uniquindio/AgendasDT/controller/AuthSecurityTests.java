package co.edu.uniquindio.AgendasDT.controller;

import co.edu.uniquindio.AgendasDT.config.*;
import co.edu.uniquindio.AgendasDT.domain.enums.UserRol;
import co.edu.uniquindio.AgendasDT.dto.*;
import co.edu.uniquindio.AgendasDT.exception.*;
import co.edu.uniquindio.AgendasDT.service.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real security chain: public login, bearer tokens, roles and CORS for the Angular client. */
@WebMvcTest({AuthController.class, UserController.class})
@Import({AuthSecurityConfig.class, JwtConfig.class, MethodSecurityConfig.class, ApiExceptionHandler.class})
@TestPropertySource(properties = {
        "security.jwt.secret=clave-solo-para-pruebas-automaticas-0123456789",
        "security.jwt.expiration=PT8H",
        "security.cors.allowed-origins=http://localhost:4200, https://doctooth.example"})
class AuthSecurityTests {
    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;
    @MockitoBean AuthService auth;
    @MockitoBean UserService users;
    private static final String LOGIN = """
            {"email":"admin@example.com","password":"Clave-segura-1"}
            """;
    private static final String REGISTER = """
            {"documento":1094956322,"nombre":"Luis","apellido":"Gómez","fechaNacimiento":"2000-03-04",
             "email":"luis@example.com","telefono":"+573001112233","password":"Clave-segura-2"}
            """;
    private static final String CREATE_USER = """
            {"id":123,"name":"Ana","lastName":"Perez","birthDate":"1995-05-12",
            "email":"ana@example.com","phone":"+573001234567","rol":"Paciente"}
            """;

    @Test
    void loginIsPublicAndNeedsNoCsrf() throws Exception {
        when(auth.login(any())).thenReturn(new AuthResponse("token", UserRol.Administrador, "Ana Pérez", "admin@example.com"));
        mvc.perform(post("/api/auth/login").contentType("application/json").content(LOGIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").value("token"))
                .andExpect(jsonPath("$.rol").value("Administrador"))
                .andExpect(jsonPath("$.nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.email").value("admin@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
        verify(auth).login(new LoginRequest("admin@example.com", "Clave-segura-1"));
    }

    @Test
    void registerIsPublicAndCreatesPatient() throws Exception {
        when(auth.register(any())).thenReturn(new AuthResponse("token", UserRol.Paciente, "Luis Gómez", "luis@example.com"));
        mvc.perform(post("/api/auth/register").contentType("application/json").content(REGISTER))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.rol").value("Paciente"));
    }

    @Test
    void badCredentialsAndInvalidBodiesAreRejected() throws Exception {
        when(auth.login(any())).thenThrow(new InvalidCredentialsException());
        mvc.perform(post("/api/auth/login").contentType("application/json").content(LOGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Correo o contraseña incorrectos."));
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"email\":\"no-es-correo\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content(REGISTER.replace("Clave-segura-2", "corta")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.password").exists());
        when(auth.register(any())).thenThrow(new UserConflictException("El correo ya está registrado."));
        mvc.perform(post("/api/auth/register").contentType("application/json").content(REGISTER))
                .andExpect(status().isConflict());
    }

    @Test
    void protectedRoutesNeedToken() throws Exception {
        mvc.perform(get("/api/users/123")).andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"));
        verifyNoInteractions(users);
    }

    @Test
    void validTokenGivesAccessByRoleWithoutCsrf() throws Exception {
        when(users.get(123L)).thenReturn(userResponse());
        when(users.create(any())).thenReturn(userResponse());
        String admin = token("Administrador", Instant.now().plusSeconds(600));
        mvc.perform(get("/api/users/123").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + admin)
                .contentType("application/json").content(CREATE_USER)).andExpect(status().isCreated());
    }

    @Test
    void roleFromTokenIsEnforced() throws Exception {
        String patient = token("Paciente", Instant.now().plusSeconds(600));
        mvc.perform(get("/api/users/123").header("Authorization", "Bearer " + patient)).andExpect(status().isForbidden());
        verifyNoInteractions(users);
    }

    @Test
    void expiredForgedOrMalformedTokensAreRejected() throws Exception {
        String expired = token("Administrador", Instant.now().minusSeconds(120));
        JwtEncoder other = new JwtConfig(new JwtProperties("otra-clave-distinta-para-firmar-0123456789",
                Duration.ofHours(8))).jwtEncoder();
        String forged = other.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                claims("Administrador", Instant.now().plusSeconds(600)))).getTokenValue();
        for (String token : new String[] {expired, forged, "no-es-un-token"}) {
            mvc.perform(get("/api/users/123").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(users);
    }

    @Test
    void corsAllowsOnlyConfiguredFrontends() throws Exception {
        for (String origin : new String[] {"http://localhost:4200", "https://doctooth.example"}) {
            mvc.perform(options("/api/auth/login").header("Origin", origin)
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "content-type"))
                    .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", origin));
        }
        mvc.perform(options("/api/users/123").header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk());
        mvc.perform(options("/api/auth/login").header("Origin", "https://otro-sitio.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    private String token(String role, Instant expiresAt) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                claims(role, expiresAt))).getTokenValue();
    }

    private JwtClaimsSet claims(String role, Instant expiresAt) {
        return JwtClaimsSet.builder().subject("admin@example.com").claim("rol", role)
                .issuedAt(expiresAt.minus(1, ChronoUnit.HOURS)).expiresAt(expiresAt).build();
    }

    private UserResponse userResponse() {
        return new UserResponse(123L, "Ana", "Perez", LocalDate.of(1995, 5, 12),
                "ana@example.com", "+573001234567", UserRol.Paciente, 0L);
    }
}
