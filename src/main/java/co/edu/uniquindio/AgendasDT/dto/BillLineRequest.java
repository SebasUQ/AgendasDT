package co.edu.uniquindio.AgendasDT.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record BillLineRequest(
        @NotBlank @Size(max = 200) String description,
        @NotNull @Min(1) @Max(999) Integer quantity,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal unitPrice) { }
