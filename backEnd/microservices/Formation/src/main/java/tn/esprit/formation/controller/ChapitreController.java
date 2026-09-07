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
import tn.esprit.formation.dto.ChapitreDtos;
import tn.esprit.formation.entity.Chapitre;
import tn.esprit.formation.entity.Formation;
import tn.esprit.formation.repository.ChapitreRepository;
import tn.esprit.formation.repository.FormationRepository;
import tn.esprit.formation.service.FormationAccessService;

import java.util.List;

/**
 * A chapter has no rule of its own: it inherits the one on the formation that owns it, so
 * every mutation here goes through FormationAccessService.requireCanEdit. The service
 * layer that used to sit in between only forwarded to the repositories.
 */
@RestController
@RequestMapping("/chapitres")
@RequiredArgsConstructor
public class ChapitreController {
    private final ChapitreRepository chapitreRepository;
    private final FormationRepository formationRepository;
    private final FormationAccessService access;

    @PostMapping
    public ResponseEntity<ChapitreDtos.Response> create(@RequestBody ChapitreDtos.Request request) {
        // An admin may add a chapter anywhere, a trainer only to a formation they created.
        access.requireCanEdit(request.getFormationId());

        Chapitre chapitre = new Chapitre();
        chapitre.setTitre(request.getTitre());
        chapitre.setContenu(request.getContenu());
        chapitre.setFormation(requireFormation(request.getFormationId()));

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(chapitreRepository.save(chapitre)));
    }

    @GetMapping("/formation/{formationId}")
    public ResponseEntity<List<ChapitreDtos.Response>> listByFormation(@PathVariable Long formationId) {
        return ResponseEntity.ok(
            chapitreRepository.findByFormationId(formationId).stream().map(this::toResponse).toList());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ChapitreDtos.Response> update(@PathVariable Long id,
                                                        @RequestBody ChapitreDtos.Request request) {
        Chapitre chapitre = requireChapitre(id);

        // Both sides matter, because this call can move a chapter between formations: the
        // caller must be allowed to take it out of the one it is in *and* to put it into
        // the one the request names. Checking only the target would let a trainer pull
        // someone else's chapter into their own formation.
        access.requireCanEdit(chapitre.getFormation().getId());
        access.requireCanEdit(request.getFormationId());

        chapitre.setTitre(request.getTitre());
        chapitre.setContenu(request.getContenu());
        chapitre.setFormation(requireFormation(request.getFormationId()));

        return ResponseEntity.ok(toResponse(chapitreRepository.save(chapitre)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        // Loaded rather than deleteById, both to find out which formation owns it and so a
        // missing id is a 404 instead of the silent 204 deleteById would give.
        Chapitre chapitre = requireChapitre(id);

        access.requireCanEdit(chapitre.getFormation().getId());
        chapitreRepository.delete(chapitre);
        return ResponseEntity.noContent().build();
    }

    private Chapitre requireChapitre(Long id) {
        return chapitreRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chapitre not found"));
    }

    private Formation requireFormation(Long id) {
        return formationRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formation not found"));
    }

    private ChapitreDtos.Response toResponse(Chapitre c) {
        return new ChapitreDtos.Response(c.getId(), c.getTitre(), c.getContenu(), c.getFormation().getId());
    }
}
