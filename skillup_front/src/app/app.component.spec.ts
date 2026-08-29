import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { RouterModule } from '@angular/router';
import { AppComponent } from './app.component';

describe('AppComponent', () => {
  beforeEach(async () => {
    localStorage.clear();

    await TestBed.configureTestingModule({
      // CommonModule for *ngIf, RouterModule for <router-outlet>.
      imports: [CommonModule, RouterModule.forRoot([])],
      declarations: [AppComponent],
      // AuthService is providedIn: 'root' and injects HttpClient.
      providers: [provideHttpClient(), provideHttpClientTesting()],
      // <app-navbar> is declared in AppModule, not here — don't resolve it.
      schemas: [CUSTOM_ELEMENTS_SCHEMA],
    }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('hides the navbar when logged out', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.showNavbar).toBeFalse();
    expect(fixture.nativeElement.querySelector('app-navbar')).toBeNull();
  });

  it('shows the navbar once a token is stored', () => {
    localStorage.setItem('token', 'fake-jwt');

    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.showNavbar).toBeTrue();
    expect(fixture.nativeElement.querySelector('app-navbar')).not.toBeNull();
  });
});
