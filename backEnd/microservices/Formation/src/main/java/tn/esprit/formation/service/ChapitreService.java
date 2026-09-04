package tn.esprit.formation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.formation.dto.ChapitreRequest;
import tn.esprit.formation.dto.ChapitreResponse;
import tn.esprit.formation.entity.Chapitre;
import tn.esprit.formation.entity.Formation;
import tn.esprit.formation.repository.ChapitreRepository;
import tn.esprit.formation.repository.FormationRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChapitreService {
    private final ChapitreRepository chapitreRepository;
    private final FormationRepository formationRepository;
    private final FormationAccessService access;

    public ChapitreResponse create(ChapitreRequest request) {
        // A chapter belongs to a formation, so it inherits that formation's rule: an admin
        // may add one anywhere, a trainer only to a formation they created.
        access.requireCanEdit(request.getFormationId());

        Formation formation = formationRepository.findById(request.getFormationId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formation not found"));

        Chapitre chapitre = new Chapitre();
        chapitre.setTitre(request.getTitre());
        chapitre.setContenu(request.getContenu());
        chapitre.setFormation(formation);

        Chapitre saved = chapitreRepository.save(chapitre);
        return toResponse(saved);
    }

    public List<ChapitreResponse> listByFormation(Long formationId) {
        return chapitreRepository.findByFormationId(formationId).stream()
            .map(this::toResponse)
            .toList();
    }

    public ChapitreResponse update(Long id, ChapitreRequest request) {
        Chapitre chapitre = chapitreRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chapitre not found"));

        // Both sides matter, because this call can move a chapter between formations: the
        // caller must be allowed to take it out of the one it is in *and* to put it into
        // the one the request names. Checking only the target would let a trainer pull
        // someone else's chapter into their own formation.
        access.requireCanEdit(chapitre.getFormation().getId());
        access.requireCanEdit(request.getFormationId());

        Formation formation = formationRepository.findById(request.getFormationId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formation not found"));

        chapitre.setTitre(request.getTitre());
        chapitre.setContenu(request.getContenu());
        chapitre.setFormation(formation);

        Chapitre saved = chapitreRepository.save(chapitre);
        return toResponse(saved);
    }

    public void delete(Long id) {
        // Loaded rather than deleteById, both to find out which formation owns it and so a
        // missing id is a 404 instead of the 500 deleteById raises.
        Chapitre chapitre = chapitreRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chapitre not found"));

        access.requireCanEdit(chapitre.getFormation().getId());
        chapitreRepository.delete(chapitre);
    }

    private ChapitreResponse toResponse(Chapitre c) {
        return new ChapitreResponse(
            c.getId(),
            c.getTitre(),
            c.getContenu(),
            c.getFormation().getId()
        );
    }
}
