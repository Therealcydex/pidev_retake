import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthResponse, LoginRequest, SignupRequest } from '../models/auth.model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = environment.apiUrl + '/auth';

  constructor(private http: HttpClient) {}

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(this.api + '/login', request)
      .pipe(tap(res => this.saveAuth(res)));
  }

  signup(request: SignupRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(this.api + '/signup', request)
      .pipe(tap(res => this.saveAuth(res)));
  }

  /** Étape 1 : demande l'envoi d'un code à l'adresse mail du compte. */
  forgotPassword(email: string): Observable<void> {
    return this.http.post<void>(this.api + '/forgot-password', { email });
  }

  /** Étape 2 : échange le code reçu par mail contre un nouveau mot de passe. */
  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>(this.api + '/reset-password', { token, newPassword });
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }

  getUser(): AuthResponse | null {
    const raw = localStorage.getItem('user');
    return raw ? JSON.parse(raw) : null;
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  private saveAuth(res: AuthResponse): void {
    localStorage.setItem('token', res.token);
    localStorage.setItem('user', JSON.stringify(res));
  }
}
