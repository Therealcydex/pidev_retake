package tn.esprit.formation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.formation.entity.Formation;
import tn.esprit.formation.entity.FormationImage;
import tn.esprit.formation.repository.FormationImageRepository;
import tn.esprit.formation.repository.FormationRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FormationImageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".webp");


    private final FormationRepository formationRepository;
    private final FormationImageRepository imageRepository;

    @Transactional
    public FormationImage store(Long formationId, MultipartFile file) {
        Formation formation = formationRepository.findById(formationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formation not found"));

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file uploaded");
        }

        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (ALLOWED_EXTENSIONS.stream().noneMatch(name.toLowerCase()::endsWith)) {
            throw new ResponseStatusException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PNG, JPG and WebP images are supported");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The uploaded file is not an image");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the uploaded file");
        }

        // The extension and the declared content type both come from the client; the
        // magic bytes are the only part of the request the client cannot lie about
        // without actually sending an image.
        if (!looksLikeImage(bytes)) {
            throw new ResponseStatusException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The file content is not a PNG, JPG or WebP image");
        }

        // Replace any existing image rather than accumulating rows.
        FormationImage image = imageRepository.findByFormationId(formationId)
            .orElseGet(FormationImage::new);

        image.setFormation(formation);
        image.setFilename(name);
        image.setContentType(contentType);
        image.setSizeBytes((long) bytes.length);
        image.setUpdatedAt(Instant.now());
        image.setData(bytes);

        return imageRepository.save(image);
    }

    @Transactional(readOnly = true)
    public FormationImage get(Long formationId) {
        return imageRepository.findByFormationId(formationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No image for this formation"));
    }

    @Transactional
    public void delete(Long formationId) {
        if (!imageRepository.existsByFormationId(formationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No image for this formation");
        }
        imageRepository.deleteByFormationId(formationId);
    }

    /** PNG: 89 50 4E 47 — JPEG: FF D8 FF — WebP: "RIFF" then "WEBP" at offset 8. */
    private boolean looksLikeImage(byte[] b) {
        if (b.length >= 4
            && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return true;
        }
        if (b.length >= 3
            && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return true;
        }
        return b.length >= 12
            && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
            && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }
}
