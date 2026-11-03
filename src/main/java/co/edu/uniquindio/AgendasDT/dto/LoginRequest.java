package co.edu.uniquindio.AgendasDT.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(max = 72) String password) {
    @Override
    public String toString() { return "LoginRequest[email=" + email + ", password=[REDACTED]]"; }
}
