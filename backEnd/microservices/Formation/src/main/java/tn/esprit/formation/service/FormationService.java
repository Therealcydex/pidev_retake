package tn.esprit.formation.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.formation.dto.FormationRequest;
import tn.esprit.formation.dto.FormationResponse;
import tn.esprit.formation.dto.FormationStatsResponse;
import tn.esprit.formation.entity.Categorie;
import tn.esprit.formation.entity.Chapitre;
import tn.esprit.formation.entity.Formation;
import tn.esprit.formation.entity.Niveau;
import tn.esprit.formation.repository.CategorieRepository;
import tn.esprit.formation.repository.FormationRepository;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Business layer of the Formation microservice. Beyond the CRUD it carries the two
 * features the jury will ask about: the PDF EXPORT and the STATISTICS.
 *
 * Q: Why does this service inject TWO repositories?
 * A: Because creating a formation requires an existing Categorie. The service is the
 *    right place to enforce that rule - the controller only speaks HTTP and the
 *    repository only speaks persistence.
 *
 * NOTE (likely question): no method here is annotated @Transactional. Each repository
 * call opens and commits its OWN transaction, so a method doing two writes is NOT atomic.
 * It happens to be harmless here (each method performs a single save), but @Transactional
 * on the service methods is the correct habit as soon as an operation writes twice.
 */
@Service
@RequiredArgsConstructor
public class FormationService {

    private final FormationRepository formationRepository;
    private final CategorieRepository categorieRepository;

    /**
     * Q: Why does the client send `categorieId` instead of a whole Categorie object?
     * A: Two reasons. The client must not be able to invent a category (it could send a
     *    name that does not exist, or silently modify an existing one). And the reference
     *    must be VALIDATED: findById(...).orElseThrow(...) guarantees the foreign key
     *    points at a real row, turning a would-be database error into a clean 404.
     */
    public FormationResponse create(FormationRequest request) {
        Categorie categorie = categorieRepository.findById(request.getCategorieId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catégorie not found"));

        Formation formation = new Formation();
        formation.setTitre(request.getTitre());
        formation.setDescription(request.getDescription());
        formation.setPrix(request.getPrix());
        formation.setNiveau(request.getNiveau());
        formation.setCategorie(categorie);

        Formation saved = formationRepository.save(formation);
        return toResponse(saved);
    }

    /**
     * NOTE (likely question - the N+1 SELECT problem):
     * findAll() issues 1 query for the formations, then toResponse() touches
     * f.getCategorie(), and because @ManyToOne is EAGER Hibernate issues one MORE query
     * per formation to load its category. 50 formations = 51 queries.
     * The classic fix is a JOIN FETCH query:
     *     @Query("SELECT f FROM Formation f JOIN FETCH f.categorie")
     * which loads everything in a single SELECT. Enable spring.jpa.show-sql=true in
     * application.properties to SEE the problem - a very convincing demo.
     */
    public List<FormationResponse> listAll() {
        return formationRepository.findAll().stream()
            .map(this::toResponse)
            .toList();
    }

    public FormationResponse getById(Long id) {
        Formation formation = formationRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formation not found"));
        return toResponse(formation);
    }

    /**
     * Q: Why load the entity first instead of building a new Formation with the same id?
     * A: Because `new Formation()` with an id set would have NULL in every field that the
     *    request does not carry - including the `chapitres` collection - and save() would
     *    overwrite the row with those nulls. Loading, mutating, then saving preserves what
     *    the request does not mention.
     */
    public FormationResponse update(Long id, FormationRequest request) {
        Formation formation = formationRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formation not found"));

        Categorie categorie = categorieRepository.findById(request.getCategorieId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catégorie not found"));

        formation.setTitre(request.getTitre());
        formation.setDescription(request.getDescription());
        formation.setPrix(request.getPrix());
        formation.setNiveau(request.getNiveau());
        formation.setCategorie(categorie);

        Formation saved = formationRepository.save(formation);
        return toResponse(saved);
    }

    /**
     * Q: What happens to the chapters of a deleted formation?
     * A: They are deleted too, thanks to cascade = CascadeType.ALL on the @OneToMany in
     *    Formation. Without it MySQL would refuse the delete: the rows in `chapitres`
     *    still reference this formation through their foreign key.
     *
     * NOTE (inconsistency worth admitting): unlike UserService.delete(), there is no
     * existsById() check here. Deleting an id that does not exist answers 204 No Content
     * as if it had worked, instead of 404.
     */
    public void delete(Long id) {
        formationRepository.deleteById(id);
    }

    /**
     * Generates a PDF summary of one formation with the iText 7 library (see pom.xml).
     *
     * Q: Why build the PDF in memory (ByteArrayOutputStream) instead of writing a file?
     * A: Because it is returned directly in the HTTP response. Writing to disk would mean
     *    choosing a directory, cleaning it up, and would break as soon as several
     *    instances of the service run. In memory there is nothing to clean, and the
     *    byte[] goes straight into the ResponseEntity.
     *    The trade-off is RAM: a very large document is held entirely in memory. For big
     *    files you would stream into the HttpServletResponse output stream instead.
     *
     * Q: getChapitres() is LAZY - why is there no LazyInitializationException here?
     * A: Because of OPEN SESSION IN VIEW, which Spring Boot enables BY DEFAULT
     *    (spring.jpa.open-in-view=true). The Hibernate session stays open for the whole
     *    HTTP request, not just for the repository call, so the collection can still be
     *    loaded at this point. It is convenient but criticised (it hides N+1 queries and
     *    holds a DB connection for the entire request); disabling it with
     *    spring.jpa.open-in-view=false would make THIS LINE throw, and the fix would be
     *    @Transactional on the method or a JOIN FETCH query.
     *    This is an excellent question to be ready for - it looks like magic otherwise.
     *
     * NOTE (likely question): a formation with no category or no niveau makes
     * getCategorie().getNom() / getNiveau().name() throw a NullPointerException -> 500.
     *
     * NOTE: document.close() is not in a finally block / try-with-resources, so an
     * exception in the middle leaks the writer. Document implements Closeable, so
     * `try (Document document = new Document(pdf)) { ... }` would be the correct form.
     */
    public byte[] exportPdf(Long id) {
        Formation formation = formationRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formation not found"));

        // The iText pipeline: an output sink -> a writer -> the PDF document ->
        // a layout Document, which is the high-level API used to add elements.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(out);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        document.add(new Paragraph(formation.getTitre()).setBold().setFontSize(20));
        document.add(new Paragraph("Catégorie: " + formation.getCategorie().getNom()));
        document.add(new Paragraph("Niveau: " + formation.getNiveau().name()));
        document.add(new Paragraph("Prix: " + formation.getPrix() + " DT"));
        document.add(new Paragraph("Description: " + formation.getDescription()));

        document.add(new Paragraph("Chapitres:").setBold().setFontSize(14));
        for (Chapitre chapitre : formation.getChapitres()) { // triggers the LAZY load
            document.add(new Paragraph("- " + chapitre.getTitre()));
        }

        // Mandatory: close() flushes the buffers and writes the PDF trailer.
        // Without it the byte[] is truncated and the file is unreadable.
        document.close();
        return out.toByteArray();
    }

    /**
     * Aggregated figures for the statistics dashboard (GET /formations/stats).
     *
     * Q: Why compute this with SQL aggregates instead of loading everything and using
     *    Java streams?
     * A: Because the DATABASE is built for it. findAll() would transfer every row over the
     *    network and consume memory proportional to the table; COUNT/AVG/GROUP BY return
     *    a handful of rows and can use indexes. The rule: aggregate where the data lives.
     *
     * Q: Why LinkedHashMap and not HashMap?
     * A: LinkedHashMap PRESERVES INSERTION ORDER, so the JSON keeps the order returned by
     *    the GROUP BY. With a HashMap the order would be arbitrary and the chart columns
     *    could move between two calls for no reason.
     *
     * The casts row[0] / row[1] are the manual price of List<Object[]> - see the note in
     * FormationRepository.countByCategorie() for the type-safe alternatives.
     */
    public FormationStatsResponse getStats() {
        Map<String, Long> byCategorie = new LinkedHashMap<>();
        for (Object[] row : formationRepository.countByCategorie()) {
            byCategorie.put((String) row[0], (Long) row[1]); // row[0] = nom, row[1] = count
        }

        Map<String, Long> byNiveau = new LinkedHashMap<>();
        for (Object[] row : formationRepository.countByNiveau()) {
            // row[0] is the Niveau ENUM here (not a String): JPQL returns the mapped Java
            // type, so it has to be cast to Niveau and converted with name().
            byNiveau.put(((Niveau) row[0]).name(), (Long) row[1]);
        }

        return new FormationStatsResponse(
            formationRepository.count(), // inherited from JpaRepository: SELECT COUNT(*)
            formationRepository.averagePrix(),
            formationRepository.minPrix(),
            formationRepository.maxPrix(),
            byCategorie,
            byNiveau
        );
    }

    /**
     * Entity -> DTO mapping, written once and reused by every method above.
     *
     * Note that it FLATTENS the relation: instead of nesting a whole Categorie object it
     * exposes categorieId + categorieNom. The client gets the label it needs to display
     * without the API leaking the internal structure of the Categorie entity, and the
     * JSON has no risk of infinite recursion.
     */
    private FormationResponse toResponse(Formation f) {
        return new FormationResponse(
            f.getId(),
            f.getTitre(),
            f.getDescription(),
            f.getPrix(),
            f.getNiveau(),
            f.getCategorie().getId(),
            f.getCategorie().getNom()
        );
    }
}
