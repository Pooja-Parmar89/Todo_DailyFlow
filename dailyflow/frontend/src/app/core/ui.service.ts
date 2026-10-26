import { Injectable, signal } from '@angular/core';

export interface Toast { id: number; kind: 'success' | 'error' | 'info'; text: string }
export interface ConfirmOptions { title: string; message: string; confirmText?: string; danger?: boolean }

/** In-app messages (toasts), the confirmation dialog, theme and optional browser notifications. */
@Injectable({ providedIn: 'root' })
export class UiService {
  readonly toasts = signal<Toast[]>([]);
  readonly confirmState = signal<(ConfirmOptions & { resolve: (v: boolean) => void }) | null>(null);
  readonly theme = signal<'light' | 'dark'>(this.initialTheme());
  /** Bumped after every task mutation so lists, counters and the reminder bell can refresh. */
  readonly changed = signal(0);
  private seq = 0;

  success(text: string) { this.push('success', text); }
  error(text: string) { this.push('error', text, 7000); }
  info(text: string) { this.push('info', text); }
  notifyChange() { this.changed.update((n) => n + 1); }

  private push(kind: Toast['kind'], text: string, ms = 4000) {
    const id = ++this.seq;
    this.toasts.update((t) => [...t, { id, kind, text }]);
    setTimeout(() => this.dismiss(id), ms);
  }
  dismiss(id: number) { this.toasts.update((t) => t.filter((x) => x.id !== id)); }

  confirm(opts: ConfirmOptions): Promise<boolean> {
    return new Promise((resolve) => this.confirmState.set({ ...opts, resolve }));
  }
  answer(v: boolean) {
    const s = this.confirmState();
    this.confirmState.set(null);
    s?.resolve(v);
  }

  toggleTheme() {
    const next = this.theme() === 'dark' ? 'light' : 'dark';
    this.theme.set(next);
    this.applyTheme();
    try { localStorage.setItem('dailyflow.theme', next); } catch { /* ignore */ }
  }
  applyTheme() { document.documentElement.setAttribute('data-theme', this.theme()); }
  private initialTheme(): 'light' | 'dark' {
    try {
      const saved = localStorage.getItem('dailyflow.theme');
      if (saved === 'light' || saved === 'dark') return saved;
    } catch { /* ignore */ }
    return typeof matchMedia === 'function' && matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  // ---- browser notifications (opt-in, only while the app is open in a browser tab)
  get browserNotificationsSupported() { return typeof Notification !== 'undefined'; }
  get browserPermission(): NotificationPermission | 'unsupported' {
    return this.browserNotificationsSupported ? Notification.permission : 'unsupported';
  }
  async requestBrowserPermission() {
    if (!this.browserNotificationsSupported) return 'unsupported';
    return Notification.requestPermission();
  }
  browserNotify(title: string, body: string) {
    if (this.browserPermission === 'granted') new Notification(title, { body });
  }
}
