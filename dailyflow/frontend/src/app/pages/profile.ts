import { Component, effect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { AuthService, errorMessage, fieldErrors } from '../core/auth';
import { UiService } from '../core/ui.service';

@Component({
  selector: 'app-profile',
  imports: [ReactiveFormsModule],
  template: `
    <div class="page-head"><h1>Profile &amp; settings</h1></div>
    <div class="cols">
      <form class="card form-col" [formGroup]="profile" (ngSubmit)="saveProfile()" novalidate>
        <h2>Profile</h2>
        <label class="field">Email<input [value]="auth.user()?.email ?? ''" disabled /></label>
        <label class="field">Name *
          <input formControlName="displayName" maxlength="100" />
          @if (profile.controls.displayName.invalid && profile.controls.displayName.touched) { <span class="err">Name is required</span> }
        </label>
        <label class="check-row"><input type="checkbox" formControlName="emailRemindersEnabled" /> Send email reminders <span class="muted small">(only works if the server has SMTP configured)</span></label>
        <label class="field">Remind me this many minutes before a task is due
          <input type="number" formControlName="reminderLeadMinutes" min="5" max="10080" />
          @if (profile.controls.reminderLeadMinutes.invalid) { <span class="err">Enter a value from 5 to 10080</span> }
        </label>
        @if (profileError()) { <p class="err" role="alert">{{ profileError() }}</p> }
        <button class="btn primary" type="submit" [disabled]="savingProfile()">{{ savingProfile() ? 'Saving…' : 'Save profile' }}</button>
      </form>

      <div class="stack">
        <form class="card form-col" [formGroup]="pwd" (ngSubmit)="savePassword()" novalidate>
          <h2>Change password</h2>
          <label class="field">Current password
            <input type="password" formControlName="currentPassword" autocomplete="current-password" />
          </label>
          <label class="field">New password
            <input type="password" formControlName="newPassword" autocomplete="new-password" />
            @if (pwd.controls.newPassword.invalid && pwd.controls.newPassword.touched) { <span class="err">At least 8 characters with a letter and a number</span> }
          </label>
          @if (pwdError()) { <p class="err" role="alert">{{ pwdError() }}</p> }
          <button class="btn primary" type="submit" [disabled]="savingPwd()">{{ savingPwd() ? 'Updating…' : 'Update password' }}</button>
        </form>

        <div class="card form-col">
          <h2>Appearance</h2>
          <p class="muted">Current theme: <strong>{{ ui.theme() === 'dark' ? 'Dark' : 'Light' }}</strong></p>
          <button type="button" class="btn" (click)="ui.toggleTheme()">Switch to {{ ui.theme() === 'dark' ? 'light' : 'dark' }} mode</button>
        </div>

        <div class="card form-col">
          <h2>Browser notifications</h2>
          @if (!ui.browserNotificationsSupported) { <p class="muted">Your browser does not support notifications.</p> }
          @else {
            <p class="muted">Status: <strong>{{ ui.browserPermission }}</strong>. Alerts appear only while DailyFlow is open in a browser tab.</p>
            @if (ui.browserPermission === 'default') { <button type="button" class="btn" (click)="enable()">Enable browser notifications</button> }
            @if (ui.browserPermission === 'denied') { <p class="muted small">Notifications are blocked. Allow them in your browser's site settings.</p> }
          }
        </div>
      </div>
    </div>`,
})
export class ProfilePage {
  readonly auth = inject(AuthService);
  readonly ui = inject(UiService);
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);

  readonly savingProfile = signal(false);
  readonly savingPwd = signal(false);
  readonly profileError = signal('');
  readonly pwdError = signal('');

  readonly profile = this.fb.nonNullable.group({
    displayName: ['', [Validators.required, Validators.maxLength(100)]],
    emailRemindersEnabled: [false],
    reminderLeadMinutes: [60, [Validators.required, Validators.min(5), Validators.max(10080)]],
  });
  readonly pwd = this.fb.nonNullable.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72), Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).+$/)]],
  });

  constructor() {
    effect(() => {
      const u = this.auth.user();
      if (u && !this.profile.dirty) this.profile.reset({ displayName: u.displayName, emailRemindersEnabled: u.emailRemindersEnabled, reminderLeadMinutes: u.reminderLeadMinutes });
    });
  }

  saveProfile() {
    this.profileError.set('');
    if (this.profile.invalid || this.savingProfile()) { this.profile.markAllAsTouched(); return; }
    const v = this.profile.getRawValue();
    this.savingProfile.set(true);
    this.api.updateProfile({ ...v, displayName: v.displayName.trim(), reminderLeadMinutes: Number(v.reminderLeadMinutes) }).subscribe({
      next: (u) => { this.savingProfile.set(false); this.auth.user.set(u); this.profile.markAsPristine(); this.ui.success('Profile saved'); },
      error: (e) => { this.savingProfile.set(false); this.profileError.set(errorMessage(e)); },
    });
  }

  savePassword() {
    this.pwdError.set('');
    if (this.pwd.invalid || this.savingPwd()) { this.pwd.markAllAsTouched(); return; }
    this.savingPwd.set(true);
    this.api.changePassword(this.pwd.getRawValue()).subscribe({
      next: (r) => { this.savingPwd.set(false); this.auth.store(r); this.pwd.reset({ currentPassword: '', newPassword: '' }); this.ui.success('Password updated'); },
      error: (e) => { this.savingPwd.set(false); this.pwdError.set(Object.values(fieldErrors(e)).join(' • ') || errorMessage(e)); },
    });
  }

  async enable() {
    const r = await this.ui.requestBrowserPermission();
    this.ui.info(r === 'granted' ? 'Browser notifications enabled' : 'Browser notifications were not allowed');
  }
}
