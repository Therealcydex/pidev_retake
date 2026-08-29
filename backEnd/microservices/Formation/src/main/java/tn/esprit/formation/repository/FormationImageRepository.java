package tn.esprit.formation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tn.esprit.formation.entity.FormationImage;

import java.util.List;
import java.util.Optional;

@Repository
public interface FormationImageRepository extends JpaRepository<FormationImage, Long> {
    Optional<FormationImage> findByFormationId(Long formationId);

    boolean existsByFormationId(Long formationId);

    void deleteByFormationId(Long formationId);

    /** (filename, updatedAt) for one formation, without touching the bytes. */
    @Query("select i.filename, i.updatedAt from FormationImage i where i.formation.id = :formationId")
    List<Object[]> findMetaByFormationId(Long formationId);

    /**
     * All (formationId, filename, updatedAt) triples in one query, so listAll() does not
     * issue a lookup per row.
     */
    @Query("select i.formation.id, i.filename, i.updatedAt from FormationImage i")
    List<Object[]> findAllImageMeta();
}
