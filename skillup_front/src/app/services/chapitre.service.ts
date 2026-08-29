import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Chapitre } from '../models/chapitre.model';

@Injectable({ providedIn: 'root' })
export class ChapitreService {
  private readonly api = environment.apiUrl + '/chapitres';

  constructor(private http: HttpClient) {}

  listByFormation(formationId: number): Observable<Chapitre[]> {
    return this.http.get<Chapitre[]>(this.api + '/formation/' + formationId);
  }

  create(chapitre: Chapitre): Observable<Chapitre> {
    return this.http.post<Chapitre>(this.api, chapitre);
  }

  update(id: number, chapitre: Chapitre): Observable<Chapitre> {
    return this.http.put<Chapitre>(this.api + '/' + id, chapitre);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(this.api + '/' + id);
  }
}
