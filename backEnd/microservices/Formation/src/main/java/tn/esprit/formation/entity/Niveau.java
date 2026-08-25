package tn.esprit.formation.entity;

/**
 * Difficulty level of a Formation.
 *
 * Stored as a STRING in the database (see @Enumerated in Formation), so the column holds
 * "DEBUTANT" and not 0. Reordering these constants therefore stays safe.
 *
 * Q: How does the client send a Niveau?
 * A: As a plain JSON string: {"niveau": "DEBUTANT"}. Jackson converts it to the enum by
 *    NAME, case-sensitively. An unknown value produces a 400 - which is exactly the
 *    validation you want, for free.
 */
public enum Niveau {
    DEBUTANT,
    INTERMEDIAIRE,
    AVANCE
}
