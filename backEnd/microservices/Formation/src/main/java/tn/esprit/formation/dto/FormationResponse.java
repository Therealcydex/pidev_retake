package tn.esprit.formation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import tn.esprit.formation.entity.Niveau;

@Getter
@Setter
@AllArgsConstructor
public class FormationResponse {
    private Long id;
    private String titre;
    private String description;
    private Double prix;
    private Niveau niveau;
    private Long categorieId;
    private String categorieNom;
}
