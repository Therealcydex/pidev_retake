import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';

import { ForgotPasswordComponent } from './forgot-password.component';
import { environment } from '../../../environments/environment';

describe('ForgotPasswordComponent', () => {
  let component: ForgotPasswordComponent;
  let http: HttpTestingController;
  const api = environment.apiUrl + '/auth';

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [ForgotPasswordComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigate: jasmine.createSpy('navigate') } }
      ]
    });
    component = TestBed.createComponent(ForgotPasswordComponent).componentInstance;
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts on the email step', () => {
    expect(component.etape).toBe(1);
  });

  it('posts the email and moves to the code step', () => {
    component.email = 'wassim@esprit.tn';
    component.demanderCode();

    const req = http.expectOne(api + '/forgot-password');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'wassim@esprit.tn' });
    req.flush(null);

    expect(component.etape).toBe(2);
  });

  it('moves on even for an unknown address, since the server never says', () => {
    component.email = 'inconnu@esprit.tn';
    component.demanderCode();

    // Le serveur répond 200 quoi qu'il arrive : l'écran ne doit pas laisser deviner
    // qu'un compte existe ou non.
    http.expectOne(api + '/forgot-password').flush(null);

    expect(component.etape).toBe(2);
    expect(component.error).toBe('');
  });

  it('refuses two passwords that differ, without calling the server', () => {
    component.token = '123456';
    component.newPassword = 'Reset1234!';
    component.confirmation = 'autre-chose';

    component.reinitialiser();

    expect(component.error).toContain('ne correspondent pas');
    http.expectNone(api + '/reset-password');
  });

  it('sends the code and the new password', () => {
    component.token = '123456';
    component.newPassword = 'Reset1234!';
    component.confirmation = 'Reset1234!';

    component.reinitialiser();

    const req = http.expectOne(api + '/reset-password');
    expect(req.request.body).toEqual({ token: '123456', newPassword: 'Reset1234!' });
    req.flush(null);

    expect(component.succes).toBeTrue();
  });

  it('explains a rejected code rather than showing a generic failure', () => {
    component.token = '000000';
    component.newPassword = 'Reset1234!';
    component.confirmation = 'Reset1234!';

    component.reinitialiser();

    http.expectOne(api + '/reset-password')
      .flush(null, { status: 400, statusText: 'Bad Request' });

    expect(component.error).toContain('expiré');
    expect(component.succes).toBeFalse();
  });

  it('clears the code when starting over', () => {
    component.etape = 2;
    component.token = '123456';
    component.error = 'quelque chose';

    component.recommencer();

    // Le champ est typé 1 | 2 : l'affectation ci-dessus le restreint à 2 pour
    // TypeScript, d'où l'élargissement explicite ici.
    expect(component.etape as number).toBe(1);
    expect(component.token).toBe('');
    expect(component.error).toBe('');
  });
});
