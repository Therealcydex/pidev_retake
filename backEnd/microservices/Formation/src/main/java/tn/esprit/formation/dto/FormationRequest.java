package tn.esprit.formation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import tn.esprit.formation.entity.Niveau;

@Getter
@Setter
public class FormationRequest {
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
