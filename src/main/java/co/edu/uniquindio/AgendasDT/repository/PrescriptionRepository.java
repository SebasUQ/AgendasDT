package co.edu.uniquindio.AgendasDT.repository;
import co.edu.uniquindio.AgendasDT.domain.Prescription;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {
    Page<Prescription> findByRecord_IdOrderByIssuedAtDescIdDesc(UUID recordId, Pageable pageable);
    boolean existsBySupersedesId(UUID id);
    Optional<Prescription> findBySupersedesId(UUID id);
}
