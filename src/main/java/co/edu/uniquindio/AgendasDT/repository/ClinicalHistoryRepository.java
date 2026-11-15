package co.edu.uniquindio.AgendasDT.repository;

import co.edu.uniquindio.AgendasDT.domain.ClinicalHistory;
import co.edu.uniquindio.AgendasDT.domain.Treatment;
import co.edu.uniquindio.AgendasDT.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ClinicalHistoryRepository extends JpaRepository<ClinicalHistory, UUID> {

    @Query("select h from ClinicalHistory h where h.PatientID = :patient")
    List<ClinicalHistory> findByPatient(@Param("patient") User patient);

    @Query("select count(h) > 0 from ClinicalHistory h where h.TreatmentID = :treatment")
    boolean existsByTreatment(@Param("treatment") Treatment treatment);
}
