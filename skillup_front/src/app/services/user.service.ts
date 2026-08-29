import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AppUser } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly api = environment.apiUrl + '/users';

  constructor(private http: HttpClient) {}

  listAll(): Observable<AppUser[]> {
    return this.http.get<AppUser[]>(this.api);
  }

  getById(id: number): Observable<AppUser> {
    return this.http.get<AppUser>(this.api + '/' + id);
  }

  update(id: number, user: Partial<AppUser>): Observable<AppUser> {
    return this.http.put<AppUser>(this.api + '/' + id, user);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(this.api + '/' + id);
  }
}
