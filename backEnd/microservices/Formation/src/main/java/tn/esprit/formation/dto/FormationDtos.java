package tn.esprit.formation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import tn.esprit.formation.entity.Niveau;

import java.util.Map;

/**
 * The formation payloads, grouped in one file rather than three.
 *
 * Nested types serialise exactly like top-level ones — Jackson reads field names, not
 * class names — so the JSON on the wire is unchanged.
 */
public final class FormationDtos {

    private FormationDtos() {
    }

    @Getter
    @Setter
    public static class Request {
        @NotBlank(message = "Le titre est obligatoire")
        private String titre;

        /**
         * Mirrors maxlength="100" on the form. The browser limit is a convenience; this is
         * the one that holds for any client, including curl.
         */
        @NotBlank(message = "La description brève est obligatoire")
        @Size(max = 100, message = "La description brève ne peut pas dépasser 100 caractères")
        private String description;

        /** Deliberately unbounded — it is mapped to TEXT and shown on the detail page. */
        private String descriptionDetaillee;

        private Niveau niveau;

        private Long categorieId;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String titre;
        private String description;
        private String descriptionDetaillee;
        private Niveau niveau;
        private Long categorieId;
        private String categorieNom;
        private boolean hasImage;
        private String imageFilename;
        private long chapitreCount;
        /** Who created the formation; drives whether a trainer may edit it. */
        private Long ownerId;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class Stats {
        private long totalFormations;
        private Map<String, Long> countByCategorie;
        private Map<String, Long> countByNiveau;
    }
}
