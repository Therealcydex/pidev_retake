import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Suggestions } from '../models/recommandation.model';

/**
 * Appelle le service de recommandation (FastAPI).
 *
 * L'URL passe par la gateway, comme tous les autres appels : celle-ci route
 * /recommandations/** vers le service Python. Le front n'a donc qu'une seule adresse
 * de base, et ignore que ce service n'est pas écrit en Java.
 */
@Injectable({ providedIn: 'root' })
export class RecommandationService {
  private readonly api = environment.apiUrl + '/recommandations';

  constructor(private http: HttpClient) {}

  pourApprenant(userId: number, k = 5): Observable<Suggestions> {
    return this.http.get<Suggestions>(`${this.api}/${userId}?k=${k}`);
  }
}
