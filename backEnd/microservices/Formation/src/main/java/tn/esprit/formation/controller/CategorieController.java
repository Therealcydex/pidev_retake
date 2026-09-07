package tn.esprit.formation.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.formation.dto.CategorieDtos;
import tn.esprit.formation.entity.Categorie;
import tn.esprit.formation.repository.CategorieRepository;
import tn.esprit.formation.service.FormationAccessService;

import java.util.List;

/**
 * Categories are a flat lookup table: a name and an id, with no rule of their own beyond
 * who may change them. There was a service layer in between, but it only forwarded to the
 * repository, so the controller talks to it directly.
 */
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategorieController {
    private final CategorieRepository categorieRepository;
    private final FormationAccessService access;

    @PostMapping
    public ResponseEntity<CategorieDtos.Response> create(@RequestBody CategorieDtos.Request request) {
        access.requireAdmin();

        Categorie categorie = new Categorie();
        categorie.setNom(request.getNom());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(categorieRepository.save(categorie)));
    }

    /** Public on purpose: the formation form needs it to fill its dropdown. */
    @GetMapping
    public ResponseEntity<List<CategorieDtos.Response>> listAll() {
        return ResponseEntity.ok(categorieRepository.findAll().stream().map(this::toResponse).toList());
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategorieDtos.Response> update(@PathVariable Long id,
                                                         @RequestBody CategorieDtos.Request request) {
        access.requireAdmin();

        Categorie categorie = categorieRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catégorie not found"));
        categorie.setNom(request.getNom());
        return ResponseEntity.ok(toResponse(categorieRepository.save(categorie)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        access.requireAdmin();
        categorieRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private CategorieDtos.Response toResponse(Categorie c) {
        return new CategorieDtos.Response(c.getId(), c.getNom());
    }
}
