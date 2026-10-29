package co.edu.uniquindio.AgendasDT.repository;

import co.edu.uniquindio.AgendasDT.domain.*;
import co.edu.uniquindio.AgendasDT.domain.enums.MeetingStatus;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in: use a dedicated PostgreSQL test database, never a production database. */
@EnabledIfEnvironmentVariable(named = "MEETING_TEST_DB_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MeetingPostgresTests {
    private final String schema = "meeting_test_" + UUID.randomUUID().toString().replace("-", "");
    private final Instant start = Instant.parse("2030-01-10T14:00:00Z");
    private final Instant end = start.plusSeconds(3600);
    private SessionFactory factory;
    private UUID existingId;

    @BeforeAll
    void initialize() throws Exception {
        try (Connection connection = connect(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
            sql.execute("SET search_path TO " + schema + ", public");
            for (String migration : List.of("001_users.sql", "002_meetings_structure.sql", "003_meetings_constraints.sql",
                    "007_user_passwords_meeting_access.sql")) {
                sql.execute(Files.readString(Path.of("database", migration)));
            }
        }
        factory = new Configuration().addAnnotatedClass(User.class).addAnnotatedClass(Meeting.class)
                .addAnnotatedClass(DentalChair.class).addAnnotatedClass(MeetingAudit.class)
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
        try (Connection connection = connect(); Statement sql = connection.createStatement()) {
            sql.execute("SET search_path TO " + schema + ", public");
            sql.execute("DELETE FROM meeting_audit; DELETE FROM meeting; DELETE FROM \"User\"; DELETE FROM dental_chair");
            sql.execute("""
                    INSERT INTO "User" (id, name, lastname, birthdate, email, phone, rol) VALUES
                    (1,'Paciente','Uno','1990-01-01','patient1@example.test','3000000001','Paciente'),
                    (2,'Paciente','Dos','1990-01-01','patient2@example.test','3000000002','Paciente'),
                    (3,'Doctor','Uno','1980-01-01','doctor1@example.test','3000000003','Odontologo'),
                    (4,'Doctor','Dos','1980-01-01','doctor2@example.test','3000000004','Odontologo');
                    INSERT INTO dental_chair (id, name) VALUES (1,'Sillón 1'),(2,'Sillón 2')
                    """);
        }
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            Meeting meeting = new Meeting();
            meeting.setPatient(em.getReference(User.class, 1L));
            meeting.setDoctor(em.getReference(User.class, 3L));
            meeting.setChair(em.getReference(DentalChair.class, 1L));
            meeting.setStartsAt(start);
            meeting.setEndsAt(end);
            meeting.setReason("Control");
            meeting.setStatus(MeetingStatus.Pendiente);
            new JpaRepositoryFactory(em).getRepository(MeetingRepository.class).saveAndFlush(meeting);
            existingId = meeting.getId();
            assertNotNull(existingId);
            assertEquals(0L, meeting.getVersion());
            em.getTransaction().commit();
        }
    }

    @ParameterizedTest
    @CsvSource({"2,3,2", "1,4,2", "2,4,1"})
    void repositoryDetectsDoctorPatientAndChairOverlaps(long patient, long doctor, long chair) {
        assertTrue(overlaps(new UUID(0, 0), patient, doctor, chair, start.plusSeconds(600), end.plusSeconds(600)));
    }

    @Test
    void repositoryAllowsOtherResourcesAndAdjacentAppointments() {
        assertFalse(overlaps(new UUID(0, 0), 2, 4, 2, start, end));
        assertFalse(overlaps(new UUID(0, 0), 1, 3, 1, end, end.plusSeconds(3600)));
        assertFalse(overlaps(new UUID(0, 0), 1, 3, 1, start.minusSeconds(3600), start));
        assertFalse(overlaps(existingId, 1, 3, 1, start, end));
    }

    @Test
    void cancelledAppointmentDoesNotReserveResources() throws SQLException {
        try (Connection connection = connect(); Statement sql = connection.createStatement()) {
            sql.execute("SET search_path TO " + schema + ", public");
            sql.execute("UPDATE meeting SET status = 'Cancelada'");
        }
        assertFalse(overlaps(new UUID(0, 0), 1, 3, 1, start, end));
    }

    @ParameterizedTest
    @CsvSource({"2,3,2", "1,4,2", "2,4,1"})
    void databaseRejectsOverlappingWritesEvenWithoutService(long patient, long doctor, long chair) {
        SQLException conflict = assertThrows(SQLException.class, () -> insert(patient, doctor, chair, start, end));
        assertEquals("23P01", conflict.getSQLState());
    }

    @Test
    void databaseAllowsAdjacentBookings() throws SQLException {
        insert(1, 3, 1, end, end.plusSeconds(3600));
    }

    @Test
    void simultaneousReservationsHaveExactlyOneWinner() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<String> reserve = () -> {
            ready.countDown();
            assertTrue(go.await(10, TimeUnit.SECONDS));
            try {
                insert(2, 4, 2, end, end.plusSeconds(3600));
                return "OK";
            } catch (SQLException ex) {
                return ex.getSQLState();
            }
        };
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<String> first = workers.submit(reserve);
            Future<String> second = workers.submit(reserve);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            assertEquals(Set.of("OK", "23P01"), Set.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)));
        }
    }

    private boolean overlaps(UUID excluded, long patient, long doctor, long chair, Instant from, Instant to) {
        try (EntityManager em = factory.createEntityManager()) {
            return new JpaRepositoryFactory(em).getRepository(MeetingRepository.class).overlaps(excluded,
                    List.of(MeetingStatus.Pendiente, MeetingStatus.Confirmada), from, to, patient, doctor, chair);
        }
    }

    private void insert(long patient, long doctor, long chair, Instant from, Instant to) throws SQLException {
        try (Connection connection = connect(); Statement session = connection.createStatement()) {
            session.execute("SET search_path TO " + schema + ", public");
            session.execute("SET statement_timeout = '10s'");
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO meeting (id, patientid, doctorid, chair_id, date, ends_at, reason, status)
                    VALUES (?, ?, ?, ?, ?, ?, 'Control', 'Pendiente')
                    """)) {
                insert.setObject(1, UUID.randomUUID());
                insert.setLong(2, patient);
                insert.setLong(3, doctor);
                insert.setLong(4, chair);
                insert.setTimestamp(5, Timestamp.from(from));
                insert.setTimestamp(6, Timestamp.from(to));
                insert.executeUpdate();
            }
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
