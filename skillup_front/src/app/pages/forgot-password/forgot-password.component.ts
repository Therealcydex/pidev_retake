import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

/**
 * Mot de passe oublié, en deux étapes dans un seul écran.
 *
 * Étape 1 : l'utilisateur donne son adresse, le serveur lui envoie un code par mail.
 * Étape 2 : il saisit ce code et son nouveau mot de passe.
 *
 * Les deux tiennent dans le même composant parce qu'elles se suivent immédiatement :
 * passer par deux routes obligerait à transporter l'adresse de l'une à l'autre, pour
 * aucun gain.
 */
@Component({
  selector: 'app-forgot-password',
  templateUrl: './forgot-password.component.html'
})
export class ForgotPasswordComponent {
  /** 1 = saisie de l'adresse, 2 = saisie du code et du nouveau mot de passe. */
  etape: 1 | 2 = 1;

  email = '';
  token = '';
  newPassword = '';
  confirmation = '';
  showPassword = false;

  error = '';
  loading = false;
  succes = false;

  constructor(private auth: AuthService, private router: Router) {}

  /**
   * Le serveur répond 200 même si l'adresse est inconnue — c'est voulu, sinon cet écran
   * permettrait de découvrir qui possède un compte. On passe donc toujours à l'étape 2,
   * et le message reste au conditionnel : « si un compte existe ».
   */
  demanderCode(): void {
    this.error = '';
    this.loading = true;

    this.auth.forgotPassword(this.email).subscribe({
      next: () => {
        this.etape = 2;
        this.loading = false;
      },
      error: () => {
        this.error = "Le service est indisponible. Réessayez dans un instant.";
        this.loading = false;
      }
    });
  }

  reinitialiser(): void {
    this.error = '';

    if (this.newPassword !== this.confirmation) {
      this.error = 'Les deux mots de passe ne correspondent pas.';
      return;
    }

    this.loading = true;

    this.auth.resetPassword(this.token, this.newPassword).subscribe({
      next: () => {
        this.succes = true;
        this.loading = false;
        // Laisse le temps de lire la confirmation avant de revenir au formulaire.
        setTimeout(() => this.router.navigate(['/login']), 2000);
      },
      error: (err) => {
        this.error = err?.status === 400
          ? 'Code invalide ou expiré. Demandez-en un nouveau.'
          : "La réinitialisation a échoué.";
        this.loading = false;
      }
    });
  }

  /** Retour à l'étape 1 pour renvoyer un code, par exemple après expiration. */
  recommencer(): void {
    this.etape = 1;
    this.token = '';
    this.newPassword = '';
    this.confirmation = '';
    this.error = '';
  }
}
