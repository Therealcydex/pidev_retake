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
