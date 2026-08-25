package tn.esprit.formation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
public class FormationStatsResponse {
    private long totalFormations;
    private Double averagePrix;
    private Double minPrix;
    private Double maxPrix;
    private Map<String, Long> countByCategorie;
    private Map<String, Long> countByNiveau;
}
