import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { FormationService } from '../../services/formation.service';

@Component({
  selector: 'app-navbar',
  templateUrl: './navbar.component.html'
})
export class NavbarComponent implements OnInit {
  username = '';
  email = '';

  get isAdmin(): boolean {
    return this.auth.getUser()?.role === 'ADMIN';
  }

  /** Two-letter monogram for the avatar, as in the design ("AD"). */
  get initials(): string {
    const source = this.username || this.email;
    return source.slice(0, 2).toUpperCase();
  }

  constructor(
    private auth: AuthService,
    private formations: FormationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    const user = this.auth.getUser();
    this.username = user?.username || '';
    this.email = user?.email || '';

    this.formations.whoami().subscribe({
      next: user => {
        this.username = user.username;
        this.email = user.email;
      },
      error: () => {}
    });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
