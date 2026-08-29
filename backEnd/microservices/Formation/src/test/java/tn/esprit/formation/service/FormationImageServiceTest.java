package tn.esprit.formation.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.formation.entity.Formation;
import tn.esprit.formation.entity.FormationImage;
import tn.esprit.formation.repository.FormationImageRepository;
import tn.esprit.formation.repository.FormationRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The three validation layers on an upload: extension, declared type, and magic bytes. */
@ExtendWith(MockitoExtension.class)
class FormationImageServiceTest {

    private static final long FORMATION_ID = 1L;

    private static final byte[] PNG_BYTES =
        new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13};
    private static final byte[] JPEG_BYTES =
        new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F'};

    @Mock private FormationRepository formationRepository;
    @Mock private FormationImageRepository imageRepository;
    @InjectMocks private FormationImageService service;

    private void formationExists() {
        lenient().when(formationRepository.findById(FORMATION_ID))
            .thenReturn(Optional.of(new Formation()));
        lenient().when(imageRepository.findByFormationId(FORMATION_ID))
            .thenReturn(Optional.empty());
    }

    private MultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    private HttpStatus statusOfStoring(MultipartFile f) {
        try {
            service.store(FORMATION_ID, f);
            return HttpStatus.OK;
        } catch (ResponseStatusException e) {
            return HttpStatus.valueOf(e.getStatusCode().value());
        }
    }

    @Test
    void acceptsAPng() {
        formationExists();
        when(imageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FormationImage saved = service.store(FORMATION_ID, file("cover.png", "image/png", PNG_BYTES));

        assertThat(saved.getFilename()).isEqualTo("cover.png");
        assertThat(saved.getContentType()).isEqualTo("image/png");
        assertThat(saved.getSizeBytes()).isEqualTo(PNG_BYTES.length);
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void acceptsAJpeg() {
        formationExists();
        when(imageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(statusOfStoring(file("cover.jpg", "image/jpeg", JPEG_BYTES)))
            .isEqualTo(HttpStatus.OK);
    }

    @Test
    void rejectsAnUnsupportedExtension() {
        formationExists();
        assertThat(statusOfStoring(file("deck.pptx", "image/png", PNG_BYTES)))
            .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void rejectsANonImageContentType() {
        formationExists();
        assertThat(statusOfStoring(file("cover.png", "application/pdf", PNG_BYTES)))
            .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    /**
     * The extension and the declared type both come from the client. The magic bytes are
     * the only part a caller cannot fake without sending a real image.
     */
    @Test
    void rejectsAFileThatOnlyClaimsToBeAnImage() {
        formationExists();
        byte[] notAnImage = "MZ this is an executable".getBytes();

        assertThat(statusOfStoring(file("cover.png", "image/png", notAnImage)))
            .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);

        verify(imageRepository, never()).save(any());
    }

    @Test
    void rejectsAnEmptyUpload() {
        formationExists();
        assertThat(statusOfStoring(file("cover.png", "image/png", new byte[0])))
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void missingFormationIsNotFound() {
        when(formationRepository.findById(FORMATION_ID)).thenReturn(Optional.empty());
        assertThat(statusOfStoring(file("cover.png", "image/png", PNG_BYTES)))
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** Re-uploading replaces the existing row instead of accumulating one per upload. */
    @Test
    void replacesAnExistingImage() {
        FormationImage existing = new FormationImage();
        existing.setId(42L);
        when(formationRepository.findById(FORMATION_ID)).thenReturn(Optional.of(new Formation()));
        when(imageRepository.findByFormationId(FORMATION_ID)).thenReturn(Optional.of(existing));
        when(imageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FormationImage saved = service.store(FORMATION_ID, file("new.png", "image/png", PNG_BYTES));

        assertThat(saved.getId()).isEqualTo(42L);
        assertThat(saved.getFilename()).isEqualTo("new.png");
    }
}
