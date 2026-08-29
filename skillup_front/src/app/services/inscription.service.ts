import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Inscription } from '../models/inscription.model';
import { Formation } from '../models/formation.model';

@Injectable({ providedIn: 'root' })
export class InscriptionService {
  private readonly api = environment.apiUrl + '/formations';

  constructor(private http: HttpClient) {}

  enroll(formationId: number): Observable<void> {
    return this.http.post<void>(this.api + '/' + formationId + '/inscription', {});
  }

  unenroll(formationId: number): Observable<void> {
    return this.http.delete<void>(this.api + '/' + formationId + '/inscription');
  }

  /** Ids of the formations the logged-in trainee is enrolled in. */
  myFormationIds(): Observable<number[]> {
    return this.http.get<number[]>(this.api + '/mes-inscriptions');
  }

  /** Everyone enrolled in one formation — admins and trainers only. */
  listByFormation(formationId: number): Observable<Inscription[]> {
    return this.http.get<Inscription[]>(this.api + '/' + formationId + '/inscriptions');
  }

  /** Which formations one user is enrolled in — admin only. */
  formationsOfUser(userId: number): Observable<Formation[]> {
    return this.http.get<Formation[]>(this.api + '/inscriptions/utilisateur/' + userId);
  }
}
