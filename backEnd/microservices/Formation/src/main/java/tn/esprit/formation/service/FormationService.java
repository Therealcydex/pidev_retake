package tn.esprit.formation.service;

import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
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
import tn.esprit.formation.repository.ChapitreRepository;
import tn.esprit.formation.repository.FormationImageRepository;
import tn.esprit.formation.repository.FormationRepository;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FormationService {

    /* Palette shared with the web UI, so the fiche looks like the app. */
    private static final Color INK    = new DeviceRgb(16, 24, 40);
    private static final Color BODY   = new DeviceRgb(71, 84, 103);
    private static final Color MUTED  = new DeviceRgb(102, 112, 133);
    private static final Color BLUE   = new DeviceRgb(37, 99, 235);
    private static final Color BORDER = new DeviceRgb(227, 230, 236);

    private final FormationRepository formationRepository;
    private final CategorieRepository categorieRepository;
    private final FormationImageRepository imageRepository;
    private final ChapitreRepository chapitreRepository;

    public FormationResponse create(FormationRequest request) {
        Categorie categorie = categorieRepository.findById(request.getCategorieId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catégorie not found"));

        Formation formation = new Formation();
        formation.setTitre(request.getTitre());
        formation.setDescription(request.getDescription());
        formation.setDescriptionDetaillee(request.getDescriptionDetaillee());
        formation.setNiveau(request.getNiveau());
        formation.setCategorie(categorie);

        Formation saved = formationRepository.save(formation);
        return toResponse(saved);
    }

    public List<FormationResponse> listAll() {
        // One query for every attached image's metadata, rather than a lookup per row.
        Map<Long, String> filenames = new HashMap<>();
        Map<Long, Long> versions = new HashMap<>();
        for (Object[] row : imageRepository.findAllImageMeta()) {
            filenames.put((Long) row[0], (String) row[1]);
            versions.put((Long) row[0], toMillis((Instant) row[2]));
        }

        // Likewise for the chapter counts shown on each catalogue card.
        Map<Long, Long> chapitreCounts = new HashMap<>();
        for (Object[] row : chapitreRepository.countGroupedByFormation()) {
            chapitreCounts.put((Long) row[0], (Long) row[1]);
        }

        return formationRepository.findAll().stream()
            .map(f -> toResponse(f, filenames.get(f.getId()),
                chapitreCounts.getOrDefault(f.getId(), 0L),
                versions.get(f.getId())))
            .toList();
    }

    public FormationResponse getById(Long id) {
        Formation formation = formationRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formation not found"));
        return toResponse(formation);
    }

    public FormationResponse update(Long id, FormationRequest request) {
        Formation formation = formationRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formation not found"));

        Categorie categorie = categorieRepository.findById(request.getCategorieId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catégorie not found"));

        formation.setTitre(request.getTitre());
        formation.setDescription(request.getDescription());
        formation.setDescriptionDetaillee(request.getDescriptionDetaillee());
        formation.setNiveau(request.getNiveau());
        formation.setCategorie(categorie);

        Formation saved = formationRepository.save(formation);
        return toResponse(saved);
    }

    public void delete(Long id) {
        formationRepository.deleteById(id);
    }

    public byte[] exportPdf(Long id) {
        Formation formation = formationRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formation not found"));

        // Fetched explicitly rather than through formation.getChapitres(), so the export
        // does not depend on the session still being open (open-in-view).
        List<Chapitre> chapitres = chapitreRepository.findByFormationId(id);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(new PdfDocument(new PdfWriter(out)));
        document.setMargins(48, 48, 48, 48);

        // Masthead
        document.add(new Paragraph("SKILLUP · FICHE FORMATION")
            .setFontSize(8.5f).setBold().setFontColor(MUTED)
            .setCharacterSpacing(1.2f).setMarginBottom(6));

        document.add(new Paragraph(formation.getTitre())
            .setFontSize(24).setBold().setFontColor(INK).setMarginBottom(4));

        document.add(new Paragraph(
            formation.getCategorie().getNom() + "   ·   " + niveauLabel(formation.getNiveau()))
            .setFontSize(11).setFontColor(MUTED).setMarginBottom(14));

        document.add(new LineSeparator(new SolidLine(0.7f))
            .setMarginBottom(20).setStrokeColor(BORDER));

        // Description
        document.add(sectionTitle("Description"));
        document.add(bodyText(formation.getDescription()));

        String detail = formation.getDescriptionDetaillee();
        if (detail != null && !detail.isBlank()) {
            document.add(sectionTitle("À propos de cette formation"));
            // Blank lines in the textarea become paragraph breaks in the fiche.
            for (String para : detail.split("\\r?\\n\\s*\\r?\\n")) {
                if (!para.isBlank()) {
                    document.add(bodyText(para.trim().replaceAll("\\s*\\r?\\n\\s*", " ")));
                }
            }
        }

        // Chapters
        document.add(sectionTitle("Programme  ·  " + chapitres.size()
            + (chapitres.size() == 1 ? " chapitre" : " chapitres")));

        if (chapitres.isEmpty()) {
            document.add(bodyText("Aucun chapitre n'a encore été ajouté à cette formation."));
        } else {
            int n = 1;
            for (Chapitre chapitre : chapitres) {
                document.add(new Paragraph(String.format("%02d   %s", n++, chapitre.getTitre()))
                    .setFontSize(12).setBold().setFontColor(INK)
                    .setMarginBottom(2).setMultipliedLeading(1.2f));

                if (chapitre.getContenu() != null && !chapitre.getContenu().isBlank()) {
                    document.add(new Paragraph(chapitre.getContenu())
                        .setFontSize(10.5f).setFontColor(BODY)
                        .setMarginLeft(26).setMarginBottom(10).setMultipliedLeading(1.4f));
                } else {
                    document.add(new Paragraph("").setMarginBottom(6));
                }
            }
        }

        // Footer
        document.add(new LineSeparator(new SolidLine(0.7f))
            .setMarginTop(14).setMarginBottom(8).setStrokeColor(BORDER));
        document.add(new Paragraph("Document généré le "
            + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
            .setFontSize(9).setFontColor(MUTED).setTextAlignment(TextAlignment.RIGHT));

        document.close();
        return out.toByteArray();
    }

    private Paragraph sectionTitle(String text) {
        return new Paragraph(text)
            .setFontSize(9).setBold().setFontColor(BLUE)
            .setCharacterSpacing(0.8f)
            .setMarginBottom(6).setMarginTop(4);
    }

    private Paragraph bodyText(String text) {
        return new Paragraph(text == null ? "" : text)
            .setFontSize(11).setFontColor(BODY)
            .setMultipliedLeading(1.45f).setMarginBottom(16);
    }

    /** Same sentence-case labels the UI shows, rather than the raw enum constant. */
    private String niveauLabel(Niveau niveau) {
        return switch (niveau) {
            case DEBUTANT -> "Débutant";
            case INTERMEDIAIRE -> "Intermédiaire";
            case AVANCE -> "Avancé";
        };
    }

    public FormationStatsResponse getStats() {
        Map<String, Long> byCategorie = new LinkedHashMap<>();
        for (Object[] row : formationRepository.countByCategorie()) {
            byCategorie.put((String) row[0], (Long) row[1]);
        }

        Map<String, Long> byNiveau = new LinkedHashMap<>();
        for (Object[] row : formationRepository.countByNiveau()) {
            byNiveau.put(((Niveau) row[0]).name(), (Long) row[1]);
        }

        return new FormationStatsResponse(
            formationRepository.count(),
            byCategorie,
            byNiveau
        );
    }

    /** Lightweight mapper for lists that only need identity — no image or chapter lookups. */
    public FormationResponse toSummary(Formation f) {
        return new FormationResponse(
            f.getId(), f.getTitre(), f.getDescription(), f.getDescriptionDetaillee(),
            f.getNiveau(), f.getCategorie().getId(), f.getCategorie().getNom(),
            false, null, 0L, null);
    }

    private FormationResponse toResponse(Formation f) {
        List<Object[]> meta = imageRepository.findMetaByFormationId(f.getId());
        String imageFilename = meta.isEmpty() ? null : (String) meta.get(0)[0];
        Long imageVersion = meta.isEmpty() ? null : toMillis((Instant) meta.get(0)[1]);

        return toResponse(f, imageFilename,
            chapitreRepository.countByFormationId(f.getId()), imageVersion);
    }

    private FormationResponse toResponse(Formation f, String imageFilename,
                                         long chapitreCount, Long imageVersion) {
        return new FormationResponse(
            f.getId(),
            f.getTitre(),
            f.getDescription(),
            f.getDescriptionDetaillee(),
            f.getNiveau(),
            f.getCategorie().getId(),
            f.getCategorie().getNom(),
            imageFilename != null,
            imageFilename,
            chapitreCount,
            imageVersion
        );
    }

    /** Rows written before updatedAt existed have none; 0 is a stable key for those. */
    private Long toMillis(Instant instant) {
        return instant == null ? 0L : instant.toEpochMilli();
    }
}
