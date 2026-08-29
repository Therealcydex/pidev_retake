package tn.esprit.formation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tn.esprit.formation.entity.Chapitre;

import java.util.List;

public interface ChapitreRepository extends JpaRepository<Chapitre, Long> {

    /** Derived query — Spring Data builds the WHERE clause from the method name. */
    List<Chapitre> findByFormationId(Long formationId);

    long countByFormationId(Long formationId);

    /**
     * All (formationId, count) pairs in one query, so the catalogue does not issue a
     * count per card.
     */
    @Query("select c.formation.id, count(c) from Chapitre c group by c.formation.id")
    List<Object[]> countGroupedByFormation();
}
