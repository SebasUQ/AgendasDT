package co.edu.uniquindio.AgendasDT.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record BillCreateRequest(
        @NotNull UUID meetingId,
        @NotEmpty @Size(max = 50) List<@Valid @NotNull BillLineRequest> lines) { }
