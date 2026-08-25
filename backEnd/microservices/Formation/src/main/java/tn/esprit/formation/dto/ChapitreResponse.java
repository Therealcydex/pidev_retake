package tn.esprit.formation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ChapitreResponse {
    private Long id;
    private String titre;
    private String contenu;
    private Long formationId;
}
