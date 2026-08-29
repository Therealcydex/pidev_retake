import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from './services/auth.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html'
})
export class AppComponent {
  private readonly authRoutes = ['/login', '/signup'];

  constructor(private auth: AuthService, private router: Router) {}

  get showNavbar(): boolean {
    const path = this.router.url.split('?')[0].split('#')[0];
    return this.auth.isLoggedIn() && !this.authRoutes.includes(path);
  }
}
