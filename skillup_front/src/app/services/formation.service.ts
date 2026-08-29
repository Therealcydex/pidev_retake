import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Formation, FormationStats } from '../models/formation.model';
import { UserInfo } from '../models/auth.model';

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

  whoami(): Observable<UserInfo> {
    return this.http.get<UserInfo>(this.api + '/whoami');
  }

  downloadPdf(id: number): Observable<Blob> {
    return this.http.get(this.api + '/' + id + '/pdf', { responseType: 'blob' });
  }

  /**
   * Uploads the formation illustration. Content-Type is deliberately not set — the
   * browser must add it along with the multipart boundary.
   */
  uploadImage(id: number, file: File): Observable<Formation> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<Formation>(this.api + '/' + id + '/image', form);
  }

  deleteImage(id: number): Observable<void> {
    return this.http.delete<void>(this.api + '/' + id + '/image');
  }

  /**
   * Direct <img src> target for the formation illustration.
   *
   * The response is cached for an hour, so the URL carries the image's own version
   * (epoch millis of the last upload). That makes a replaced image show up everywhere
   * immediately — across components, page loads and sessions — which a per-component
   * counter could not do.
   */
  imageUrl(id: number, version?: number | null): string {
    return this.api + '/' + id + '/image?v=' + (version ?? 0);
  }
}
