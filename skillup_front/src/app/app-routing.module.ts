import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { LoginComponent } from './pages/login/login.component';
import { SignupComponent } from './pages/signup/signup.component';
import { FormationListComponent } from './pages/formation-list/formation-list.component';
import { FormationFormComponent } from './pages/formation-form/formation-form.component';
import { CategorieListComponent } from './pages/categorie-list/categorie-list.component';
import { StatsComponent } from './pages/stats/stats.component';
import { authGuard } from './guards/auth.guard';

const routes: Routes = [
  { path: '', redirectTo: '/formations', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'signup', component: SignupComponent },
  { path: 'formations', component: FormationListComponent, canActivate: [authGuard] },
  { path: 'formations/new', component: FormationFormComponent, canActivate: [authGuard] },
  { path: 'formations/:id/edit', component: FormationFormComponent, canActivate: [authGuard] },
  { path: 'categories', component: CategorieListComponent, canActivate: [authGuard] },
  { path: 'stats', component: StatsComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: '/formations' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
