import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Creating and editing formations is open to admins and trainers. */
export const staffGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const role = auth.getUser()?.role;
  if (role === 'ADMIN' || role === 'TRAINER') return true;

  router.navigate(['/formations']);
  return false;
};
