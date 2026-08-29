import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { UserService } from '../../services/user.service';
import { AuthService } from '../../services/auth.service';
import { AppUser, Role, ROLES } from '../../models/user.model';

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

  constructor(private userService: UserService, private auth: AuthService) {}

  ngOnInit(): void {
    this.load();
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
