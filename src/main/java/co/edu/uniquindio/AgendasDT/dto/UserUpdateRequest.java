package co.edu.uniquindio.AgendasDT.dto;

import co.edu.uniquindio.AgendasDT.domain.enums.UserRol;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonProperty;

public record UserUpdateRequest(
        @Size(min = 1, max = 100) @Pattern(regexp = "(?s).*\\S.*") String name,
        @Size(min = 1, max = 100) @Pattern(regexp = "(?s).*\\S.*") String lastName,
        @Past LocalDate birthDate,
        @Email @Size(min = 1, max = 254) String email,
        @Pattern(regexp = "\\+?[0-9]{7,15}") String phone,
        UserRol rol,
        @PositiveOrZero Long version,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Size(min = 8, max = 72) @Pattern(regexp = "(?s).*\\S.*") String password) {
    public UserUpdateRequest(String name, String lastName, LocalDate birthDate,
                             String email, String phone, UserRol rol, Long version) {
        this(name, lastName, birthDate, email, phone, rol, version, null);
    }

    @Override
    public String toString() { return "UserUpdateRequest[password=[REDACTED]]"; }
}
