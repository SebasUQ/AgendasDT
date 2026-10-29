package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.domain.User;
import co.edu.uniquindio.AgendasDT.domain.UserAudit;
import co.edu.uniquindio.AgendasDT.domain.enums.UserRol;
import co.edu.uniquindio.AgendasDT.dto.*;
import co.edu.uniquindio.AgendasDT.exception.*;
import co.edu.uniquindio.AgendasDT.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {
    @Mock UserRepository users;
    @Mock UserAuditRepository audit;
    UserService service;

    @BeforeEach
    void setup() {
        service = new UserService(users, audit, new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "unused", List.of()));
    }

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    @Test
    void createsNormalizedUserAndAuditsAdministrator() {
        when(users.saveAndFlush(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0);
            assertNull(user.getVersion());
            user.setVersion(0L);
            return user;
        });
        UserResponse response = service.create(createRequest());
        assertEquals("ana@example.com", response.email());
        assertEquals("Ana", response.name());
        assertEquals("+570123456789", response.phone());
        assertEquals(0L, response.version());
        ArgumentCaptor<UserAudit> entry = ArgumentCaptor.forClass(UserAudit.class);
        verify(audit).save(entry.capture());
        assertEquals("admin", entry.getValue().getActor());
        assertEquals("CREATE", entry.getValue().getAction());
        assertEquals(123L, entry.getValue().getUserId());
        assertEquals(UserRol.Paciente, entry.getValue().getNewRole());
    }

    @Test
    void rejectsDuplicateIdentificationWithoutWriting() {
        when(users.existsById(123L)).thenReturn(true);
        assertThrows(UserConflictException.class, () -> service.create(createRequest()));
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void rejectsDuplicateNormalizedEmailWithoutWriting() {
        when(users.emailExists("ana@example.com")).thenReturn(true);
        assertThrows(UserConflictException.class, () -> service.create(createRequest()));
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void editsExistingUserKeepingIdAndAuditsRoleChange() {
        User user = existingUser();
        when(users.findById(123L)).thenReturn(Optional.of(user));
        doAnswer(call -> { user.setVersion(1L); return null; }).when(users).flush();
        UserResponse response = service.update(123L, updateRequest(0L));
        assertEquals(123L, response.id());
        assertEquals(1L, response.version());
        assertEquals(UserRol.Odontologo, response.rol());
        verify(users).emailExistsForAnotherUser("ana@example.com", 123L);
        ArgumentCaptor<UserAudit> entry = ArgumentCaptor.forClass(UserAudit.class);
        verify(audit).save(entry.capture());
        assertEquals(UserRol.Paciente, entry.getValue().getPreviousRole());
        assertEquals(UserRol.Odontologo, entry.getValue().getNewRole());
        assertEquals("UPDATE", entry.getValue().getAction());
    }

    @Test
    void rejectsEditingUnknownUser() {
        assertThrows(UserNotFoundException.class, () -> service.update(123L, updateRequest(0L)));
        verify(users, never()).flush();
        verifyNoInteractions(audit);
    }

    @Test
    void rejectsEmailOwnedByAnotherUserWithoutChangingEntity() {
        User user = existingUser();
        when(users.findById(123L)).thenReturn(Optional.of(user));
        when(users.emailExistsForAnotherUser("ana@example.com", 123L)).thenReturn(true);
        assertThrows(UserConflictException.class, () -> service.update(123L, updateRequest(0L)));
        assertEquals(UserRol.Paciente, user.getRol());
        verify(users, never()).flush();
        verifyNoInteractions(audit);
    }

    @Test
    void rejectsStaleEditWithoutChangingEntity() {
        User user = existingUser();
        when(users.findById(123L)).thenReturn(Optional.of(user));
        assertThrows(UserConflictException.class, () -> service.update(123L, updateRequest(9L)));
        assertEquals(UserRol.Paciente, user.getRol());
        verify(users, never()).flush();
        verifyNoInteractions(audit);
    }

    @Test
    void storesOnlyHashWhenCreatingWithPassword() {
        var request = new UserCreateRequest(123L, "Ana", "Perez", LocalDate.of(1995, 5, 12),
                "ana@example.com", "+573001234567", UserRol.Paciente, "Test-password-123");
        service.create(request);
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        String hash = saved.getValue().getPasswordHash();
        assertNotEquals(request.password(), hash);
        assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches(request.password(), hash));
        assertFalse(request.toString().contains(request.password()));
    }

    @Test
    void editWithoutPasswordPreservesExistingHash() {
        User user = existingUser();
        user.setPasswordHash("existing-hash");
        when(users.findById(123L)).thenReturn(Optional.of(user));
        service.update(123L, updateRequest(0L));
        assertEquals("existing-hash", user.getPasswordHash());
    }

    @Test
    void editsPasswordByEncodingNewValue() {
        User user = existingUser();
        user.setPasswordHash("old-hash");
        when(users.findById(123L)).thenReturn(Optional.of(user));
        service.update(123L, new UserUpdateRequest("Ana", "Perez", LocalDate.of(1995, 5, 12),
                "ana@example.com", "+573001234567", UserRol.Paciente, 0L, "Updated-password-123"));
        assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .matches("Updated-password-123", user.getPasswordHash()));
    }

    @Test
    void rejectsOversizedUtf8PasswordBeforeWriting() {
        var request = new UserCreateRequest(123L, "Ana", "Perez", LocalDate.of(1995, 5, 12),
                "ana@example.com", "+573001234567", UserRol.Paciente, "é".repeat(37));
        assertThrows(InvalidUserException.class, () -> service.create(request));
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    private UserCreateRequest createRequest() {
        return new UserCreateRequest(123L, " Ana ", "Perez", LocalDate.of(1995, 5, 12),
                "ANA@EXAMPLE.COM", "+570123456789", UserRol.Paciente);
    }

    private UserUpdateRequest updateRequest(Long version) {
        return new UserUpdateRequest("Ana", "Perez", LocalDate.of(1995, 5, 12),
                "ANA@EXAMPLE.COM", "+570123456789", UserRol.Odontologo, version);
    }

    private User existingUser() {
        User user = new User();
        user.setId(123L);
        user.setRol(UserRol.Paciente);
        user.setVersion(0L);
        return user;
    }
}
