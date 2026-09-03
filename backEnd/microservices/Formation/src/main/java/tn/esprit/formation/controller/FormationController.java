package tn.esprit.formation.controller;

import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.formation.client.UserDto;
import tn.esprit.formation.dto.FormationRequest;
import tn.esprit.formation.dto.FormationResponse;
import tn.esprit.formation.dto.FormationStatsResponse;
import tn.esprit.formation.dto.InscriptionResponse;
import tn.esprit.formation.entity.FormationImage;
import tn.esprit.formation.service.CurrentUserService;
import tn.esprit.formation.service.FormationAccessService;
import tn.esprit.formation.service.FormationImageService;
import tn.esprit.formation.service.FormationService;
import tn.esprit.formation.service.InscriptionService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/formations")
@RequiredArgsConstructor
public class FormationController {
    private final FormationService formationService;
    private final CurrentUserService currentUserService;
    private final FormationImageService imageService;
    private final FormationAccessService access;
    private final InscriptionService inscriptionService;

    @PostMapping
    public ResponseEntity<FormationResponse> create(@Valid @RequestBody FormationRequest request) {
        access.requireStaff();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(formationService.create(request, access.currentUserId()));
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
        access.requireCanEdit(id);
        imageService.store(id, file);
        return ResponseEntity.ok(formationService.getById(id));
    }

    /** The image itself, served inline so an <img> tag can point straight at it. */
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getImage(@PathVariable Long id) {
        FormationImage image = imageService.get(id);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(image.getContentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + image.getFilename() + "\"")
            .body(image.getData());
    }

    @DeleteMapping("/{id}/image")
    public ResponseEntity<Void> deleteImage(@PathVariable Long id) {
        access.requireCanEdit(id);
        imageService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /* ---------- inscriptions ---------- */

    /** How many formations each user follows, keyed by user id. Admin only. */
    @GetMapping("/inscriptions/compteurs")
    public ResponseEntity<Map<Long, Long>> inscriptionCounts() {
        return ResponseEntity.ok(inscriptionService.countsByUser());
    }

    /** Which formations one user is enrolled in. Admin only. */
    @GetMapping("/inscriptions/utilisateur/{userId}")
    public ResponseEntity<List<FormationResponse>> formationsOfUser(@PathVariable Long userId) {
        return ResponseEntity.ok(
            inscriptionService.formationsOfUser(userId).stream()
                .map(formationService::toResponse)
                .toList());
    }

    /** Formation ids the caller is enrolled in. Declared before /{id}. */
    @GetMapping("/mes-inscriptions")
    public ResponseEntity<List<Long>> myInscriptions() {
        return ResponseEntity.ok(inscriptionService.myFormationIds());
    }

    @PostMapping("/{id}/inscription")
    public ResponseEntity<Void> enroll(@PathVariable Long id) {
        inscriptionService.enroll(id);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{id}/inscription")
    public ResponseEntity<Void> unenroll(@PathVariable Long id) {
        inscriptionService.unenroll(id);
        return ResponseEntity.noContent().build();
    }

    /** Enrolled trainees. Admins see any formation, trainers only their own. */
    @GetMapping("/{id}/inscriptions")
    public ResponseEntity<List<InscriptionResponse>> inscriptions(@PathVariable Long id) {
        access.requireCanEdit(id);
        return ResponseEntity.ok(inscriptionService.listByFormation(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FormationResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(formationService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FormationResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody FormationRequest request) {
        access.requireCanEdit(id);
        return ResponseEntity.ok(formationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        access.requireCanEdit(id);
        formationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
