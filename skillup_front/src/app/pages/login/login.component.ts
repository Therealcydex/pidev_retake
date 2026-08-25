import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {
  username = '';
  password = '';
  error = '';
  loading = false;

  constructor(private auth: AuthService, private router: Router) {}

  submit(): void {
    this.error = '';
    this.loading = true;
    this.auth.login({ username: this.username, password: this.password }).subscribe({
      next: () => this.router.navigate(['/formations']),
      error: () => {
        this.error = 'Invalid username or password';
        this.loading = false;
      }
    });
  }
}
