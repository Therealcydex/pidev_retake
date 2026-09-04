import { Injectable } from '@angular/core';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from '../services/auth.service';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  constructor(private auth: AuthService) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const token = this.auth.getToken();
    if (token) {
      // HttpRequest is immutable: setting the header on `req` directly would be silently
      // dropped, so the header goes on a clone. Doing it here rather than in each service
      // is why no other file in the app ever mentions the token.
      req = req.clone({
        setHeaders: { Authorization: 'Bearer ' + token }
      });
    }
    return next.handle(req);
  }
}
