package co.edu.uniquindio.AgendasDT.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "doctor_profile")
@Getter @Setter
public class DoctorProfile {
    @Id private Long id;
    @Column(name = "professional_registration", nullable = false, length = 100)
    private String professionalRegistration;
    @Column(length = 150) private String specialty;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;
}
