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

/**
 * CRUD for chapters. Same pattern as FormationService, one level down the relation.
 *
 * Q: Why does a Chapitre service need the FormationRepository?
 * A: A chapter cannot exist without its formation. The client sends a `formationId`, and
 *    this service must resolve it into a real entity before linking it - which
 *    simultaneously VALIDATES that the parent exists (404 if not) and gives Hibernate
 *    the managed object to write in the foreign key.
 */
@Service
@RequiredArgsConstructor
public class ChapitreService {

    private final ChapitreRepository chapitreRepository;
    private final FormationRepository formationRepository;

    /**
     * Q: Saving the chapter alone is enough to attach it to the formation - why?
     * A: Because Chapitre is the OWNING SIDE of the relation (it holds the @ManyToOne, so
     *    the FK column `formation_id` lives in its table). Hibernate only looks at the
     *    owning side when it writes. Adding the chapter to formation.getChapitres()
     *    without setting chapitre.setFormation(...) would change NOTHING in the database -
     *    the classic bidirectional-relation trap.
     */
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

    /**
     * NOTE: returns EVERY chapter of every formation, which is rarely what a UI wants.
     * See the suggested findByFormationId(...) in ChapitreRepository.
     * Same N+1 remark as in FormationService.listAll(): @ManyToOne is EAGER, so
     * toResponse() triggers one extra query per chapter to load its formation.
     */
    public List<ChapitreResponse> listAll() {
        return chapitreRepository.findAll().stream()
            .map(this::toResponse)
            .toList();
    }

    public ChapitreResponse getById(Long id) {
        Chapitre chapitre = chapitreRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chapitre not found"));
        return toResponse(chapitre);
    }

    /**
     * Note that an update can MOVE a chapter to another formation, since the request
     * carries the formationId and it is re-resolved here. Both 404s are possible:
     * unknown chapter, or unknown target formation.
     */
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

    /**
     * Deleting a chapter is safe in both directions: nothing references it, and its
     * formation is untouched - the FK lives on this side.
     *
     * NOTE: no existsById() check, so an unknown id answers 204 instead of 404.
     */
    public void delete(Long id) {
        chapitreRepository.deleteById(id);
    }

    /**
     * Entity -> DTO. It exposes only formationId, never the Formation object itself:
     * returning the entity would drag its own `chapitres` list into the JSON, and Jackson
     * would loop Formation -> Chapitre -> Formation -> ... until a StackOverflowError.
     * Flattening to an id is what breaks the cycle.
     *
     * NOTE: c.getFormation() is assumed non-null. A chapter whose formation_id is NULL in
     * the database (possible - there is no nullable=false) would produce a 500 here.
     */
    private ChapitreResponse toResponse(Chapitre c) {
        return new ChapitreResponse(
            c.getId(),
            c.getTitre(),
            c.getContenu(),
            c.getFormation().getId()
        );
    }
}
