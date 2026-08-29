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
import tn.esprit.formation.dto.ChapitreRequest;
import tn.esprit.formation.dto.ChapitreResponse;
import tn.esprit.formation.service.ChapitreService;

import java.util.List;

@RestController
@RequestMapping("/chapitres")
@RequiredArgsConstructor
public class ChapitreController {
    private final ChapitreService chapitreService;

    @PostMapping
    public ResponseEntity<ChapitreResponse> create(@RequestBody ChapitreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chapitreService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<ChapitreResponse>> listAll() {
        return ResponseEntity.ok(chapitreService.listAll());
    }

    /** Declared before /{id} so "formation" is never read as a chapter id. */
    @GetMapping("/formation/{formationId}")
    public ResponseEntity<List<ChapitreResponse>> listByFormation(@PathVariable Long formationId) {
        return ResponseEntity.ok(chapitreService.listByFormation(formationId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChapitreResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(chapitreService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ChapitreResponse> update(@PathVariable Long id, @RequestBody ChapitreRequest request) {
        return ResponseEntity.ok(chapitreService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        chapitreService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
