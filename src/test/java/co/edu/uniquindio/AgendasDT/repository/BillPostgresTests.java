package co.edu.uniquindio.AgendasDT.repository;

import co.edu.uniquindio.AgendasDT.domain.*;
import co.edu.uniquindio.AgendasDT.domain.enums.*;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in: use a dedicated PostgreSQL test database, never a production database. */
@EnabledIfEnvironmentVariable(named = "MEETING_TEST_DB_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BillPostgresTests {
    private final String schema = "bill_test_" + UUID.randomUUID().toString().replace("-", "");
    private final Instant now = Instant.parse("2030-01-10T15:00:00Z");
    private final UUID meetingId = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private SessionFactory factory;

    @BeforeAll
    void initialize() throws Exception {
        try (Connection connection = connect(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
            sql.execute("SET search_path TO " + schema + ", public");
            for (String migration : List.of("001_users.sql", "002_meetings_structure.sql",
                    "003_meetings_constraints.sql", "004_bills.sql", "007_user_passwords_meeting_access.sql")) {
                sql.execute(Files.readString(Path.of("database", migration)));
            }
        }
        factory = new Configuration().addAnnotatedClass(User.class).addAnnotatedClass(Meeting.class)
                .addAnnotatedClass(DentalChair.class).addAnnotatedClass(Bill.class)
                .addAnnotatedClass(BillDetail.class).addAnnotatedClass(BillAudit.class)
                .setProperty("hibernate.connection.url", required("MEETING_TEST_DB_URL"))
                .setProperty("hibernate.connection.username", required("MEETING_TEST_DB_USER"))
                .setProperty("hibernate.connection.password", required("MEETING_TEST_DB_PASSWORD"))
                .setProperty("hibernate.default_schema", schema)
                .setProperty("hibernate.hbm2ddl.auto", "validate")
                .buildSessionFactory();
    }

    @AfterAll
    void cleanup() throws SQLException {
        if (factory != null) { factory.close(); }
        try (Connection connection = connect(); Statement sql = connection.createStatement()) {
            // The schema is a generated test-only identifier; no existing tables are touched.
            sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @BeforeEach
    void seed() throws SQLException {
        execute("DELETE FROM bill_audit; DELETE FROM billdetail; DELETE FROM bill; DELETE FROM meeting;"
                + " DELETE FROM \"User\"; DELETE FROM dental_chair");
        execute("""
                INSERT INTO "User" (id, name, lastname, birthdate, email, phone, rol) VALUES
                (1,'Paciente','Uno','1990-01-01','patient1@example.test','3000000001','Paciente'),
                (3,'Doctor','Uno','1980-01-01','doctor1@example.test','3000000003','Odontologo');
                INSERT INTO dental_chair (id, name) VALUES (1,'Sillón 1');
                INSERT INTO meeting (id, patientid, doctorid, chair_id, date, ends_at, reason, status)
                VALUES ('00000000-0000-4000-8000-000000000001', 1, 3, 1,
                        '2030-01-10T14:00:00Z', '2030-01-10T15:00:00Z', 'Control', 'Completada')
                """);
    }

    @Test
    void savesBillWithLinesAndFindsItByMeeting() {
        UUID id = saveBill(List.of(line(1, "Limpieza", 1, "80000.00"), line(2, "Resina", 2, "45000.50")));
        try (EntityManager em = factory.createEntityManager()) {
            BillRepository bills = new JpaRepositoryFactory(em).getRepository(BillRepository.class);
            assertTrue(bills.existsByMeetingId(meetingId));
            Bill bill = bills.findByMeetingId(meetingId).orElseThrow();
            assertEquals(id, bill.getId());
            assertEquals(0L, bill.getVersion());
            assertEquals(new BigDecimal("170001.00"), bill.getTotal());
            assertEquals(List.of("Limpieza", "Resina"), bill.getDetails().stream().map(BillDetail::getDescription).toList());
            assertEquals(now, bill.getIssuedAt());
        }
    }

    @Test
    void replacingLinesDeletesOldOnesAndIncreasesVersion() throws SQLException {
        UUID id = saveBill(List.of(line(1, "Limpieza", 1, "80000.00"), line(2, "Resina", 2, "45000.50")));
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            Bill bill = em.find(Bill.class, id);
            bill.getDetails().clear();
            BillDetail detail = line(1, "Valoración", 1, "30000.00");
            detail.setBill(bill);
            bill.getDetails().add(detail);
            bill.setTotal(new BigDecimal("30000.00"));
            bill.setUpdatedAt(now.plusSeconds(60));
            em.getTransaction().commit();
            assertEquals(1L, bill.getVersion());
        }
        assertEquals(1, count("SELECT count(*) FROM billdetail"));
        try (EntityManager em = factory.createEntityManager()) {
            assertEquals("Valoración", em.find(Bill.class, id).getDetails().getFirst().getDescription());
        }
    }

    @Test
    void databaseRejectsSecondBillForSameMeeting() {
        saveBill(List.of(line(1, "Limpieza", 1, "80000.00")));
        RuntimeException conflict = assertThrows(RuntimeException.class,
                () -> saveBill(List.of(line(1, "Limpieza", 1, "80000.00"))));
        assertEquals("23505", sqlState(conflict));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Paid without payment method.
            "INSERT INTO bill (id, meetingid, date, total, status, paid_at, updated_at) VALUES (gen_random_uuid(), '%s', now(), 0, 'Pagada', now(), now())",
            // Pending but with a payment method.
            "INSERT INTO bill (id, meetingid, date, total, status, payment_method, updated_at) VALUES (gen_random_uuid(), '%s', now(), 0, 'Pendiente', 'Efectivo', now())",
            // Pending but with a payment time.
            "INSERT INTO bill (id, meetingid, date, total, status, paid_at, updated_at) VALUES (gen_random_uuid(), '%s', now(), 0, 'Pendiente', now(), now())",
            // Unknown status.
            "INSERT INTO bill (id, meetingid, date, total, status, updated_at) VALUES (gen_random_uuid(), '%s', now(), 0, 'Anulada', now())",
            // Negative total.
            "INSERT INTO bill (id, meetingid, date, total, status, updated_at) VALUES (gen_random_uuid(), '%s', now(), -1, 'Pendiente', now())"})
    void databaseRejectsInconsistentBills(String insert) {
        SQLException error = assertThrows(SQLException.class, () -> execute(insert.formatted(meetingId)));
        assertEquals("23514", error.getSQLState());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "'Limpieza', 2, 1000.00, 1000.00",   // subtotal does not match quantity * price
            "'Limpieza', 0, 1000.00, 0.00",      // quantity below 1
            "'Limpieza', 1, -1.00, -1.00",       // negative price
            "'  ', 1, 1000.00, 1000.00"})        // blank description
    void databaseRejectsInconsistentLines(String values) {
        UUID id = saveBill(List.of(line(1, "Limpieza", 1, "80000.00")));
        SQLException error = assertThrows(SQLException.class, () -> execute(
                "INSERT INTO billdetail (id, billid, line_number, description, quantity, unit_price, subtotal)"
                        + " VALUES (gen_random_uuid(), '" + id + "', 2, " + values + ")"));
        assertEquals("23514", error.getSQLState());
    }

    private UUID saveBill(List<BillDetail> lines) {
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            try {
                Bill bill = new Bill();
                bill.setMeeting(em.getReference(Meeting.class, meetingId));
                bill.setIssuedAt(now);
                bill.setUpdatedAt(now);
                bill.setStatus(BillStatus.Pendiente);
                lines.forEach(detail -> detail.setBill(bill));
                bill.getDetails().addAll(lines);
                bill.setTotal(lines.stream().map(BillDetail::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add));
                new JpaRepositoryFactory(em).getRepository(BillRepository.class).saveAndFlush(bill);
                em.persist(new BillAudit(bill.getId(), "reception", "CREATE", now));
                em.getTransaction().commit();
                return bill.getId();
            } catch (RuntimeException ex) {
                if (em.getTransaction().isActive()) { em.getTransaction().rollback(); }
                throw ex;
            }
        }
    }

    private BillDetail line(int number, String description, int quantity, String unitPrice) {
        BillDetail detail = new BillDetail();
        detail.setLineNumber(number);
        detail.setDescription(description);
        detail.setQuantity(quantity);
        detail.setUnitPrice(new BigDecimal(unitPrice));
        detail.setSubtotal(detail.getUnitPrice().multiply(BigDecimal.valueOf(quantity)));
        return detail;
    }

    private String sqlState(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) { return sql.getSQLState(); }
        }
        return null;
    }

    private long count(String query) throws SQLException {
        try (Connection connection = connect(); Statement sql = connection.createStatement()) {
            sql.execute("SET search_path TO " + schema + ", public");
            try (ResultSet result = sql.executeQuery(query)) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private void execute(String statement) throws SQLException {
        try (Connection connection = connect(); Statement sql = connection.createStatement()) {
            sql.execute("SET search_path TO " + schema + ", public");
            sql.execute(statement);
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(required("MEETING_TEST_DB_URL"),
                required("MEETING_TEST_DB_USER"), required("MEETING_TEST_DB_PASSWORD"));
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) { throw new IllegalStateException("Falta la variable " + name); }
        return value;
    }
}
