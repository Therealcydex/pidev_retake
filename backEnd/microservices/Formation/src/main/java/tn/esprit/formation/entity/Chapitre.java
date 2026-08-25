package tn.esprit.formation.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A chapter belonging to one Formation.
 *
 * This is the OWNING SIDE of the Formation <-> Chapitre relation: the class that holds
 * the @ManyToOne is the one whose table carries the FOREIGN KEY. Concretely, Hibernate
 * creates a column `formation_id` in the `chapitres` table. Formation only holds the
 * mirror image of that link (see its `mappedBy`).
 */
@Entity
@Getter
@Setter
@Table(name = "chapitres")
public class Chapitre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String titre;

    // NOTE (likely question): `contenu` maps to VARCHAR(255) by default, so a chapter
    // body longer than 255 characters is REJECTED by MySQL at insert time.
    // The fix is @Column(columnDefinition = "TEXT") or @Lob.
    private String contenu;

    /**
     * Q: What is the DEFAULT fetch type of @ManyToOne?
     * A: EAGER. Loading a Chapitre automatically loads its Formation with it.
     *    (@OneToMany and @ManyToMany default to LAZY - the opposite.)
     *    This asymmetry is a favourite exam question.
     *
     * Q: Is EAGER a good idea here?
     * A: It is convenient - ChapitreService.toResponse() calls c.getFormation().getId()
     *    and it always works. But it causes the N+1 SELECT problem: listAll() runs
     *    1 query for the chapters + 1 query per chapter to fetch its formation.
     *    The usual fix is @ManyToOne(fetch = FetchType.LAZY) plus an explicit
     *    "JOIN FETCH" query when the parent is really needed.
     *
     * Q: Why is there no @JoinColumn?
     * A: It is optional. Without it Hibernate derives the column name from the field:
     *    formation + _ + the primary key = `formation_id`. Adding
     *    @JoinColumn(name = "formation_id", nullable = false) would document it and
     *    enforce that a chapter cannot be orphaned.
     */
    @ManyToOne
    private Formation formation;
}
