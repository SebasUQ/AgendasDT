package co.edu.uniquindio.AgendasDT.dto;

import co.edu.uniquindio.AgendasDT.domain.enums.PaymentMethod;
import jakarta.validation.constraints.*;

public record BillPaymentRequest(
        @NotNull PaymentMethod paymentMethod,
        @NotNull @PositiveOrZero Long version) { }
