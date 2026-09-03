package tn.esprit.formation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tn.esprit.formation.entity.FormationImage;

import java.util.Optional;

@Repository
public interface FormationImageRepository extends JpaRepository<FormationImage, Long> {
    Optional<FormationImage> findByFormationId(Long formationId);

    boolean existsByFormationId(Long formationId);

    void deleteByFormationId(Long formationId);

    /** The filename alone, so listing formations never loads the image bytes. */
    @Query("select i.filename from FormationImage i where i.formation.id = :formationId")
    Optional<String> findFilenameByFormationId(Long formationId);
}
