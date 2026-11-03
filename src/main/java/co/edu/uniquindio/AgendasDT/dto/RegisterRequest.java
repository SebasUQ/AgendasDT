package co.edu.uniquindio.AgendasDT.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record RegisterRequest(
        @NotNull @Positive Long documento,
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Size(max = 100) String apellido,
        @NotNull @Past LocalDate fechaNacimiento,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "\\+?[0-9]{7,15}") String telefono,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotNull @Size(min = 8, max = 72) @Pattern(regexp = "(?s).*\\S.*") String password) {
    @Override
    public String toString() { return "RegisterRequest[documento=" + documento + ", password=[REDACTED]]"; }
}
