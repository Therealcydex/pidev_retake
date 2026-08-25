import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Formation, FormationStats } from '../models/formation.model';

@Injectable({ providedIn: 'root' })
export class FormationService {
  private readonly api = environment.apiUrl + '/formations';

  constructor(private http: HttpClient) {}

  listAll(): Observable<Formation[]> {
    return this.http.get<Formation[]>(this.api);
  }

  getById(id: number): Observable<Formation> {
    return this.http.get<Formation>(this.api + '/' + id);
  }

  create(formation: Formation): Observable<Formation> {
    return this.http.post<Formation>(this.api, formation);
  }

  update(id: number, formation: Formation): Observable<Formation> {
    return this.http.put<Formation>(this.api + '/' + id, formation);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(this.api + '/' + id);
  }

  getStats(): Observable<FormationStats> {
    return this.http.get<FormationStats>(this.api + '/stats');
  }

  downloadPdf(id: number): Observable<Blob> {
    return this.http.get(this.api + '/' + id + '/pdf', { responseType: 'blob' });
  }
}
