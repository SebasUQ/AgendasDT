package co.edu.uniquindio.AgendasDT.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record BillUpdateRequest(
        @NotEmpty @Size(max = 50) List<@Valid @NotNull BillLineRequest> lines,
        @NotNull @PositiveOrZero Long version) { }
