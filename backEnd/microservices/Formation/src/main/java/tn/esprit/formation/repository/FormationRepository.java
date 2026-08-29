package tn.esprit.formation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tn.esprit.formation.entity.Formation;

import java.util.List;

public interface FormationRepository extends JpaRepository<Formation, Long> {
    @Query("SELECT f.categorie.nom, COUNT(f) FROM Formation f WHERE f.categorie IS NOT NULL GROUP BY f.categorie.nom")
    List<Object[]> countByCategorie();

    @Query("SELECT f.niveau, COUNT(f) FROM Formation f WHERE f.niveau IS NOT NULL GROUP BY f.niveau")
    List<Object[]> countByNiveau();
}
