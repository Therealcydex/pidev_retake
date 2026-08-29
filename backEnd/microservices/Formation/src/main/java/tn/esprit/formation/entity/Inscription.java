package tn.esprit.formation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A trainee enrolled in a formation.
 *
 * `userId` is a plain Long, not a relation: the user lives in another microservice and
 * another database, so there is no foreign key to point at. The name behind the id is
 * resolved at read time through the USER service over OpenFeign.
 */
@Entity
@Getter
@Setter
@Table(
    name = "inscriptions",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_inscription_formation_user",
        columnNames = {"formation_id", "user_id"})
)
public class Inscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "formation_id")
    private Formation formation;

    // Explicit name so the @UniqueConstraint above can resolve the column.
    @Column(name = "user_id")
    private Long userId;

    private Instant dateInscription;
}
