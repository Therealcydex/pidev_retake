package tn.esprit.formation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tn.esprit.formation.entity.Inscription;

import java.util.List;
import java.util.Optional;

@Repository
public interface InscriptionRepository extends JpaRepository<Inscription, Long> {

    Optional<Inscription> findByFormationIdAndUserId(Long formationId, Long userId);

    /** Removes every enrolment for a formation that is being deleted. */
    void deleteByFormationId(Long formationId);

    boolean existsByFormationIdAndUserId(Long formationId, Long userId);

    /** Everyone enrolled in one formation — the admin and trainer view. */
    List<Inscription> findByFormationIdOrderByDateInscriptionAsc(Long formationId);

    /** Everything one trainee is enrolled in — "Mes formations". */
    List<Inscription> findByUserId(Long userId);

    /**
     * (userId, count) for everyone with at least one enrolment, in one query — so the
     * admin users table shows a count per row without a request per user.
     */
    @Query("select i.userId, count(i) from Inscription i group by i.userId")
    List<Object[]> countGroupedByUser();
}
