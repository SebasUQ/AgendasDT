package co.edu.uniquindio.AgendasDT.repository;
import co.edu.uniquindio.AgendasDT.domain.ClinicalAddendum;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ClinicalAddendumRepository extends JpaRepository<ClinicalAddendum, UUID> {
    Page<ClinicalAddendum> findByRecord_IdOrderByRecordedAtAscIdAsc(UUID recordId, Pageable pageable);
}
