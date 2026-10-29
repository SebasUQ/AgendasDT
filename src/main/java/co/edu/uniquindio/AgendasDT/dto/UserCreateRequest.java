package co.edu.uniquindio.AgendasDT.dto;

import co.edu.uniquindio.AgendasDT.domain.enums.UserRol;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonProperty;

public record UserCreateRequest(
        @NotNull @Positive Long id,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String lastName,
        @NotNull @Past LocalDate birthDate,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "\\+?[0-9]{7,15}") String phone,
        @NotNull UserRol rol,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Size(min = 8, max = 72) @Pattern(regexp = "(?s).*\\S.*") String password) {
    public UserCreateRequest(Long id, String name, String lastName, LocalDate birthDate,
                             String email, String phone, UserRol rol) {
        this(id, name, lastName, birthDate, email, phone, rol, null);
    }

    @Override
    public String toString() { return "UserCreateRequest[id=" + id + ", password=[REDACTED]]"; }
}
