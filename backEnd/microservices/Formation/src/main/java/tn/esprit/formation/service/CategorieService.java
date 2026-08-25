package tn.esprit.formation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.formation.dto.CategorieRequest;
import tn.esprit.formation.dto.CategorieResponse;
import tn.esprit.formation.entity.Categorie;
import tn.esprit.formation.repository.CategorieRepository;

import java.util.List;

/**
 * The simplest of the three services: a category is just a name.
 * It is the reference data that Formation points at.
 */
@Service
@RequiredArgsConstructor
public class CategorieService {

    private final CategorieRepository categorieRepository;

    /**
     * NOTE (likely question): nothing prevents creating twice the same category name.
     * Two rows "Web" would then be counted separately in the formations, but MERGED in the
     * statistics, because FormationRepository.countByCategorie() groups by NAME.
     * The fix is @Column(unique = true) on Categorie.nom plus an existsByNom() check here.
     */
    public CategorieResponse create(CategorieRequest request) {
        Categorie categorie = new Categorie();
        categorie.setNom(request.getNom());
        Categorie saved = categorieRepository.save(categorie);
        return new CategorieResponse(saved.getId(), saved.getNom());
    }

    public List<CategorieResponse> listAll() {
        return categorieRepository.findAll().stream()
            .map(c -> new CategorieResponse(c.getId(), c.getNom()))
            .toList();
    }

    public CategorieResponse getById(Long id) {
        Categorie categorie = categorieRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catégorie not found"));
        return new CategorieResponse(categorie.getId(), categorie.getNom());
    }

    public CategorieResponse update(Long id, CategorieRequest request) {
        Categorie categorie = categorieRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catégorie not found"));
        categorie.setNom(request.getNom());
        Categorie saved = categorieRepository.save(categorie);
        return new CategorieResponse(saved.getId(), saved.getNom());
    }

    /**
     * NOTE (the REAL BUG of this microservice - expect the question):
     * deleting a category still referenced by a formation violates the foreign key
     * `formations.categorie_id`. MySQL rejects the DELETE, Hibernate throws a
     * DataIntegrityViolationException, and the client gets a 500 with a stack trace
     * instead of a meaningful error.
     *
     * The three possible policies, and how you would say them:
     *   1. FORBID (what a jury expects here) - count the dependent formations first and
     *      answer 409 CONFLICT: "this category is used by N formations".
     *   2. DETACH - set categorie = null on those formations, then delete.
     *   3. CASCADE - delete the formations too. Almost certainly wrong: losing courses
     *      because a label was removed is not acceptable.
     *
     * There is also no existsById() check, so deleting an unknown id answers 204.
     */
    public void delete(Long id) {
        categorieRepository.deleteById(id);
    }
}
