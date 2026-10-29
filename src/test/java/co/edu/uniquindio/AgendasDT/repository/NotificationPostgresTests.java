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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in: use a dedicated PostgreSQL test database, never a production database. */
@EnabledIfEnvironmentVariable(named = "MEETING_TEST_DB_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationPostgresTests {
    private final String schema = "notification_test_" + UUID.randomUUID().toString().replace("-", "");
    private final Instant now = Instant.parse("2030-01-09T15:00:00Z");
    private final Instant start = Instant.parse("2030-01-10T14:00:00Z");
    private final UUID first = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private final UUID second = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private SessionFactory factory;

    @BeforeAll
    void initialize() throws Exception {
        try (Connection connection = connect(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
            sql.execute("SET search_path TO " + schema + ", public");
            for (String migration : List.of("001_users.sql", "002_meetings_structure.sql",
                    "003_meetings_constraints.sql", "005_notifications.sql", "007_user_passwords_meeting_access.sql")) {
                sql.execute(Files.readString(Path.of("database", migration)));
            }
        }
        factory = new Configuration().addAnnotatedClass(User.class).addAnnotatedClass(Meeting.class)
                .addAnnotatedClass(DentalChair.class).addAnnotatedClass(Notification.class)
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
        execute("DELETE FROM notification; DELETE FROM meeting; DELETE FROM \"User\"; DELETE FROM dental_chair");
        execute("""
                INSERT INTO "User" (id, name, lastname, birthdate, email, phone, rol) VALUES
                (1,'Ana','Uno','1990-01-01','ana@example.test','3000000001','Paciente'),
                (2,'Luis','Dos','1990-01-01','luis@example.test','3000000002','Paciente'),
                (3,'Carlos','Rojas','1980-01-01','carlos@example.test','3000000003','Odontologo');
                INSERT INTO dental_chair (id, name) VALUES (1,'Sillón 1'),(2,'Sillón 2');
                INSERT INTO meeting (id, patientid, doctorid, chair_id, date, ends_at, reason, status) VALUES
                ('00000000-0000-4000-8000-000000000001', 1, 3, 1, '2030-01-10T14:00:00Z', '2030-01-10T15:00:00Z', 'Control', 'Confirmada'),
                ('00000000-0000-4000-8000-000000000002', 2, 3, 2, '2030-01-10T14:00:00Z', '2030-01-10T15:00:00Z', 'Control', 'Cancelada')
                """);
    }

    @Test
    void savesNotificationsAndListsThemByMeeting() {
        save(first, NotificationType.Confirmacion, NotificationChannel.SMS, NotificationStatus.Pendiente, now);
        save(first, NotificationType.Confirmacion, NotificationChannel.Correo, NotificationStatus.Pendiente, now);
        save(second, NotificationType.Confirmacion, NotificationChannel.Correo, NotificationStatus.Pendiente, now);
        try (EntityManager em = factory.createEntityManager()) {
            List<Notification> found = repository(em).findByMeeting(first);
            assertEquals(List.of(NotificationChannel.Correo, NotificationChannel.SMS),
                    found.stream().map(Notification::getChannel).toList());
            assertEquals(0L, found.getFirst().getVersion());
            assertEquals(2, repository(em).findByMeetingStart(first, start).size());
            assertEquals(0, repository(em).findByMeetingStart(first, start.plusSeconds(1)).size());
        }
    }

    @Test
    void lockDueReturnsOnlyPendingNotificationsWhoseTimeArrived() {
        UUID due = save(first, NotificationType.Confirmacion, NotificationChannel.Correo, NotificationStatus.Pendiente, now);
        save(first, NotificationType.Confirmacion, NotificationChannel.SMS, NotificationStatus.Pendiente, now.plusSeconds(1));
        save(first, NotificationType.Reprogramacion, NotificationChannel.Correo, NotificationStatus.Fallida, now);
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            List<Notification> found = repository(em).lockDue(NotificationStatus.Pendiente, now, PageRequest.of(0, 50));
            assertEquals(List.of(due), found.stream().map(Notification::getId).toList());
            em.getTransaction().rollback();
        }
    }

    @Test
    void secondDispatcherSkipsRowsLockedByTheFirst() {
        save(first, NotificationType.Confirmacion, NotificationChannel.Correo, NotificationStatus.Pendiente, now);
        save(first, NotificationType.Confirmacion, NotificationChannel.SMS, NotificationStatus.Pendiente, now);
        try (EntityManager one = factory.createEntityManager(); EntityManager two = factory.createEntityManager()) {
            one.getTransaction().begin();
            assertEquals(1, repository(one).lockDue(NotificationStatus.Pendiente, now, PageRequest.of(0, 1)).size());
            two.getTransaction().begin();
            List<Notification> rest = repository(two).lockDue(NotificationStatus.Pendiente, now, PageRequest.of(0, 50));
            assertEquals(1, rest.size());
            two.getTransaction().rollback();
            one.getTransaction().rollback();
        }
    }

    @Test
    void discardPendingOnlyChangesPendingNotificationsOfThatMeeting() throws SQLException {
        save(first, NotificationType.Confirmacion, NotificationChannel.Correo, NotificationStatus.Pendiente, now);
        save(first, NotificationType.Confirmacion, NotificationChannel.SMS, NotificationStatus.Fallida, now);
        save(second, NotificationType.Confirmacion, NotificationChannel.Correo, NotificationStatus.Pendiente, now);
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            assertEquals(1, repository(em).discardPending(first, "La cita fue reprogramada.",
                    NotificationStatus.Pendiente, NotificationStatus.Descartada));
            em.getTransaction().commit();
        }
        assertEquals(1, count("SELECT count(*) FROM notification WHERE status = 'Descartada'"
                + " AND last_error = 'La cita fue reprogramada.' AND version = 1 AND meeting_id = '" + first + "'"));
        assertEquals(2, count("SELECT count(*) FROM notification WHERE status <> 'Descartada' AND version = 0"));
    }

    @Test
    void findsOnlyActiveMeetingsInsideReminderWindow() {
        try (EntityManager em = factory.createEntityManager()) {
            NotificationRepository repository = repository(em);
            List<MeetingStatus> active = List.of(MeetingStatus.Pendiente, MeetingStatus.Confirmada);
            List<Meeting> found = repository.findMeetingsStartingBetween(active, now, start);
            assertEquals(List.of(first), found.stream().map(Meeting::getId).toList());
            assertEquals("Ana", found.getFirst().getPatient().getName());
            assertTrue(repository.findMeetingsStartingBetween(active, start, start.plusSeconds(3600)).isEmpty());
            assertTrue(repository.findMeetingsStartingBetween(active, now, start.minusSeconds(1)).isEmpty());
        }
    }

    @Test
    void databaseAllowsOnlyOneActiveReminderPerChannelAndTime() throws SQLException {
        UUID reminder = save(first, NotificationType.Recordatorio, NotificationChannel.Correo, NotificationStatus.Pendiente, now);
        save(first, NotificationType.Recordatorio, NotificationChannel.SMS, NotificationStatus.Pendiente, now);
        RuntimeException duplicate = assertThrows(RuntimeException.class, () ->
                save(first, NotificationType.Recordatorio, NotificationChannel.Correo, NotificationStatus.Pendiente, now));
        assertEquals("23505", sqlState(duplicate));
        execute("UPDATE notification SET status = 'Descartada' WHERE id = '" + reminder + "'");
        assertDoesNotThrow(() ->
                save(first, NotificationType.Recordatorio, NotificationChannel.Correo, NotificationStatus.Pendiente, now));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "'Enviada', NULL, 1", "'Pendiente', now(), 0", "'Fallida', now(), 3",
            "'Pendiente', NULL, 4", "'Pendiente', NULL, -1", "'Desconocido', NULL, 0"})
    void databaseRejectsInconsistentNotifications(String statusSentAttempts) {
        SQLException error = assertThrows(SQLException.class, () -> execute("""
                INSERT INTO notification (id, meeting_id, type, channel, recipient, message, meeting_starts_at,
                    created_at, scheduled_at, status, sent_at, attempts)
                VALUES (gen_random_uuid(), '%s', 'Confirmacion', 'Correo', 'ana@example.test', 'Hola', now(),
                    now(), now(), %s)
                """.formatted(first, statusSentAttempts)));
        assertEquals("23514", error.getSQLState());
    }

    private UUID save(UUID meetingId, NotificationType type, NotificationChannel channel,
                      NotificationStatus status, Instant scheduledAt) {
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            try {
                Notification notification = new Notification();
                notification.setMeeting(em.getReference(Meeting.class, meetingId));
                notification.setType(type);
                notification.setChannel(channel);
                notification.setRecipient("ana@example.test");
                notification.setMessage("Hola Ana");
                notification.setMeetingStartsAt(start);
                notification.setCreatedAt(now);
                notification.setScheduledAt(scheduledAt);
                notification.setStatus(status);
                notification.setAttempts(status == NotificationStatus.Fallida ? 3 : 0);
                repository(em).saveAndFlush(notification);
                em.getTransaction().commit();
                return notification.getId();
            } catch (RuntimeException ex) {
                if (em.getTransaction().isActive()) { em.getTransaction().rollback(); }
                throw ex;
            }
        }
    }

    private NotificationRepository repository(EntityManager em) {
        return new JpaRepositoryFactory(em).getRepository(NotificationRepository.class);
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
