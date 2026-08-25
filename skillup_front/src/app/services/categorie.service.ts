import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Categorie } from '../models/categorie.model';

@Injectable({ providedIn: 'root' })
export class CategorieService {
  private readonly api = environment.apiUrl + '/categories';

  constructor(private http: HttpClient) {}

  listAll(): Observable<Categorie[]> {
    return this.http.get<Categorie[]>(this.api);
  }

  create(categorie: Categorie): Observable<Categorie> {
    return this.http.post<Categorie>(this.api, categorie);
  }

  update(id: number, categorie: Categorie): Observable<Categorie> {
    return this.http.put<Categorie>(this.api + '/' + id, categorie);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(this.api + '/' + id);
  }
}
