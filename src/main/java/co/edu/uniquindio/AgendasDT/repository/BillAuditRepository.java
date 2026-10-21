package co.edu.uniquindio.AgendasDT.repository;

import co.edu.uniquindio.AgendasDT.domain.BillAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface BillAuditRepository extends JpaRepository<BillAudit, UUID> { }
