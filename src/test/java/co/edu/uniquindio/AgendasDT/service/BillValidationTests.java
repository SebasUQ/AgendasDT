package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.config.MethodSecurityConfig;
import co.edu.uniquindio.AgendasDT.config.TimeConfig;
import co.edu.uniquindio.AgendasDT.domain.enums.PaymentMethod;
import co.edu.uniquindio.AgendasDT.dto.*;
import co.edu.uniquindio.AgendasDT.exception.BillNotFoundException;
import co.edu.uniquindio.AgendasDT.repository.*;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

/** The service validates its input itself, so invalid data is rejected before any repository call. */
@SpringJUnitConfig({BillService.class, MethodSecurityConfig.class, TimeConfig.class,
        MethodValidationPostProcessor.class})
@WithMockUser(roles = "Recepcionista")
class BillValidationTests {
    private static final UUID MEETING = UUID.fromString("00000000-0000-4000-8000-000000000001");
    @Autowired BillService service;
    @MockitoBean BillRepository bills;
    @MockitoBean MeetingRepository meetings;
    @MockitoBean BillAuditRepository audit;

    static Stream<BillCreateRequest> invalidCreates() {
        return Stream.of(
                new BillCreateRequest(null, List.of(line("Limpieza", 1, "1"))),
                new BillCreateRequest(MEETING, List.of()),
                new BillCreateRequest(MEETING, null),
                new BillCreateRequest(MEETING, Collections.nCopies(51, line("Limpieza", 1, "1"))),
                new BillCreateRequest(MEETING, Arrays.asList(line("Limpieza", 1, "1"), null)),
                new BillCreateRequest(MEETING, List.of(line(" ", 1, "1"))),
                new BillCreateRequest(MEETING, List.of(line("x".repeat(201), 1, "1"))),
                new BillCreateRequest(MEETING, List.of(line("Limpieza", 0, "1"))),
                new BillCreateRequest(MEETING, List.of(line("Limpieza", 1000, "1"))),
                new BillCreateRequest(MEETING, List.of(new BillLineRequest("Limpieza", null, BigDecimal.ONE))),
                new BillCreateRequest(MEETING, List.of(new BillLineRequest("Limpieza", 1, null))),
                new BillCreateRequest(MEETING, List.of(line("Limpieza", 1, "-1"))),
                new BillCreateRequest(MEETING, List.of(line("Limpieza", 1, "10.505"))),
                new BillCreateRequest(MEETING, List.of(line("Limpieza", 1, "100000000"))));
    }

    @ParameterizedTest
    @MethodSource("invalidCreates")
    void rejectsInvalidCreateBeforeTouchingData(BillCreateRequest request) {
        assertThrows(ConstraintViolationException.class, () -> service.create(request));
        verifyNoInteractions(bills, meetings, audit);
    }

    @Test
    void rejectsInvalidEditAndPayment() {
        UUID id = UUID.randomUUID();
        assertThrows(ConstraintViolationException.class,
                () -> service.update(id, new BillUpdateRequest(List.of(line("Limpieza", 1, "1")), null)));
        assertThrows(ConstraintViolationException.class,
                () -> service.update(id, new BillUpdateRequest(List.of(), 0L)));
        assertThrows(ConstraintViolationException.class,
                () -> service.pay(id, new BillPaymentRequest(null, 0L)));
        assertThrows(ConstraintViolationException.class,
                () -> service.pay(id, new BillPaymentRequest(PaymentMethod.Efectivo, -1L)));
        verifyNoInteractions(bills, meetings, audit);
    }

    @Test
    void acceptsLimitValues() {
        // Free services (price 0), the maximum price and the maximum quantity are valid.
        var request = new BillCreateRequest(MEETING, List.of(line("Control", 1, "0"),
                line("Implante", 999, "99999999.99")));
        // Reaches the repository, which finds no meeting.
        assertThrows(BillNotFoundException.class, () -> service.create(request));
    }

    private static BillLineRequest line(String description, Integer quantity, String unitPrice) {
        return new BillLineRequest(description, quantity, new BigDecimal(unitPrice));
    }
}
