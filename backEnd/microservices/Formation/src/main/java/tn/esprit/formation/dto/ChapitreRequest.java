package tn.esprit.formation.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChapitreRequest {
    private String titre;
    private String contenu;
    private Long formationId;
}
