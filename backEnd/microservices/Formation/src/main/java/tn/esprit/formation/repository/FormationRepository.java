package tn.esprit.formation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tn.esprit.formation.entity.Formation;

import java.util.List;

/**
 * The only repository here with custom queries - the statistics feeding GET /formations/stats.
 *
 * Q: Why @Query instead of a derived method name like findAll...?
 * A: Query derivation can only express simple criteria (findBy..., countBy...). It cannot
 *    express aggregation - AVG, MIN, MAX, GROUP BY. As soon as you need those you write
 *    the query yourself.
 *
 * Q: Is this SQL?
 * A: No, it is JPQL (Java Persistence Query Language). It targets the ENTITY MODEL, not
 *    the tables: "FROM Formation f" uses the CLASS name (capital F), and f.prix is the
 *    FIELD name - not the `formations` table and its columns. Hibernate translates JPQL
 *    into the SQL dialect of the database, so the same query runs on MySQL or PostgreSQL.
 *    For real SQL you would write @Query(value = "...", nativeQuery = true) and lose
 *    that portability.
 *
 * Q: Why do these return Double and not double?
 * A: On an EMPTY table AVG/MIN/MAX return NULL. A primitive double would throw a
 *    NullPointerException while unboxing. The wrapper lets the null travel up to the JSON
 *    as "averagePrix": null.
 */
public interface FormationRepository extends JpaRepository<Formation, Long> {

    @Query("SELECT AVG(f.prix) FROM Formation f")
    Double averagePrix();

    @Query("SELECT MIN(f.prix) FROM Formation f")
    Double minPrix();

    @Query("SELECT MAX(f.prix) FROM Formation f")
    Double maxPrix();

    /**
     * Q: Why List<Object[]> and not a proper type?
     * A: The query selects TWO columns (the category name and the count), so each row is
     *    an array: row[0] = String nom, row[1] = Long count. FormationService.getStats()
     *    casts and rebuilds a Map from it.
     *
     *    That cast is unchecked and breaks silently if the SELECT order changes. The clean
     *    alternatives are:
     *      - a JPQL constructor expression: SELECT new tn.esprit...StatRow(f.categorie.nom, COUNT(f))
     *      - or a Spring Data projection interface with getNom() / getCount().
     *
     * Q: Why "f.categorie.nom" - is that a join?
     * A: Yes, an implicit one. JPQL follows the association and Hibernate generates the
     *    INNER JOIN on categories automatically. The WHERE ... IS NOT NULL guard excludes
     *    formations with no category, which would otherwise disappear anyway through the
     *    inner join.
     */
    @Query("SELECT f.categorie.nom, COUNT(f) FROM Formation f WHERE f.categorie IS NOT NULL GROUP BY f.categorie.nom")
    List<Object[]> countByCategorie();

    /** Same shape, grouped on the enum: row[0] = Niveau, row[1] = Long count. */
    @Query("SELECT f.niveau, COUNT(f) FROM Formation f WHERE f.niveau IS NOT NULL GROUP BY f.niveau")
    List<Object[]> countByNiveau();
}
