package tn.esprit.formation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.formation.entity.Chapitre;

/**
 * Plain CRUD, inherited from JpaRepository.
 *
 * NOTE (a natural improvement to mention): the API can only list ALL chapters. Fetching
 * the chapters of one formation is the obvious need, and it is one line of query
 * derivation away:
 *     List<Chapitre> findByFormationId(Long formationId);
 * Spring Data would generate SELECT * FROM chapitres WHERE formation_id = ?
 */
public interface ChapitreRepository extends JpaRepository<Chapitre, Long> {
}
