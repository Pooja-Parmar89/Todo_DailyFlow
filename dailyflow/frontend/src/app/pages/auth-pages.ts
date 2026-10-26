import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService, errorMessage, fieldErrors } from '../core/auth';
import { UiService } from '../core/ui.service';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth-wrap">
      <form class="card auth-card" [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <h1>✔ DailyFlow</h1>
        <p class="muted">Sign in to your tasks.</p>
        <label class="field">Email
          <input type="email" formControlName="email" autocomplete="username" />
          @if (bad('email')) { <span class="err">Enter your email</span> }
        </label>
        <label class="field">Password
          <input type="password" formControlName="password" autocomplete="current-password" />
          @if (bad('password')) { <span class="err">Enter your password</span> }
        </label>
        @if (error()) { <p class="err" role="alert">{{ error() }}</p> }
        <button class="btn primary block" type="submit" [disabled]="busy()">{{ busy() ? 'Signing in…' : 'Sign in' }}</button>
        <p class="muted center">New here? <a routerLink="/register">Create an account</a></p>
      </form>
    </div>`,
})
export class LoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly ui = inject(UiService);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly form = this.fb.nonNullable.group({ email: ['', Validators.required], password: ['', Validators.required] });
  bad(n: 'email' | 'password') { const c = this.form.controls[n]; return c.invalid && (c.touched || c.dirty); }
  submit() {
    this.error.set('');
    if (this.form.invalid || this.busy()) { this.form.markAllAsTouched(); return; }
    this.busy.set(true);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email.trim(), password).subscribe({
      next: (r) => { this.ui.success(`Welcome back, ${r.user.displayName}`); this.router.navigate(['/dashboard']); },
      error: (e) => { this.busy.set(false); this.error.set(e.status === 401 ? 'Invalid email or password' : errorMessage(e)); },
    });
  }
}

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth-wrap">
      <form class="card auth-card" [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <h1>Create your account</h1>
        <label class="field">Name
          <input formControlName="displayName" autocomplete="name" maxlength="100" />
          @if (bad('displayName')) { <span class="err">{{ server()['displayName'] || 'Name is required' }}</span> }
        </label>
        <label class="field">Email
          <input type="email" formControlName="email" autocomplete="email" />
          @if (bad('email')) { <span class="err">{{ server()['email'] || 'Enter a valid email address' }}</span> }
        </label>
        <label class="field">Password
          <input type="password" formControlName="password" autocomplete="new-password" />
          @if (bad('password')) { <span class="err">{{ server()['password'] || 'At least 8 characters with a letter and a number' }}</span> }
        </label>
        @if (error()) { <p class="err" role="alert">{{ error() }}</p> }
        <button class="btn primary block" type="submit" [disabled]="busy()">{{ busy() ? 'Creating…' : 'Create account' }}</button>
        <p class="muted center">Already registered? <a routerLink="/login">Sign in</a></p>
      </form>
    </div>`,
})
export class RegisterPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly ui = inject(UiService);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly server = signal<Record<string, string>>({});
  readonly form = this.fb.nonNullable.group({
    displayName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72), Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).+$/)]],
  });
  bad(n: 'displayName' | 'email' | 'password') { const c = this.form.controls[n]; return (c.invalid && (c.touched || c.dirty)) || !!this.server()[n]; }
  submit() {
    this.error.set(''); this.server.set({});
    if (this.form.invalid || this.busy()) { this.form.markAllAsTouched(); return; }
    this.busy.set(true);
    const v = this.form.getRawValue();
    this.auth.register(v.displayName.trim(), v.email.trim(), v.password).subscribe({
      next: () => { this.ui.success('Account created'); this.router.navigate(['/dashboard']); },
      error: (e) => { this.busy.set(false); this.server.set(fieldErrors(e)); this.error.set(errorMessage(e)); },
    });
  }
}
