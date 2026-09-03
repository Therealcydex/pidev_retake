import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { InscriptionService } from './inscription.service';
import { FormationService } from './formation.service';
import { environment } from '../../environments/environment';

describe('InscriptionService', () => {
  let service: InscriptionService;
  let http: HttpTestingController;
  const api = environment.apiUrl + '/formations';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(InscriptionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('enrols with a POST and no meaningful body', () => {
    service.enroll(3).subscribe();
    const req = http.expectOne(api + '/3/inscription');
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });

  it('unenrols with a DELETE', () => {
    service.unenroll(3).subscribe();
    const req = http.expectOne(api + '/3/inscription');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('reads the caller\'s own enrolments', () => {
    let ids: number[] | undefined;
    service.myFormationIds().subscribe((r) => (ids = r));

    const req = http.expectOne(api + '/mes-inscriptions');
    expect(req.request.method).toBe('GET');
    req.flush([1, 4]);

    expect(ids).toEqual([1, 4]);
  });

  it('reads the roster for one formation', () => {
    service.listByFormation(9).subscribe();
    http.expectOne(api + '/9/inscriptions').flush([]);
  });

  it('reads which formations a user follows', () => {
    service.formationsOfUser(18).subscribe();
    http.expectOne(api + '/inscriptions/utilisateur/18').flush([]);
  });

  it('reads the per-user counts', () => {
    service.countsByUser().subscribe();
    http.expectOne(api + '/inscriptions/compteurs').flush({});
  });
});

describe('FormationService image URLs', () => {
  let service: FormationService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(FormationService);
  });

  it('points at the image endpoint for a formation', () => {
    expect(service.imageUrl(3)).toContain('/formations/3/image');
  });
});
