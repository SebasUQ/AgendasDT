package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.config.JwtProperties;
import co.edu.uniquindio.AgendasDT.domain.User;
import co.edu.uniquindio.AgendasDT.domain.UserAudit;
import co.edu.uniquindio.AgendasDT.domain.enums.UserRol;
import co.edu.uniquindio.AgendasDT.dto.*;
import co.edu.uniquindio.AgendasDT.exception.*;
import co.edu.uniquindio.AgendasDT.repository.*;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class AuthService {
    private final UserRepository users;
    private final UserAuditRepository audit;
    private final PasswordEncoder passwords;
    private final JwtEncoder tokens;
    private final JwtProperties jwt;
    private final Clock clock;
    private volatile String unknownUserHash;

    public AuthService(UserRepository users, UserAuditRepository audit, PasswordEncoder passwords,
                       JwtEncoder tokens, JwtProperties jwt, Clock clock) {
        this.users = users;
        this.audit = audit;
        this.passwords = passwords;
        this.tokens = tokens;
        this.jwt = jwt;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(@Valid LoginRequest request) {
        Optional<User> found = users.findByNormalizedEmail(normalizeEmail(request.email()));
        String hash = found.map(User::getPasswordHash).orElse(null);
        // Unknown accounts still pay the hashing cost, so response times do not reveal registered emails.
        boolean matches = passwords.matches(request.password(), hash != null ? hash : unknownUserHash());
        if (hash == null || !matches) {
            throw new InvalidCredentialsException();
        }
        return issue(found.get());
    }

    public AuthResponse register(@Valid RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsById(request.documento())) {
            throw new UserConflictException("Ya existe un usuario con esa identificación.");
        }
        if (users.emailExists(email)) {
            throw new UserConflictException("El correo ya está registrado.");
        }
        User user = new User();
        user.setId(request.documento());
        user.setName(request.nombre().strip());
        user.setLastName(request.apellido().strip());
        user.setBirthDate(request.fechaNacimiento());
        user.setEmail(email);
        user.setPhone(request.telefono());
        user.setRol(UserRol.Paciente);
        user.setPasswordHash(passwords.encode(request.password()));
        users.saveAndFlush(user);
        audit.save(new UserAudit(user.getId(), email, "CREATE", null, UserRol.Paciente));
        return issue(user);
    }

    private AuthResponse issue(User user) {
        Instant now = clock.instant();
        String fullName = user.getName() + " " + user.getLastName();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("agendas-doctooth")
                .subject(user.getEmail())
                .issuedAt(now)
                .expiresAt(now.plus(jwt.expiration()))
                .claim("rol", user.getRol().name())
                .claim("nombre", fullName)
                .claim("uid", user.getId())
                .build();
        String token = tokens.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new AuthResponse(token, user.getRol(), fullName, user.getEmail(), user.getId(), user.getPhone());
    }

    private String unknownUserHash() {
        if (unknownUserHash == null) {
            unknownUserHash = passwords.encode("cuenta-inexistente");
        }
        return unknownUserHash;
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
