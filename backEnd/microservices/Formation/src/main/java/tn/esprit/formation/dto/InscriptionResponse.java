package tn.esprit.formation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One enrolled trainee. The id comes from this service's `inscriptions` table; the
 * username and email are fetched from the USER service over Feign.
 */
@Getter
@Setter
@AllArgsConstructor
public class InscriptionResponse {
    private Long userId;
    private String username;
    private String email;
    private Instant dateInscription;
}
