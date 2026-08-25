package tn.esprit.formation.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A course category ("Web", "Mobile", "Data"...).
 *
 * Q: The relation Categorie <-> Formation is declared only in Formation. Why?
 * A: It is a UNIDIRECTIONAL @ManyToOne: you can navigate Formation -> Categorie but not
 *    the other way. That is a deliberate simplification - the application never needs
 *    "give me the formations of this category" from the Categorie object; it queries
 *    them instead (see FormationRepository.countByCategorie()).
 *    Adding @OneToMany(mappedBy = "categorie") here would make it bidirectional.
 *
 * NOTE (likely question - REAL BUG): because nothing here knows about the formations
 * that point to it, DELETE /categories/{id} on a category still used by a formation
 * violates the foreign key. MySQL rejects it and the API answers 500 instead of a clean
 * 409 CONFLICT. Try it before the defence and be ready to explain the fix:
 * check for dependent formations in CategorieService.delete() first.
 *
 * NOTE: `nom` has no unique constraint, so two categories can share the same name -
 * and countByCategorie() groups by name, so they would be merged in the statistics.
 */
@Entity
@Getter
@Setter
@Table(name = "categories")
public class Categorie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nom;
}
