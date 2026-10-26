import { Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth';
import { ReminderSummary } from '../core/models';
import { UiService } from '../core/ui.service';
import { fmtTime, initials } from '../core/util';
import { TaskFormComponent, TaskFormService } from '../shared/task-form';

interface NavItem { path: string; label: string; icon: string }

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TaskFormComponent],
  template: `
    <a class="skip" href="#main">Skip to content</a>
    <div class="shell">
      <aside class="sidebar" [class.open]="menuOpen()" aria-label="Main navigation">
        <div class="brand">✔ DailyFlow</div>
        <nav>
          @for (n of nav; track n.path) {
            <a [routerLink]="n.path" routerLinkActive="active" (click)="menuOpen.set(false)"><span class="ico" aria-hidden="true">{{ n.icon }}</span>{{ n.label }}</a>
          }
        </nav>
        <div class="sidebar-foot">
          <span class="avatar" aria-hidden="true">{{ initials(auth.user()?.displayName) }}</span>
          <div class="grow"><div class="name">{{ auth.user()?.displayName }}</div><div class="muted small">{{ auth.user()?.email }}</div></div>
        </div>
      </aside>
      @if (menuOpen()) { <div class="scrim" (click)="menuOpen.set(false)"></div> }

      <div class="main-col">
        <header class="topbar">
          <button type="button" class="icon-btn menu-btn" (click)="menuOpen.set(!menuOpen())" aria-label="Toggle navigation menu" [attr.aria-expanded]="menuOpen()">☰</button>
          <div class="grow"></div>
          <button type="button" class="btn primary" (click)="quickAdd()">＋ Quick add</button>
          <div class="bell-wrap">
            <button type="button" class="icon-btn" (click)="bellOpen.set(!bellOpen())" [attr.aria-label]="'Reminders: ' + badge() + ' need attention'" aria-haspopup="true" [attr.aria-expanded]="bellOpen()">
              🔔 @if (badge() > 0) { <span class="bell-count">{{ badge() }}</span> }
            </button>
            @if (bellOpen()) {
              <div class="popover" role="dialog" aria-label="Reminders">
                <h3>Reminders</h3>
                @if (!reminders() || (reminders()!.overdueCount === 0 && reminders()!.dueSoon.length === 0)) {
                  <p class="muted">Nothing needs your attention right now.</p>
                }
                @if (reminders() && reminders()!.overdueCount > 0) {
                  <a routerLink="/overdue" class="pop-link danger-text" (click)="bellOpen.set(false)">⚠ {{ reminders()!.overdueCount }} overdue task(s)</a>
                }
                @for (i of reminders()?.dueSoon ?? []; track i.key) {
                  <div class="pop-item"><strong>{{ i.title }}</strong><span class="muted small">due {{ time(i.dueAt) }}</span></div>
                }
                @if (ui.browserNotificationsSupported && ui.browserPermission === 'default') {
                  <button type="button" class="btn small" (click)="enableBrowser()">Enable browser notifications</button>
                }
                <p class="muted small">Reminders refresh while this page is open. They are not delivered when the app is closed.</p>
              </div>
            }
          </div>
          <button type="button" class="icon-btn" (click)="ui.toggleTheme()" [attr.aria-label]="ui.theme() === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'">{{ ui.theme() === 'dark' ? '☀' : '☾' }}</button>
          <button type="button" class="btn" (click)="auth.logout()">Log out</button>
        </header>
        <main id="main" tabindex="-1"><router-outlet /></main>
      </div>
    </div>
    <app-task-form />`,
})
export class Shell {
  readonly auth = inject(AuthService);
  readonly ui = inject(UiService);
  private readonly api = inject(ApiService);
  private readonly form = inject(TaskFormService);

  readonly nav: NavItem[] = [
    { path: '/dashboard', label: 'Dashboard', icon: '▦' },
    { path: '/tasks', label: 'All tasks', icon: '☰' },
    { path: '/today', label: 'Today & upcoming', icon: '☀' },
    { path: '/calendar', label: 'Calendar', icon: '▤' },
    { path: '/overdue', label: 'Overdue', icon: '⚠' },
    { path: '/completed', label: 'Completed', icon: '✓' },
    { path: '/categories', label: 'Categories', icon: '◍' },
    { path: '/reports', label: 'Reports', icon: '▥' },
    { path: '/profile', label: 'Profile & settings', icon: '⚙' },
  ];
  readonly menuOpen = signal(false);
  readonly bellOpen = signal(false);
  readonly reminders = signal<ReminderSummary | null>(null);
  readonly badge = () => (this.reminders()?.overdueCount ?? 0) + (this.reminders()?.dueSoon.length ?? 0);
  initials = initials;
  time = fmtTime;
  private readonly notified = new Set<string>();

  constructor() {
    this.ui.applyTheme();
    if (!this.auth.user()) this.auth.restore().subscribe({ error: () => undefined });
    effect(() => { this.ui.changed(); this.loadReminders(); });
    const t = setInterval(() => this.loadReminders(), 60_000);
    inject(DestroyRef).onDestroy(() => clearInterval(t));
  }

  private loadReminders() {
    this.api.reminders().subscribe({
      next: (r) => {
        this.reminders.set(r);
        for (const i of r.dueSoon) {
          if (!this.notified.has(i.key)) { this.notified.add(i.key); this.ui.browserNotify('Task due soon', `${i.title} — due ${fmtTime(i.dueAt)}`); }
        }
      },
      error: () => undefined,
    });
  }
  quickAdd() { this.form.open(); }
  async enableBrowser() {
    const r = await this.ui.requestBrowserPermission();
    this.ui.info(r === 'granted' ? 'Browser notifications enabled' : 'Browser notifications were not allowed');
  }
}
