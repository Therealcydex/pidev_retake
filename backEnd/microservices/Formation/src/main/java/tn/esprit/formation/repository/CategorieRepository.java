package tn.esprit.formation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.formation.entity.Categorie;

/**
 * Empty on purpose: extending JpaRepository<Categorie, Long> is already enough for the
 * whole CRUD (save, findAll, findById, deleteById, count...). Spring Data generates the
 * implementation at startup, so there is nothing to write.
 *
 * Q: Why no @Repository annotation here, when the User microservice has one?
 * A: It is optional for Spring Data interfaces - they are detected by the JPA repository
 *    scanner, not by component scanning. Both styles work; only the consistency differs.
 */
public interface CategorieRepository extends JpaRepository<Categorie, Long> {
}
