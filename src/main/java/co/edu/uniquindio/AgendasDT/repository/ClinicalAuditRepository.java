package co.edu.uniquindio.AgendasDT.repository;
import co.edu.uniquindio.AgendasDT.domain.ClinicalAudit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ClinicalAuditRepository extends JpaRepository<ClinicalAudit, UUID> { }
