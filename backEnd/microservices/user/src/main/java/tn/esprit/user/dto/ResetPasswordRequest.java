package tn.esprit.user.dto;

import lombok.Getter;
import lombok.Setter;

/** Echange du jeton recu par mail contre un nouveau mot de passe. */
@Getter
@Setter
public class ResetPasswordRequest {
    private String token;
    private String newPassword;
}
