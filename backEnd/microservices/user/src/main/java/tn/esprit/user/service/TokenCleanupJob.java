package tn.esprit.user.service;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Tache planifiee : efface les codes de reinitialisation perimes.
 *
 * Pourquoi : un code expire n'est plus utilisable, mais il reste ecrit en base. Deux
 * raisons de ne pas l'y laisser —
 *
 *   1. il continue d'exister dans la boite mail de l'utilisateur ; ne le garder nulle
 *      part cote serveur reduit ce qu'une fuite de base pourrait rapprocher ;
 *   2. la colonne se remplit de valeurs mortes, une par demande jamais aboutie.
 *
 * La verification d'expiration reste faite a l'utilisation (UserService.resetPassword) :
 * ce menage ne remplace pas le controle, il nettoie derriere lui. Un code perime est
 * refuse meme si le nettoyage n'est pas encore passe.
 */
@Component
@RequiredArgsConstructor
public class TokenCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(TokenCleanupJob.class);

    private final UserService userService;

    /** Journalise aussi les passages sans rien a supprimer — utile pour la demonstration. */
    @Value("${skillup.cleanup.verbose:false}")
    private boolean verbeux;

    /**
     * Toutes les heures, decalage d'une minute apres le demarrage.
     *
     * fixedDelayString plutot que fixedRate : le delai court a la *fin* de l'execution
     * precedente, donc deux passages ne peuvent jamais se chevaucher. L'intervalle est
     * une propriete pour pouvoir le descendre a quelques secondes pendant une demonstration
     * sans toucher au code.
     */
    @Scheduled(
        fixedDelayString = "${skillup.cleanup.interval-ms:3600000}",
        initialDelayString = "${skillup.cleanup.initial-delay-ms:60000}")
    public void purgerJetonsExpires() {
        LocalDateTime maintenant = LocalDateTime.now();
        int efface = userService.purgerJetonsExpires(maintenant);

        if (efface > 0) {
            log.info("Menage des jetons : {} code(s) perime(s) efface(s)", efface);
        } else if (verbeux) {
            log.info("Menage des jetons : aucun code perime");
        }
    }
}
