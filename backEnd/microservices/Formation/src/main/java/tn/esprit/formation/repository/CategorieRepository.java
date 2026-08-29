package tn.esprit.formation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.formation.entity.Categorie;

public interface CategorieRepository extends JpaRepository<Categorie, Long> {
}
