package tn.esprit.formation.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * The central entity of the microservice. It sits in the middle of both relations:
 *
 *   Categorie  1 -------- *  Formation  1 -------- *  Chapitre
 *              (ManyToOne)              (OneToMany)
 *
 * In database terms:
 *   formations.categorie_id -> categories.id
 *   chapitres.formation_id  -> formations.id
 * Note that BOTH foreign keys live on the "many" side. That is always true: the FK is
 * on the table that can appear several times.
 */
@Entity
@Getter
@Setter
@Table(name = "formations")
public class Formation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String titre;

    // NOTE: like Chapitre.contenu, this is VARCHAR(255) by default - a long description
    // will be rejected by MySQL. Use @Column(columnDefinition = "TEXT").
    private String description;

    /**
     * Q: Why Double and not double?
     * A: The wrapper accepts null, so a formation may have no price. A primitive would
     *    default to 0.0 and you could not tell "free" from "not set".
     *
     * NOTE (likely question): Double is a FLOATING-POINT type and is inexact - the classic
     * 0.1 + 0.2 = 0.30000000000000004. For money the correct type is BigDecimal, mapped
     * with @Column(precision = 10, scale = 2). Say this before the jury does.
     */
    private Double prix;

    // STRING rather than the default ORDINAL, so inserting a level into the Niveau enum
    // cannot silently reinterpret existing rows.
    @Enumerated(EnumType.STRING)
    private Niveau niveau;

    /**
     * Owning side of the Categorie relation: the FK `categorie_id` is created in the
     * `formations` table. EAGER by default (see the note in Chapitre), which is why
     * FormationService.toResponse() can read f.getCategorie().getNom() without a
     * LazyInitializationException.
     */
    @ManyToOne
    private Categorie categorie;

    /**
     * Q: What does mappedBy = "formation" mean?
     * A: "This side does NOT own the relation; the link is already mapped by the field
     *    named `formation` in Chapitre." It stops Hibernate from creating a second,
     *    redundant join table. Removing mappedBy would produce an extra
     *    `formations_chapitres` table - a very common beginner bug.
     *
     * Q: What does CascadeType.ALL do?
     * A: It propagates every EntityManager operation (PERSIST, MERGE, REMOVE, REFRESH,
     *    DETACH) from Formation to its chapters. In practice:
     *      - saving a Formation saves its new chapters,
     *      - DELETING a Formation DELETES all of its chapters (this is what makes
     *        DELETE /formations/{id} work despite the foreign key).
     *    Use it only for a true composition: a chapter has no meaning without its
     *    formation. It would be wrong on `categorie` - deleting a formation must not
     *    delete the category.
     *
     * Q: What is the difference with orphanRemoval = true?
     * A: Cascade REMOVE fires when the PARENT is deleted. orphanRemoval fires when a
     *    child is REMOVED FROM THE COLLECTION. It is absent here, so
     *    formation.getChapitres().remove(c) detaches the chapter in memory but leaves
     *    the row in the database with its FK intact.
     *
     * Q: What is the default fetch type of @OneToMany?
     * A: LAZY. The chapters are only loaded when the collection is first accessed - which
     *    happens in FormationService.exportPdf(). See the note there about why that
     *    works outside a transaction (open-in-view).
     *
     * Q: Why initialise the list with new ArrayList<>()?
     * A: So the collection is never null. formation.getChapitres().add(...) on a brand new
     *    object works immediately instead of throwing a NullPointerException.
     */
    @OneToMany(mappedBy = "formation", cascade = CascadeType.ALL)
    private List<Chapitre> chapitres = new ArrayList<>();
}
