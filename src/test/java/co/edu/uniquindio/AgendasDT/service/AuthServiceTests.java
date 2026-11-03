package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.config.JwtConfig;
import co.edu.uniquindio.AgendasDT.config.JwtProperties;
import co.edu.uniquindio.AgendasDT.domain.User;
import co.edu.uniquindio.AgendasDT.domain.UserAudit;
import co.edu.uniquindio.AgendasDT.domain.enums.UserRol;
import co.edu.uniquindio.AgendasDT.dto.*;
import co.edu.uniquindio.AgendasDT.exception.*;
import co.edu.uniquindio.AgendasDT.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import java.time.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {
    @Mock UserRepository users;
    @Mock UserAuditRepository audit;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(4);
    private final Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    private final JwtProperties properties = new JwtProperties("clave-solo-para-pruebas-automaticas-0123456789",
            Duration.ofHours(8));
    private final JwtConfig jwt = new JwtConfig(properties);
    private final JwtDecoder decoder = jwt.jwtDecoder();
    private AuthService service;

    @BeforeEach
    void setup() {
        service = new AuthService(users, audit, passwords, jwt.jwtEncoder(), properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void loginReturnsSignedTokenWithEmailRoleAndName() {
        when(users.findByNormalizedEmail("ana@example.com")).thenReturn(Optional.of(user("Clave-segura-1")));
        AuthResponse response = service.login(new LoginRequest("  Ana@Example.com ", "Clave-segura-1"));
        assertEquals(UserRol.Recepcionista, response.rol());
        assertEquals("Ana Pérez", response.nombre());
        assertEquals("ana@example.com", response.email());
        Jwt token = decoder.decode(response.token());
        assertEquals("ana@example.com", token.getSubject());
        assertEquals("Recepcionista", token.getClaimAsString("rol"));
        assertEquals("Ana Pérez", token.getClaimAsString("nombre"));
        assertEquals(123456789L, ((Number) token.getClaim("uid")).longValue());
        assertEquals(now.plus(Duration.ofHours(8)), token.getExpiresAt());
    }

    @Test
    void wrongPasswordIsRejected() {
        when(users.findByNormalizedEmail("ana@example.com")).thenReturn(Optional.of(user("Clave-segura-1")));
        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("ana@example.com", "otra-clave-123")));
    }

    @Test
    void accountWithoutPasswordCannotLogIn() {
        User user = user("Clave-segura-1");
        user.setPasswordHash(null);
        when(users.findByNormalizedEmail("ana@example.com")).thenReturn(Optional.of(user));
        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("ana@example.com", "cuenta-inexistente")));
    }

    @Test
    void unknownEmailGetsSameMessageAsWrongPassword() {
        InvalidCredentialsException unknown = assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("nadie@example.com", "Clave-segura-1")));
        when(users.findByNormalizedEmail("ana@example.com")).thenReturn(Optional.of(user("Clave-segura-1")));
        InvalidCredentialsException wrong = assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("ana@example.com", "otra-clave-123")));
        assertEquals(wrong.getMessage(), unknown.getMessage());
        assertEquals("Correo o contraseña incorrectos.", unknown.getMessage());
    }

    @Test
    void registerCreatesPatientWithHashedPasswordAndAudit() {
        when(users.emailExists("luis@example.com")).thenReturn(false);
        RegisterRequest request = new RegisterRequest(1094956322L, " Luis ", " Gómez ", LocalDate.of(2000, 3, 4),
                " Luis@Example.com ", "+573001112233", "Clave-segura-2");
        AuthResponse response = service.register(request);
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        User user = saved.getValue();
        assertEquals(1094956322L, user.getId());
        assertEquals("Luis", user.getName());
        assertEquals("Gómez", user.getLastName());
        assertEquals("luis@example.com", user.getEmail());
        assertEquals(UserRol.Paciente, user.getRol());
        assertNotEquals("Clave-segura-2", user.getPasswordHash());
        assertTrue(passwords.matches("Clave-segura-2", user.getPasswordHash()));
        assertEquals(UserRol.Paciente, response.rol());
        assertEquals("luis@example.com", decoder.decode(response.token()).getSubject());
        ArgumentCaptor<UserAudit> entry = ArgumentCaptor.forClass(UserAudit.class);
        verify(audit).save(entry.capture());
        assertEquals("luis@example.com", entry.getValue().getActor());
        assertEquals("CREATE", entry.getValue().getAction());
    }

    @Test
    void registerRejectsDuplicateDocumentOrEmail() {
        RegisterRequest request = new RegisterRequest(1094956322L, "Luis", "Gómez", LocalDate.of(2000, 3, 4),
                "luis@example.com", "+573001112233", "Clave-segura-2");
        when(users.existsById(1094956322L)).thenReturn(true);
        assertEquals("Ya existe un usuario con esa identificación.",
                assertThrows(UserConflictException.class, () -> service.register(request)).getMessage());
        when(users.existsById(1094956322L)).thenReturn(false);
        when(users.emailExists("luis@example.com")).thenReturn(true);
        assertEquals("El correo ya está registrado.",
                assertThrows(UserConflictException.class, () -> service.register(request)).getMessage());
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void secretMustBeLongEnough() {
        assertThrows(IllegalStateException.class, () -> new JwtProperties("corta", Duration.ofHours(8)));
        assertThrows(IllegalStateException.class, () -> new JwtProperties(null, Duration.ofHours(8)));
        assertThrows(IllegalStateException.class,
                () -> new JwtProperties("clave-solo-para-pruebas-automaticas-0123456789", Duration.ZERO));
    }

    @Test
    void requestsNeverPrintPasswords() {
        assertFalse(new LoginRequest("ana@example.com", "Clave-segura-1").toString().contains("Clave-segura-1"));
        assertFalse(new RegisterRequest(1L, "Ana", "Pérez", LocalDate.of(2000, 1, 1), "ana@example.com",
                "+573001112233", "Clave-segura-1").toString().contains("Clave-segura-1"));
    }

    private User user(String password) {
        User user = new User();
        user.setId(123456789L);
        user.setName("Ana");
        user.setLastName("Pérez");
        user.setEmail("ana@example.com");
        user.setRol(UserRol.Recepcionista);
        user.setPasswordHash(passwords.encode(password));
        return user;
    }
}
