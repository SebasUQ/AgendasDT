package co.edu.uniquindio.AgendasDT.repository;
import co.edu.uniquindio.AgendasDT.domain.ClinicalProcedure;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ClinicalProcedureRepository extends JpaRepository<ClinicalProcedure, UUID> {
    @Query("select p from ClinicalProcedure p where p.active = true and p.validFrom <= :now and (p.validUntil is null or p.validUntil > :now)")
    Page<ClinicalProcedure> findCurrent(@Param("now") Instant now, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from ClinicalProcedure p where p.id = :id")
    Optional<ClinicalProcedure> lockForBilling(@Param("id") UUID id);
}
