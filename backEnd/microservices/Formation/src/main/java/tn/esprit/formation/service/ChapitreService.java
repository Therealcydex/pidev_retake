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

    public ChapitreResponse create(ChapitreRequest request) {
        Formation formation = formationRepository.findById(request.getFormationId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formation not found"));

        Chapitre chapitre = new Chapitre();
        chapitre.setTitre(request.getTitre());
        chapitre.setContenu(request.getContenu());
        chapitre.setFormation(formation);

        Chapitre saved = chapitreRepository.save(chapitre);
        return toResponse(saved);
    }

    public List<ChapitreResponse> listAll() {
        return chapitreRepository.findAll().stream()
            .map(this::toResponse)
            .toList();
    }

    /** Filters in SQL, so the detail page does not download every chapter to show one formation's. */
    public List<ChapitreResponse> listByFormation(Long formationId) {
        return chapitreRepository.findByFormationId(formationId).stream()
            .map(this::toResponse)
            .toList();
    }

    public ChapitreResponse getById(Long id) {
        Chapitre chapitre = chapitreRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chapitre not found"));
        return toResponse(chapitre);
    }

    public ChapitreResponse update(Long id, ChapitreRequest request) {
        Chapitre chapitre = chapitreRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chapitre not found"));

        Formation formation = formationRepository.findById(request.getFormationId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formation not found"));

        chapitre.setTitre(request.getTitre());
        chapitre.setContenu(request.getContenu());
        chapitre.setFormation(formation);

        Chapitre saved = chapitreRepository.save(chapitre);
        return toResponse(saved);
    }

    public void delete(Long id) {
        chapitreRepository.deleteById(id);
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
