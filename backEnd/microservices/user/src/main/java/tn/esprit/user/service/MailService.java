package tn.esprit.user.service;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envoi des mails du service USER.
 *
 * Isole ici plutot que dans UserService : celui-ci parle d'utilisateurs, pas de SMTP.
 * Si demain le mail part par un autre canal, seule cette classe change.
 */
@Service
@RequiredArgsConstructor
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String expediteur;

    /**
     * Envoie le jeton de reinitialisation.
     *
     * L'echec d'envoi n'interrompt pas la demande : le jeton est deja enregistre et
     * reste valable. On le journalise alors, ce qui permet de continuer meme si le
     * reseau bloque le port SMTP — un cas frequent sur un poste d'ecole.
     */
    public void envoyerJetonReinitialisation(String destinataire, String jeton, int minutes) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(expediteur);
        message.setTo(destinataire);
        message.setSubject("SkillUp — reinitialisation de votre mot de passe");
        message.setText("""
                Bonjour,

                Vous avez demande a reinitialiser votre mot de passe SkillUp.

                Votre code de reinitialisation :

                    %s

                Ce code est valable %d minutes et ne peut servir qu'une seule fois.

                Si vous n'etes pas a l'origine de cette demande, ignorez ce message :
                votre mot de passe reste inchange.

                L'equipe SkillUp
                """.formatted(jeton, minutes));

        try {
            mailSender.send(message);
            log.info("Mail de reinitialisation envoye a {}", destinataire);
        } catch (Exception e) {
            log.error("Envoi du mail a {} impossible : {}", destinataire, e.getMessage());
            log.warn("Jeton de reinitialisation pour {} : {}", destinataire, jeton);
        }
    }
}
