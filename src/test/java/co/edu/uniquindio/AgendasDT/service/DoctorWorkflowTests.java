package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.domain.*;
import co.edu.uniquindio.AgendasDT.domain.enums.*;
import co.edu.uniquindio.AgendasDT.domain.ClinicalRecord.*;
import co.edu.uniquindio.AgendasDT.dto.*;
import co.edu.uniquindio.AgendasDT.dto.DoctorDtos.*;
import co.edu.uniquindio.AgendasDT.exception.*;
import co.edu.uniquindio.AgendasDT.repository.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:doctor_module;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test") @Transactional
@WithMockUser(username="doctor@example.test", roles="Odontologo")
class DoctorWorkflowTests {
    @Autowired DoctorService doctorService;
    @Autowired ClinicalService clinical;
    @Autowired DoctorChargeService charges;
    @Autowired BillService billing;
    @Autowired UserRepository users;
    @Autowired MeetingRepository meetings;
    @Autowired DoctorProfileRepository profiles;
    @Autowired DentalChairRepository chairs;
    @Autowired ClinicalRecordRepository records;
    @Autowired ClinicalAddendumRepository addenda;
    @Autowired ClinicalAuditRepository audits;
    @Autowired ClinicalProcedureRepository procedures;
    @Autowired BillRepository bills;
    @Autowired EntityManager entityManager;
    private User doctor, patient, otherDoctor, otherPatient;
    private Meeting meeting;
    private DentalChair chair;

    @BeforeEach void seed() {
        doctor = user(7101L,UserRol.Odontologo,"doctor@example.test","Andrés");
        patient = user(7102L,UserRol.Paciente,"patient@example.test","María");
        otherDoctor = user(7103L,UserRol.Odontologo,"other@example.test","Otro");
        otherPatient = user(7104L,UserRol.Paciente,"otherpatient@example.test","Ajeno");
        DoctorProfile profile = new DoctorProfile(); profile.setId(doctor.getId());
        profile.setProfessionalRegistration("REG-123"); profile.setUpdatedAt(Instant.now()); profiles.saveAndFlush(profile);
        chair = new DentalChair(); chair.setId(7105L); chair.setName("Sillón de pruebas clínicas"); chairs.saveAndFlush(chair);
        meeting = meeting(patient,doctor,MeetingStatus.Confirmada);
        meeting(otherPatient,otherDoctor,MeetingStatus.Confirmada);
    }

    @Test void scopesAgendaAndPatientSearchToAuthenticatedDoctor() {
        var agenda = doctorService.agenda(null,null,0,20);
        assertEquals(1,agenda.totalElements()); assertEquals(meeting.getId(),agenda.content().getFirst().id());
        var assigned = doctorService.patients("",0,20);
        assertEquals(1,assigned.totalElements()); assertEquals(patient.getId(),assigned.content().getFirst().id());
        assertEquals(1,doctorService.patients("María",0,20).totalElements());
        assertEquals(1,doctorService.patients("María Pérez",0,20).totalElements());
        assertEquals(1,doctorService.patients("7102",0,20).totalElements());
        assertThrows(AccessDeniedException.class, () -> clinical.history(otherPatient.getId(),0,20));
    }

    @Test void finalizesEvolutionCompletesMeetingAndConsolidatesFdi() {
        RecordResponse record = clinical.finalizeConsultation(meeting.getId(),request(List.of(
                new ToothRequest(11,Surface.Vestibular,FindingType.Patologia,"Hallazgo registrado"),
                new ToothRequest(55,Surface.Oclusal,FindingType.TratamientoRealizado,"Intervención registrada"))));
        assertEquals(MeetingStatus.Completada,meetings.findById(meeting.getId()).orElseThrow().getStatus());
        assertEquals("REG-123",record.professionalRegistration());
        assertEquals("Alergia documentada",clinical.history(patient.getId(),0,20).records().content().getFirst().allergies());
        var odontogram = clinical.odontogram(patient.getId());
        assertEquals(2,odontogram.size()); assertEquals("Rojo",odontogram.getFirst().color());
        assertEquals("Azul",odontogram.getLast().color());
        assertTrue(audits.findAll().stream().anyMatch(a -> a.getAction().equals("FINALIZE_RECORD")));
        assertThrows(ClinicalException.class, () -> clinical.finalizeConsultation(meeting.getId(),request(List.of())));
    }

    @Test void immutableEvolutionIsCorrectedThroughAddendum() {
        UUID id = clinical.finalizeConsultation(meeting.getId(),request(List.of())).id();
        ClinicalRecord stored = records.findById(id).orElseThrow(); stored.setDiagnosis("Cambio que no debe persistir");
        entityManager.flush(); entityManager.clear();
        assertEquals("Diagnóstico documentado",records.findById(id).orElseThrow().getDiagnosis());
        var correction = clinical.addendum(id,new AddendumRequest("Corrección de transcripción","Aclaración del diagnóstico original"));
        assertEquals(doctor.getId(),correction.doctorId());
        assertEquals(1,clinical.addenda(id,0,20).totalElements());
        assertEquals("Diagnóstico documentado",clinical.getRecord(id).diagnosis());
    }

    @Test void validatesFdiDuplicatesStateAndMeetingVersion() {
        ClinicalException invalid = assertThrows(ClinicalException.class, () -> clinical.finalizeConsultation(meeting.getId(),
                request(List.of(new ToothRequest(99,Surface.Oclusal,FindingType.Patologia,"Hallazgo")))));
        assertEquals(400,invalid.getStatus().value());
        assertThrows(ClinicalException.class, () -> clinical.finalizeConsultation(meeting.getId(),request(List.of(
                new ToothRequest(11,Surface.Oclusal,FindingType.Patologia,"A"),
                new ToothRequest(11,Surface.Oclusal,FindingType.TratamientoRealizado,"B")))));
        RecordRequest r = request(List.of());
        assertThrows(ClinicalException.class, () -> clinical.finalizeConsultation(meeting.getId(),new RecordRequest(99L,
                r.reason(),r.findings(),r.diagnosis(),r.performedProcedure(),r.plan(),null,null,null,List.of())));
        meeting.setStatus(MeetingStatus.Cancelada); meetings.flush();
        assertThrows(ClinicalException.class, () -> clinical.finalizeConsultation(meeting.getId(),request(List.of())));
        assertEquals(0,records.count());
    }

    @Test void unrelatedDoctorCannotConsultOrFinalizeAndOtherAuthorCannotCorrect() {
        UUID id = clinical.finalizeConsultation(meeting.getId(),request(List.of())).id();
        login(otherDoctor.getEmail(),"Odontologo");
        assertThrows(AccessDeniedException.class, () -> clinical.getRecord(id));
        assertThrows(AccessDeniedException.class, () -> clinical.finalizeConsultation(meeting.getId(),request(List.of())));
        meeting(patient,otherDoctor,MeetingStatus.Confirmada);
        assertEquals(id,clinical.getRecord(id).id());
        assertThrows(AccessDeniedException.class, () -> clinical.addendum(id,new AddendumRequest("Motivo","Texto")));
        assertThrows(AccessDeniedException.class, () -> clinical.prescribe(id,prescription(null,null)));
    }

    @Test void issuesImmutablePrescriptionsAndCorrectionsWithSnapshots() {
        UUID record = clinical.finalizeConsultation(meeting.getId(),request(List.of())).id();
        var first = clinical.prescribe(record,prescription(null,null));
        assertEquals("REG-123",first.professionalRegistration());
        doctor.setName("Nombre cambiado"); users.flush();
        DoctorProfile profile = profiles.findById(doctor.getId()).orElseThrow(); profile.setProfessionalRegistration("REG-456"); profiles.flush();
        assertEquals("Andrés Pérez",clinical.prescription(first.id(),true).doctorName());
        assertEquals("REG-123",clinical.prescription(first.id(),false).professionalRegistration());
        assertThrows(ClinicalException.class, () -> clinical.prescribe(record,prescription(first.id(),null)));
        var correction = clinical.prescribe(record,prescription(first.id(),"Aclaración de instrucciones"));
        assertEquals(first.id(),correction.supersedesId());
        assertEquals(correction.id(),clinical.prescription(first.id(),true).supersededById());
        assertEquals(2,clinical.prescriptions(record,0,20).totalElements());
        assertThrows(ClinicalException.class, () -> clinical.prescribe(record,prescription(first.id(),"Otra corrección")));
    }

    @Test void professionalRegistrationIsRequiredForClinicalDocuments() {
        profiles.deleteAll(); profiles.flush();
        assertThrows(ClinicalException.class, () -> clinical.finalizeConsultation(meeting.getId(),request(List.of())));
        var profile = doctorService.updateProfile(new ProfileRequest("REG-NUEVO","Odontología general",null));
        assertEquals("REG-NUEVO",profile.professionalRegistration());
        assertEquals("REG-NUEVO",doctorService.profile().professionalRegistration());
    }

    @Test void orderUsesServerTariffAndSupplyKitAndCanBePaidByReception() {
        clinical.finalizeConsultation(meeting.getId(),request(List.of()));
        ClinicalProcedure procedure = procedure();
        var result = charges.create(meeting.getId(),new ChargeRequest(List.of(new ChargeLine(procedure.getId(),3))));
        assertEquals(new BigDecimal("3001.50"),result.bill().total());
        assertEquals(6L,result.supplies().getFirst().units());
        Bill bill = bills.findById(result.bill().id()).orElseThrow(); assertTrue(bill.isClinicalOrder());
        assertEquals(procedure.getId(),bill.getDetails().getFirst().getProcedureId());
        assertThrows(ClinicalException.class, () -> charges.create(meeting.getId(),new ChargeRequest(List.of(new ChargeLine(procedure.getId(),1)))));
        login("reception@example.test","Recepcionista");
        assertThrows(InvalidBillException.class, () -> billing.update(bill.getId(),new BillUpdateRequest(
                List.of(new BillLineRequest("Precio alterado",1,new BigDecimal("1.00"))),bill.getVersion())));
        assertEquals(BillStatus.Pagada,billing.pay(bill.getId(),new BillPaymentRequest(PaymentMethod.Efectivo,bill.getVersion())).status());
        login(doctor.getEmail(),"Odontologo"); assertEquals(BillStatus.Pagada,charges.get(meeting.getId()).bill().status());
    }

    @Test void inactiveTariffAndUnfinishedConsultationCannotBeCharged() {
        ClinicalProcedure procedure = procedure();
        var request = new ChargeRequest(List.of(new ChargeLine(procedure.getId(),1)));
        assertThrows(ClinicalException.class, () -> charges.create(meeting.getId(),request));
        clinical.finalizeConsultation(meeting.getId(),request(List.of()));
        procedure.setActive(false); procedures.flush();
        assertThrows(ClinicalException.class, () -> charges.create(meeting.getId(),request));
        assertEquals(0,bills.count());
    }

    @Test @WithMockUser(roles="Paciente") void patientCannotBypassControllerForClinicalData() {
        assertThrows(AccessDeniedException.class, () -> clinical.history(patient.getId(),0,20));
        assertThrows(AccessDeniedException.class, () -> doctorService.agenda(null,null,0,20));
    }

    private RecordRequest request(List<ToothRequest> teeth) {
        return new RecordRequest(meeting.getVersion(),"Motivo registrado","Hallazgos documentados","Diagnóstico documentado",
                "Procedimiento registrado","Plan documentado","Antecedentes documentados","Alergia documentada","Condición documentada",teeth);
    }
    private PrescriptionRequest prescription(UUID previous,String reason) {
        return new PrescriptionRequest(List.of(new MedicationRequest("Medicamento indicado por el profesional","Dosis documentada",
                "Frecuencia documentada","Duración documentada","Instrucciones del profesional")),previous,reason);
    }
    private User user(Long id,UserRol role,String email,String name) {
        User u = new User(); u.setId(id); u.setRol(role); u.setEmail(email); u.setName(name); u.setLastName("Pérez");
        u.setBirthDate(LocalDate.of(1990,1,1)); u.setPhone("3001234567"); return users.saveAndFlush(u);
    }
    private Meeting meeting(User patient,User doctor,MeetingStatus status) {
        Meeting m = new Meeting(); m.setPatient(patient); m.setDoctor(doctor); m.setChair(chair); m.setStatus(status);
        m.setReason("Control"); m.setStartsAt(Instant.now().minusSeconds(3600)); m.setEndsAt(Instant.now().plusSeconds(3600));
        return meetings.saveAndFlush(m);
    }
    private ClinicalProcedure procedure() {
        ClinicalProcedure p = new ClinicalProcedure(); p.setCode("PROC-1"); p.setName("Procedimiento de prueba");
        p.setPrice(new BigDecimal("1000.50")); p.setValidFrom(Instant.now().minusSeconds(3600));
        var supply = new ClinicalProcedure.Supply(); supply.setCode("KIT-1"); supply.setName("Insumo de prueba"); supply.setUnit("unidad"); supply.setUnits(2);
        p.getSupplies().add(supply); return procedures.saveAndFlush(p);
    }
    private void login(String name,String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(name,"unused",List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
}
