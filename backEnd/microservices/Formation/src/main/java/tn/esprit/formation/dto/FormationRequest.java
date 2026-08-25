package tn.esprit.formation.dto;

import lombok.Getter;
import lombok.Setter;
import tn.esprit.formation.entity.Niveau;

@Getter
@Setter
public class FormationRequest {
    private String titre;
    private String description;
    private Double prix;
    private Niveau niveau;
    private Long categorieId;
}
