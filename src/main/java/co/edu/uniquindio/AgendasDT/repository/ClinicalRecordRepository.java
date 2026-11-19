package co.edu.uniquindio.AgendasDT.repository;
import co.edu.uniquindio.AgendasDT.domain.ClinicalRecord;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ClinicalRecordRepository extends JpaRepository<ClinicalRecord, UUID> {
    boolean existsByMeeting_Id(UUID meetingId);
    Page<ClinicalRecord> findByPatient_IdOrderByRecordedAtDescIdDesc(Long patientId, Pageable pageable);
    List<ClinicalRecord> findByPatient_IdOrderByRecordedAtAscIdAsc(Long patientId);
}
