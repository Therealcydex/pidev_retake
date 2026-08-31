package tn.esprit.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class User implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    private String password;

    private String email;

    @Enumerated(EnumType.STRING)
    private Role role;

    /**
     * Jeton de reinitialisation du mot de passe, envoye par mail. Null en temps normal :
     * il n'existe qu'entre la demande d'oubli et son utilisation, et il est efface des
     * qu'il a servi — un jeton ne vaut que pour un seul changement.
     */
    @Column(name = "reset_token")
    private String resetToken;

    /** Fin de validite du jeton. Passe cette date, il est refuse meme s'il est correct. */
    @Column(name = "reset_token_expiry")
    private LocalDateTime resetTokenExpiry;

    /**
     * Utilisateur sans reinitialisation en cours — le cas courant. Evite de repeter
     * deux null a chaque construction.
     */
    public User(Long id, String username, String password, String email, Role role) {
        this(id, username, password, email, role, null, null);
    }
}
