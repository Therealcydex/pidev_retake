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

@Service
@RequiredArgsConstructor
public class CategorieService {
    private final CategorieRepository categorieRepository;
    private final FormationAccessService access;

    public CategorieResponse create(CategorieRequest request) {
        access.requireAdmin();

        Categorie categorie = new Categorie();
        categorie.setNom(request.getNom());
        Categorie saved = categorieRepository.save(categorie);
        return new CategorieResponse(saved.getId(), saved.getNom());
    }

    /** Public on purpose: the formation form needs it to fill its dropdown. */
    public List<CategorieResponse> listAll() {
        return categorieRepository.findAll().stream()
            .map(c -> new CategorieResponse(c.getId(), c.getNom()))
            .toList();
    }

    public CategorieResponse update(Long id, CategorieRequest request) {
        access.requireAdmin();

        Categorie categorie = categorieRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catégorie not found"));
        categorie.setNom(request.getNom());
        Categorie saved = categorieRepository.save(categorie);
        return new CategorieResponse(saved.getId(), saved.getNom());
    }

    public void delete(Long id) {
        access.requireAdmin();
        categorieRepository.deleteById(id);
    }
}
