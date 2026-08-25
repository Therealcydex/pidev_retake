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
import tn.esprit.formation.dto.CategorieRequest;
import tn.esprit.formation.dto.CategorieResponse;
import tn.esprit.formation.service.CategorieService;

import java.util.List;

/**
 * REST API for categories. Reached through the Gateway: /categories/** -> port 8084.
 *
 *   POST   /categories        create    201
 *   GET    /categories        list      200
 *   GET    /categories/{id}   read one  200 / 404
 *   PUT    /categories/{id}   replace   200 / 404
 *   DELETE /categories/{id}   delete    204  <-- 500 if the category is still used,
 *                                             see the note in CategorieService.delete()
 *
 * The structure is identical to FormationController - same annotations, same status
 * codes. The detailed explanations (@RestController, @PathVariable, PUT vs POST,
 * why 201 and 204) are written there, once, rather than repeated in all three
 * controllers.
 *
 * NOTE: no @PreAuthorize and no authentication at all on this service - anyone who can
 * reach port 8084 can create or delete categories. See config/SecurityConfig.
 */
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategorieController {

    private final CategorieService categorieService;

    @PostMapping
    public ResponseEntity<CategorieResponse> create(@RequestBody CategorieRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categorieService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<CategorieResponse>> listAll() {
        return ResponseEntity.ok(categorieService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategorieResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(categorieService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategorieResponse> update(@PathVariable Long id, @RequestBody CategorieRequest request) {
        return ResponseEntity.ok(categorieService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        categorieService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
