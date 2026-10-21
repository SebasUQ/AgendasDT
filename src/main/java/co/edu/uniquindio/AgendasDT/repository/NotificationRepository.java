package co.edu.uniquindio.AgendasDT.repository;

import co.edu.uniquindio.AgendasDT.domain.Meeting;
import co.edu.uniquindio.AgendasDT.domain.Notification;
import co.edu.uniquindio.AgendasDT.domain.enums.MeetingStatus;
import co.edu.uniquindio.AgendasDT.domain.enums.NotificationStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    @Query("select n from Notification n where n.meeting.id = :meetingId order by n.createdAt, n.channel")
    List<Notification> findByMeeting(@Param("meetingId") UUID meetingId);

    @Query("select n from Notification n where n.meeting.id = :meetingId and n.meetingStartsAt = :startsAt")
    List<Notification> findByMeetingStart(@Param("meetingId") UUID meetingId, @Param("startsAt") Instant startsAt);

    // Rows locked by another dispatcher are skipped, so a notification is never sent twice at the same time.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select n from Notification n where n.status = :status and n.scheduledAt <= :now order by n.scheduledAt")
    List<Notification> lockDue(@Param("status") NotificationStatus status, @Param("now") Instant now, Pageable page);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Notification n set n.status = :discarded, n.lastError = :reason, n.version = n.version + 1
            where n.meeting.id = :meetingId and n.status = :pending
            """)
    int discardPending(@Param("meetingId") UUID meetingId, @Param("reason") String reason,
                       @Param("pending") NotificationStatus pending, @Param("discarded") NotificationStatus discarded);

    @Query("""
            select m from Meeting m join fetch m.patient join fetch m.doctor
            where m.status in :statuses and m.startsAt > :from and m.startsAt <= :until order by m.startsAt
            """)
    List<Meeting> findMeetingsStartingBetween(@Param("statuses") Collection<MeetingStatus> statuses,
                                              @Param("from") Instant from, @Param("until") Instant until);
}
