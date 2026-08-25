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

/**
 * REST API for chapters. Reached through the Gateway: /chapitres/** -> port 8084.
 *
 *   POST   /chapitres        create (needs formationId in the body)  201 / 404
 *   GET    /chapitres        list ALL chapters                       200
 *   GET    /chapitres/{id}   read one                                200 / 404
 *   PUT    /chapitres/{id}   replace, can move it to another formation
 *   DELETE /chapitres/{id}   delete                                  204
 *
 * Q: Why a separate controller instead of nesting the chapters under their formation,
 *    e.g. GET /formations/{id}/chapitres?
 * A: Both designs are valid. A flat resource is simpler to write; the nested form
 *    expresses the composition better and is what most REST guidelines recommend for a
 *    child that has no meaning on its own. Worth mentioning as an improvement - the
 *    listing here returns every chapter of the whole database, which no screen needs.
 *
 * Detailed annotation explanations live in FormationController.
 */
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
