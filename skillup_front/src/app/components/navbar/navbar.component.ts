import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-navbar',
  templateUrl: './navbar.component.html'
})
export class NavbarComponent {
  constructor(private auth: AuthService, private router: Router) {}

  get username(): string {
    return this.auth.getUser()?.username || '';
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
