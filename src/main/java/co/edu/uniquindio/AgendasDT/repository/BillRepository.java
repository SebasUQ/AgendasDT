package co.edu.uniquindio.AgendasDT.repository;

import co.edu.uniquindio.AgendasDT.domain.Bill;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillRepository extends JpaRepository<Bill, UUID> {
    boolean existsByMeetingId(UUID meetingId);

    Optional<Bill> findByMeetingId(UUID meetingId);
}
