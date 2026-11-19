package co.edu.uniquindio.AgendasDT.repository;
import co.edu.uniquindio.AgendasDT.domain.PlannedSupply;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PlannedSupplyRepository extends JpaRepository<PlannedSupply, UUID> {
    List<PlannedSupply> findByBillIdOrderByCodeAsc(UUID billId);
}
