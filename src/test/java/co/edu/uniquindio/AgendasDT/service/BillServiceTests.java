package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.domain.*;
import co.edu.uniquindio.AgendasDT.domain.enums.*;
import co.edu.uniquindio.AgendasDT.dto.*;
import co.edu.uniquindio.AgendasDT.exception.*;
import co.edu.uniquindio.AgendasDT.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillServiceTests {
    @Mock BillRepository bills;
    @Mock MeetingRepository meetings;
    @Mock BillAuditRepository audit;
    private final Instant now = Instant.parse("2026-10-17T15:00:00Z");
    private final UUID billId = UUID.fromString("00000000-0000-4000-8000-000000000010");
    private final UUID meetingId = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private BillService service;

    @BeforeEach
    void setup() {
        service = new BillService(bills, meetings, audit, Clock.fixed(now, ZoneOffset.UTC));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("reception", "unused", List.of()));
    }

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    @Test
    void createsPendingBillWithServerCalculatedTotalsAndAudit() {
        when(meetings.findById(meetingId)).thenReturn(Optional.of(meeting(MeetingStatus.Completada)));
        savesWithId();
        BillResponse response = service.create(new BillCreateRequest(meetingId,
                List.of(line(" Limpieza ", 1, "80000"), line("Resina", 2, "45000.50"))));
        assertEquals(billId, response.id());
        assertEquals(meetingId, response.meetingId());
        assertEquals(1L, response.patientId());
        assertEquals(now, response.issuedAt());
        assertEquals(BillStatus.Pendiente, response.status());
        assertNull(response.paymentMethod());
        assertNull(response.paidAt());
        assertEquals(new BigDecimal("170001.00"), response.total());
        assertEquals(2, response.lines().size());
        assertEquals(new BillResponse.Line(1, "Limpieza", 1, new BigDecimal("80000.00"), new BigDecimal("80000.00")),
                response.lines().get(0));
        assertEquals(new BillResponse.Line(2, "Resina", 2, new BigDecimal("45000.50"), new BigDecimal("90001.00")),
                response.lines().get(1));
        ArgumentCaptor<BillAudit> entry = ArgumentCaptor.forClass(BillAudit.class);
        verify(audit).save(entry.capture());
        assertEquals(billId, entry.getValue().getBillId());
        assertEquals("reception", entry.getValue().getActor());
        assertEquals("CREATE", entry.getValue().getAction());
        assertEquals(now, entry.getValue().getOccurredAt());
    }

    @Test
    void billsAnyMeetingThatIsNotCancelled() {
        savesWithId();
        for (MeetingStatus status : List.of(MeetingStatus.Pendiente, MeetingStatus.Confirmada, MeetingStatus.Completada)) {
            when(meetings.findById(meetingId)).thenReturn(Optional.of(meeting(status)));
            assertEquals(BillStatus.Pendiente, service.create(create()).status());
        }
    }

    @Test
    void rejectsCancelledMeeting() {
        when(meetings.findById(meetingId)).thenReturn(Optional.of(meeting(MeetingStatus.Cancelada)));
        assertThrows(InvalidBillException.class, () -> service.create(create()));
        verifyNoInteractions(bills, audit);
    }

    @Test
    void rejectsUnknownMeeting() {
        assertThrows(BillNotFoundException.class, () -> service.create(create()));
        verifyNoInteractions(bills, audit);
    }

    @Test
    void rejectsSecondBillForSameMeeting() {
        when(meetings.findById(meetingId)).thenReturn(Optional.of(meeting(MeetingStatus.Completada)));
        when(bills.existsByMeetingId(meetingId)).thenReturn(true);
        assertThrows(BillConflictException.class, () -> service.create(create()));
        verify(bills, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void databaseConflictDoesNotWriteAudit() {
        when(meetings.findById(meetingId)).thenReturn(Optional.of(meeting(MeetingStatus.Completada)));
        when(bills.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_bill_meeting"));
        assertThrows(DataIntegrityViolationException.class, () -> service.create(create()));
        verifyNoInteractions(audit);
    }

    @Test
    void editReplacesLinesAndRecalculatesTotal() {
        Bill bill = existing(BillStatus.Pendiente);
        when(bills.findById(billId)).thenReturn(Optional.of(bill));
        doAnswer(call -> { bill.setVersion(1L); return null; }).when(bills).flush();
        BillResponse response = service.update(billId, new BillUpdateRequest(
                List.of(line("Valoración", 1, "30000"), line("Radiografía", 3, "15000")), 0L));
        assertEquals(new BigDecimal("75000.00"), response.total());
        assertEquals(List.of("Valoración", "Radiografía"),
                response.lines().stream().map(BillResponse.Line::description).toList());
        assertEquals(List.of(1, 2), response.lines().stream().map(BillResponse.Line::lineNumber).toList());
        assertEquals(1L, response.version());
        assertEquals(now, bill.getUpdatedAt());
        ArgumentCaptor<BillAudit> entry = ArgumentCaptor.forClass(BillAudit.class);
        verify(audit).save(entry.capture());
        assertEquals("UPDATE", entry.getValue().getAction());
    }

    @Test
    void rejectsStaleEditBeforeChangingData() {
        Bill bill = existing(BillStatus.Pendiente);
        when(bills.findById(billId)).thenReturn(Optional.of(bill));
        assertThrows(BillConflictException.class,
                () -> service.update(billId, new BillUpdateRequest(List.of(line("Otro", 1, "1")), 3L)));
        assertEquals(new BigDecimal("50000.00"), bill.getTotal());
        assertEquals("Limpieza", bill.getDetails().getFirst().getDescription());
        verify(bills, never()).flush();
        verifyNoInteractions(audit);
    }

    @Test
    void rejectsEditingPaidBill() {
        Bill bill = existing(BillStatus.Pagada);
        when(bills.findById(billId)).thenReturn(Optional.of(bill));
        assertThrows(BillConflictException.class,
                () -> service.update(billId, new BillUpdateRequest(List.of(line("Otro", 1, "1")), 0L)));
        assertEquals(new BigDecimal("50000.00"), bill.getTotal());
        verify(bills, never()).flush();
        verifyNoInteractions(audit);
    }

    @Test
    void registersPayment() {
        Bill bill = existing(BillStatus.Pendiente);
        when(bills.findById(billId)).thenReturn(Optional.of(bill));
        BillResponse response = service.pay(billId, new BillPaymentRequest(PaymentMethod.Tarjeta, 0L));
        assertEquals(BillStatus.Pagada, response.status());
        assertEquals(PaymentMethod.Tarjeta, response.paymentMethod());
        assertEquals(now, response.paidAt());
        assertEquals(new BigDecimal("50000.00"), response.total());
        verify(bills).flush();
        ArgumentCaptor<BillAudit> entry = ArgumentCaptor.forClass(BillAudit.class);
        verify(audit).save(entry.capture());
        assertEquals("PAY", entry.getValue().getAction());
    }

    @Test
    void rejectsPayingTwice() {
        Bill bill = existing(BillStatus.Pagada);
        when(bills.findById(billId)).thenReturn(Optional.of(bill));
        assertThrows(BillConflictException.class,
                () -> service.pay(billId, new BillPaymentRequest(PaymentMethod.Transferencia, 0L)));
        assertEquals(PaymentMethod.Efectivo, bill.getPaymentMethod());
        verifyNoInteractions(audit);
    }

    @Test
    void rejectsStalePayment() {
        Bill bill = existing(BillStatus.Pendiente);
        when(bills.findById(billId)).thenReturn(Optional.of(bill));
        assertThrows(BillConflictException.class,
                () -> service.pay(billId, new BillPaymentRequest(PaymentMethod.Efectivo, 1L)));
        assertEquals(BillStatus.Pendiente, bill.getStatus());
        verifyNoInteractions(audit);
    }

    @Test
    void readsBillByIdAndByMeeting() {
        Bill bill = existing(BillStatus.Pendiente);
        when(bills.findById(billId)).thenReturn(Optional.of(bill));
        when(bills.findByMeetingId(meetingId)).thenReturn(Optional.of(bill));
        assertEquals(billId, service.get(billId).id());
        assertEquals(billId, service.getByMeeting(meetingId).id());
        verifyNoInteractions(audit);
    }

    @Test
    void rejectsUnknownBill() {
        assertThrows(BillNotFoundException.class, () -> service.get(billId));
        assertThrows(BillNotFoundException.class, () -> service.getByMeeting(meetingId));
        assertThrows(BillNotFoundException.class,
                () -> service.update(billId, new BillUpdateRequest(List.of(line("Otro", 1, "1")), 0L)));
        assertThrows(BillNotFoundException.class,
                () -> service.pay(billId, new BillPaymentRequest(PaymentMethod.Efectivo, 0L)));
        verifyNoInteractions(meetings, audit);
    }

    private void savesWithId() {
        when(bills.saveAndFlush(any())).thenAnswer(call -> {
            Bill bill = call.getArgument(0);
            bill.setId(billId);
            bill.setVersion(0L);
            return bill;
        });
    }

    private BillCreateRequest create() {
        return new BillCreateRequest(meetingId, List.of(line("Limpieza", 1, "50000")));
    }

    private BillLineRequest line(String description, int quantity, String unitPrice) {
        return new BillLineRequest(description, quantity, new BigDecimal(unitPrice));
    }

    private Meeting meeting(MeetingStatus status) {
        User patient = new User();
        patient.setId(1L);
        patient.setRol(UserRol.Paciente);
        Meeting meeting = new Meeting();
        meeting.setId(meetingId);
        meeting.setPatient(patient);
        meeting.setStatus(status);
        return meeting;
    }

    private Bill existing(BillStatus status) {
        Bill bill = new Bill();
        bill.setId(billId);
        bill.setMeeting(meeting(MeetingStatus.Completada));
        bill.setIssuedAt(now.minusSeconds(3600));
        bill.setUpdatedAt(now.minusSeconds(3600));
        BillDetail detail = new BillDetail();
        detail.setBill(bill);
        detail.setLineNumber(1);
        detail.setDescription("Limpieza");
        detail.setQuantity(1);
        detail.setUnitPrice(new BigDecimal("50000.00"));
        detail.setSubtotal(new BigDecimal("50000.00"));
        bill.getDetails().add(detail);
        bill.setTotal(new BigDecimal("50000.00"));
        bill.setStatus(status);
        if (status == BillStatus.Pagada) {
            bill.setPaymentMethod(PaymentMethod.Efectivo);
            bill.setPaidAt(now.minusSeconds(60));
        }
        bill.setVersion(0L);
        return bill;
    }
}
