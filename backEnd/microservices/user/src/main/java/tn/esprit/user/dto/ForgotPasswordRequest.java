package tn.esprit.user.dto;

import lombok.Getter;
import lombok.Setter;

/** Demande d'oubli de mot de passe : on ne connait que l'adresse mail. */
@Getter
@Setter
public class ForgotPasswordRequest {
    private String email;
}
