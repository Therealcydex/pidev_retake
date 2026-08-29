import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { UserService } from '../../services/user.service';
import { AuthService } from '../../services/auth.service';
import { AppUser, Role, ROLES } from '../../models/user.model';
import { Formation } from '../../models/formation.model';
import { InscriptionService } from '../../services/inscription.service';

@Component({
  selector: 'app-user-list',
  templateUrl: './user-list.component.html'
})
export class UserListComponent implements OnInit {
  users: AppUser[] = [];
  roles = ROLES;
  editingId: number | null = null;
  editingRole: Role = 'TRAINEE';
  error = '';

  constructor(
    private userService: UserService,
    private auth: AuthService,
    private inscriptionService: InscriptionService
  ) {}

  /** userId -> number of formations followed, loaded once for the whole table. */
  counts: Record<number, number> = {};

  ngOnInit(): void {
    this.load();
    this.loadCounts();
  }

  private loadCounts(): void {
    this.inscriptionService.countsByUser().subscribe({
      next: (c) => (this.counts = c),
      error: () => (this.counts = {})
    });
  }

  countFor(u: AppUser): number {
    return this.counts[u.id] || 0;
  }

  load(): void {
    this.userService.listAll().subscribe({
      next: (data) => (this.users = data),
      error: (err) => this.showError(err)
    });
  }

  isSelf(u: AppUser): boolean {
    return this.auth.getUser()?.id === u.id;
  }

  /* ---- enrolments, expanded one user at a time ---- */

  expandedId: number | null = null;
  inscriptions: Formation[] = [];
  loadingInscriptions = false;

  toggleInscriptions(u: AppUser): void {
    if (this.expandedId === u.id) {
      this.expandedId = null;
      return;
    }

    this.expandedId = u.id;
    this.inscriptions = [];
    this.loadingInscriptions = true;

    this.inscriptionService.formationsOfUser(u.id).subscribe({
      next: (list) => {
        this.inscriptions = list;
        this.loadingInscriptions = false;
      },
      error: (err) => {
        this.loadingInscriptions = false;
        this.showError(err);
      }
    });
  }

  startEdit(u: AppUser): void {
    this.error = '';
    this.editingId = u.id;
    this.editingRole = u.role;
  }

  cancelEdit(): void {
    this.editingId = null;
  }

  saveEdit(u: AppUser): void {
    if (this.editingId === null) return;

    this.userService.update(this.editingId, {
      username: u.username,
      email: u.email,
      role: this.editingRole
    }).subscribe({
      next: () => {
        this.cancelEdit();
        this.load();
      },
      error: (err) => this.showError(err)
    });
  }

  delete(u: AppUser): void {
    if (!confirm('Delete user "' + u.username + '"?')) return;

    this.userService.delete(u.id).subscribe({
      next: () => this.load(),
      error: (err) => this.showError(err)
    });
  }

  private showError(err: HttpErrorResponse): void {
    this.error = err.error?.message || err.error?.error || 'Request failed (' + err.status + ')';
  }
}
