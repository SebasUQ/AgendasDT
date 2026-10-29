package co.edu.uniquindio.AgendasDT.dto;

import co.edu.uniquindio.AgendasDT.domain.enums.MeetingStatus;
import jakarta.validation.constraints.*;

public record MeetingStatusRequest(@NotNull MeetingStatus status,
                                   @NotNull @PositiveOrZero Long version) { }
