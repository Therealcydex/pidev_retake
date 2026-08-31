import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { LoginComponent } from './pages/login/login.component';
import { SignupComponent } from './pages/signup/signup.component';
import { ForgotPasswordComponent } from './pages/forgot-password/forgot-password.component';
import { FormationListComponent } from './pages/formation-list/formation-list.component';
import { FormationFormComponent } from './pages/formation-form/formation-form.component';
import { FormationDetailComponent } from './pages/formation-detail/formation-detail.component';
import { StatsComponent } from './pages/stats/stats.component';
import { UserListComponent } from './pages/user-list/user-list.component';
import { authGuard } from './guards/auth.guard';
import { adminGuard } from './guards/admin.guard';
import { staffGuard } from './guards/staff.guard';
import { guestGuard } from './guards/guest.guard';

const routes: Routes = [
  { path: '', redirectTo: '/formations', pathMatch: 'full' },
  { path: 'login', component: LoginComponent, canActivate: [guestGuard] },
  { path: 'signup', component: SignupComponent, canActivate: [guestGuard] },
  { path: 'forgot-password', component: ForgotPasswordComponent, canActivate: [guestGuard] },
  { path: 'formations', component: FormationListComponent, canActivate: [authGuard] },
  // 'new' must stay above ':id' — Angular matches top-down and would otherwise read
  // "new" as a formation id.
  { path: 'formations/new', component: FormationFormComponent, canActivate: [authGuard, staffGuard] },
  { path: 'formations/:id', component: FormationDetailComponent, canActivate: [authGuard] },
  { path: 'formations/:id/edit', component: FormationFormComponent, canActivate: [authGuard, staffGuard] },
  { path: 'stats', component: StatsComponent, canActivate: [authGuard] },
  { path: 'users', component: UserListComponent, canActivate: [authGuard, adminGuard] },
  { path: '**', redirectTo: '/formations' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
