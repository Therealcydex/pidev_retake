package tn.esprit.formation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.formation.entity.Chapitre;

import java.util.List;

public interface ChapitreRepository extends JpaRepository<Chapitre, Long> {

    /** Derived query — Spring Data builds the WHERE clause from the method name. */
    List<Chapitre> findByFormationId(Long formationId);

    long countByFormationId(Long formationId);
}
