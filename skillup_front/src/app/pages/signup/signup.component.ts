import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-signup',
  templateUrl: './signup.component.html'
})
export class SignupComponent {
  username = '';
  email = '';
  password = '';
  acceptTerms = false;
  error = '';
  loading = false;

  constructor(private auth: AuthService, private router: Router) {}

  /** 0-4, drives the strength meter in the design. */
  get passwordStrength(): number {
    const p = this.password;
    if (!p) return 0;

    let score = 0;
    if (p.length >= 8) score++;
    if (/\d/.test(p)) score++;
    if (/[a-z]/.test(p) && /[A-Z]/.test(p)) score++;
    if (/[^A-Za-z0-9]/.test(p)) score++;
    return score;
  }

  submit(): void {
    this.error = '';
    this.loading = true;
    this.auth.signup({ username: this.username, email: this.email, password: this.password }).subscribe({
      next: () => this.router.navigate(['/formations']),
      error: (err) => {
        this.error = err?.error?.message || 'Signup failed';
        this.loading = false;
      }
    });
  }
}
