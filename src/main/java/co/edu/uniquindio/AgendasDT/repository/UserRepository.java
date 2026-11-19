package co.edu.uniquindio.AgendasDT.repository;

import co.edu.uniquindio.AgendasDT.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface UserRepository extends JpaRepository<User, Long> {
    @Query("""
            select u from User u where u.rol = co.edu.uniquindio.AgendasDT.domain.enums.UserRol.Paciente
            and (lower(concat(u.name,' ',u.lastName)) like :term or cast(u.id as string) like :term)
            and exists (select m.id from Meeting m where m.patient.id = u.id and m.doctor.id = :doctorId
                and m.status <> co.edu.uniquindio.AgendasDT.domain.enums.MeetingStatus.Cancelada)
            """)
    org.springframework.data.domain.Page<User> findAssignedPatients(@Param("doctorId") Long doctorId,
            @Param("term") String term, org.springframework.data.domain.Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForMeetingCreation(@Param("id") Long id);
    @Query("select u from User u where lower(trim(u.email)) = :email")
    Optional<User> findByNormalizedEmail(@Param("email") String email);
    @Query("select count(u) > 0 from User u where lower(trim(u.email)) = :email")
    boolean emailExists(@Param("email") String email);

    @Query("select count(u) > 0 from User u where lower(trim(u.email)) = :email and u.id <> :id")
    boolean emailExistsForAnotherUser(@Param("email") String email, @Param("id") Long id);


}
