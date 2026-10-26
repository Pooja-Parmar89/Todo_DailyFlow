import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, tap, throwError } from 'rxjs';
import { ApiService } from './api.service';
import { AuthResponse, User } from './models';

const TOKEN_KEY = 'dailyflow.token';

function readToken(): string | null {
  try { return sessionStorage.getItem(TOKEN_KEY); } catch { return null; }
}

/**
 * Token strategy: a single short-lived JWT access token (server default 60 min) kept in sessionStorage,
 * so it is dropped when the tab closes. There is no refresh token: when it expires the API answers 401,
 * the interceptor clears the session and the user signs in again.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  readonly token = signal<string | null>(readToken());
  readonly user = signal<User | null>(null);
  readonly isLoggedIn = computed(() => !!this.token());

  login(email: string, password: string) {
    return this.api.login({ email, password }).pipe(tap((r) => this.store(r)));
  }
  register(displayName: string, email: string, password: string) {
    return this.api.register({ displayName, email, password }).pipe(tap((r) => this.store(r)));
  }
  store(r: AuthResponse) {
    try { sessionStorage.setItem(TOKEN_KEY, r.token); } catch { /* storage unavailable: keep in memory */ }
    this.token.set(r.token);
    this.user.set(r.user);
  }
  /** Restores the user object after a page refresh (the token survives in sessionStorage). */
  restore() {
    return this.api.me().pipe(tap((u) => this.user.set(u)));
  }
  logout(callServer = true) {
    const clear = () => {
      try { sessionStorage.removeItem(TOKEN_KEY); } catch { /* ignore */ }
      this.token.set(null);
      this.user.set(null);
      this.router.navigate(['/login']);
    };
    if (callServer && this.token()) {
      this.api.logout().subscribe({ next: clear, error: clear });
    } else {
      clear();
    }
  }
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token();
  const authed = token && req.url.startsWith('/api') ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  return next(authed).pipe(
    catchError((err: HttpErrorResponse) => {
      const isAuthCall = req.url.startsWith('/api/auth/login') || req.url.startsWith('/api/auth/register');
      if (err.status === 401 && !isAuthCall && auth.token()) {
        auth.logout(false);
      }
      return throwError(() => err);
    }),
  );
};

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isLoggedIn() ? true : router.createUrlTree(['/login']);
};

export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isLoggedIn() ? router.createUrlTree(['/dashboard']) : true;
};

/** Turns any HttpErrorResponse into a readable message (server ApiError body when present). */
export function errorMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) return 'Cannot reach the server. Check that the backend is running.';
    const body = err.error as { message?: string; fieldErrors?: Record<string, string> } | null;
    if (body?.fieldErrors && Object.keys(body.fieldErrors).length) return Object.values(body.fieldErrors).join(' • ');
    if (body?.message) return body.message;
    return `Request failed (${err.status})`;
  }
  return 'Something went wrong';
}
export function fieldErrors(err: unknown): Record<string, string> {
  return err instanceof HttpErrorResponse ? ((err.error as { fieldErrors?: Record<string, string> } | null)?.fieldErrors ?? {}) : {};
}
