package co.edu.uniquindio.AgendasDT.dto;

import co.edu.uniquindio.AgendasDT.domain.Bill;
import co.edu.uniquindio.AgendasDT.domain.BillDetail;
import co.edu.uniquindio.AgendasDT.domain.enums.BillStatus;
import co.edu.uniquindio.AgendasDT.domain.enums.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BillResponse(UUID id, UUID meetingId, Long patientId, Instant issuedAt, List<Line> lines,
                           BigDecimal total, BillStatus status, PaymentMethod paymentMethod, Instant paidAt,
                           Long version) {
    public record Line(Integer lineNumber, String description, Integer quantity, BigDecimal unitPrice,
                       BigDecimal subtotal) {
        static Line from(BillDetail detail) {
            return new Line(detail.getLineNumber(), detail.getDescription(), detail.getQuantity(),
                    detail.getUnitPrice(), detail.getSubtotal());
        }
    }

    public static BillResponse from(Bill bill) {
        return new BillResponse(bill.getId(), bill.getMeeting().getId(), bill.getMeeting().getPatient().getId(),
                bill.getIssuedAt(), bill.getDetails().stream().map(Line::from).toList(), bill.getTotal(),
                bill.getStatus(), bill.getPaymentMethod(), bill.getPaidAt(), bill.getVersion());
    }
}
