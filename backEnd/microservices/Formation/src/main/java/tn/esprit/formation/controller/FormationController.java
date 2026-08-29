package tn.esprit.formation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.formation.client.UserDto;
import tn.esprit.formation.dto.FormationRequest;
import tn.esprit.formation.dto.FormationResponse;
import tn.esprit.formation.dto.FormationStatsResponse;
import tn.esprit.formation.entity.FormationImage;
import tn.esprit.formation.service.CurrentUserService;
import tn.esprit.formation.service.FormationImageService;
import tn.esprit.formation.service.FormationService;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/formations")
@RequiredArgsConstructor
public class FormationController {
    private final FormationService formationService;
    private final CurrentUserService currentUserService;
    private final FormationImageService imageService;

    @PostMapping
    public ResponseEntity<FormationResponse> create(@Valid @RequestBody FormationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(formationService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<FormationResponse>> listAll() {
        return ResponseEntity.ok(formationService.listAll());
    }

    @GetMapping("/stats")
    public ResponseEntity<FormationStatsResponse> getStats() {
        return ResponseEntity.ok(formationService.getStats());
    }

    @GetMapping("/whoami")
    public ResponseEntity<UserDto> whoami(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(currentUserService.currentUser());
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long id) {
        byte[] pdf = formationService.exportPdf(id);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"formation-" + id + ".pdf\"")
            .body(pdf);
    }

    /** Attach or replace the formation illustration. Admins and trainers only. */
    @PostMapping("/{id}/image")
    public ResponseEntity<FormationResponse> uploadImage(
        @PathVariable Long id,
        @RequestParam("file") MultipartFile file) {
        imageService.requireUploaderRole();
        imageService.store(id, file);
        return ResponseEntity.ok(formationService.getById(id));
    }

    /**
     * The image itself. Served inline with an ETag so a second visit revalidates into a
     * 304 instead of re-downloading it for every row of the list.
     */
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getImage(@PathVariable Long id) {
        FormationImage image = imageService.get(id);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(image.getContentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + image.getFilename() + "\"")
            .eTag("\"" + image.getId() + "-" + image.getSizeBytes() + "\"")
            .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
            .body(image.getData());
    }

    @DeleteMapping("/{id}/image")
    public ResponseEntity<Void> deleteImage(@PathVariable Long id) {
        imageService.requireUploaderRole();
        imageService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<FormationResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(formationService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FormationResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody FormationRequest request) {
        return ResponseEntity.ok(formationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        formationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
