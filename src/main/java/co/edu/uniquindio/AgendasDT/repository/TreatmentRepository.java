package co.edu.uniquindio.AgendasDT.repository;

import co.edu.uniquindio.AgendasDT.domain.Treatment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TreatmentRepository extends JpaRepository<Treatment, UUID> {
}
