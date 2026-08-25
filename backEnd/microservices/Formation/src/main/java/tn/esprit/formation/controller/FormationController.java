package tn.esprit.formation.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.formation.dto.FormationRequest;
import tn.esprit.formation.dto.FormationResponse;
import tn.esprit.formation.dto.FormationStatsResponse;
import tn.esprit.formation.service.FormationService;

import java.util.List;

/**
 * REST API for formations. Reached through the Gateway: /formations/** -> port 8084.
 *
 *   POST   /formations         create              201
 *   GET    /formations         list                200
 *   GET    /formations/stats   statistics          200
 *   GET    /formations/{id}    read one            200 / 404
 *   GET    /formations/{id}/pdf  PDF export        200 / 404
 *   PUT    /formations/{id}    replace             200 / 404
 *   DELETE /formations/{id}    delete + chapters   204
 *
 * Q: Where is the CORS configuration? The Angular app is on :4200, this is on :8084.
 * A: It is centralised on the GATEWAY, not repeated in each service. If you ever call
 *    this service directly from the browser, bypassing the gateway, the request is
 *    blocked by the same-origin policy - a frequent cause of "it works in Postman but
 *    not in Angular".
 */
@RestController
@RequestMapping("/formations")
@RequiredArgsConstructor
public class FormationController {

    private final FormationService formationService;

    @PostMapping
    public ResponseEntity<FormationResponse> create(@RequestBody FormationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(formationService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<FormationResponse>> listAll() {
        return ResponseEntity.ok(formationService.listAll());
    }

    /**
     * Q: /formations/stats and /formations/{id} both match the URL "/formations/stats".
     *    Why is there no conflict, and does the declaration ORDER matter?
     * A: No conflict, and the order in the file does NOT matter. Spring compares the
     *    candidate patterns by SPECIFICITY, and a literal segment always beats a path
     *    variable, so "/stats" wins over "/{id}".
     *    Had it been the opposite, "stats" would have been bound to a Long id, conversion
     *    would have failed and the client would get 400 - which is exactly the bug you see
     *    in frameworks that match in declaration order (Express, for instance).
     *    Keeping the literal routes above the parameterised ones is a good habit anyway:
     *    it makes the intent readable.
     */
    @GetMapping("/stats")
    public ResponseEntity<FormationStatsResponse> getStats() {
        return ResponseEntity.ok(formationService.getStats());
    }

    /**
     * Returns the PDF built by iText.
     *
     * Q: Why ResponseEntity<byte[]> and not a String or a DTO?
     * A: A PDF is BINARY. Serialising it as text would corrupt it - any byte that is not
     *    valid in the response charset gets mangled. byte[] is written to the response
     *    body untouched.
     *
     * Q: What do the two headers do?
     *    - Content-Type: application/pdf   tells the browser how to interpret the bytes.
     *    - Content-Disposition: attachment; filename="..."  tells it to DOWNLOAD the file
     *      under that name rather than display it inline. Replace `attachment` with
     *      `inline` to open it in the browser's PDF viewer instead.
     *
     * Q: How does the Angular side consume this?
     * A: It must ask for a blob - http.get(url, { responseType: 'blob' }) - otherwise
     *    Angular tries to parse the binary as JSON and fails.
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long id) {
        byte[] pdf = formationService.exportPdf(id);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"formation-" + id + ".pdf\"")
            .body(pdf);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FormationResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(formationService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FormationResponse> update(@PathVariable Long id, @RequestBody FormationRequest request) {
        return ResponseEntity.ok(formationService.update(id, request));
    }

    /** Cascades to the chapters - see CascadeType.ALL in the Formation entity. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        formationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
