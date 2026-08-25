import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-signup',
  templateUrl: './signup.component.html',
  styleUrls: ['./signup.component.css']
})
export class SignupComponent {
  username = '';
  email = '';
  password = '';
  error = '';
  loading = false;

  constructor(private auth: AuthService, private router: Router) {}

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
