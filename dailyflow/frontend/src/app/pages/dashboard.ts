import { Component, effect, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { errorMessage } from '../core/auth';
import { Activity, Dashboard } from '../core/models';
import { UiService } from '../core/ui.service';
import { fmtDateTime } from '../core/util';
import { PriorityBadge, StatusBadge } from '../shared/badges';
import { DailyChart } from '../shared/chart';
import { CalendarRow } from '../shared/cal-row';
import { TaskFormService } from '../shared/task-form';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, PriorityBadge, StatusBadge, DailyChart, CalendarRow],
  template: `
    <div class="page-head">
      <h1>Dashboard</h1>
      <button type="button" class="btn primary" (click)="add()">＋ Add task</button>
    </div>
    @if (loading() && !d()) {
      <div class="stats">@for (i of [1,2,3,4,5,6]; track i) { <div class="card skeleton h80"></div> }</div>
    } @else if (error()) {
      <div class="card"><p class="err">{{ error() }}</p><button class="btn" (click)="load()">Retry</button></div>
    } @else if (d(); as d) {
      @if (d.total === 0) {
        <div class="card empty"><h2>No tasks yet</h2><p class="muted">Add your first task to start tracking your day.</p><button class="btn primary" (click)="add()">Create a task</button></div>
      }
      <div class="stats">
        <a class="stat card" routerLink="/tasks"><span class="num">{{ d.total }}</span><span class="lbl">Total tasks</span></a>
        <a class="stat card" routerLink="/today"><span class="num">{{ d.dueToday }}</span><span class="lbl">Due today ({{ d.dueTodayCompleted }} done)</span></a>
        <a class="stat card" routerLink="/completed"><span class="num ok">{{ d.completed }}</span><span class="lbl">Completed</span></a>
        <a class="stat card" routerLink="/tasks"><span class="num">{{ d.pending }}</span><span class="lbl">Pending (to do)</span></a>
        <a class="stat card" routerLink="/tasks"><span class="num info">{{ d.inProgress }}</span><span class="lbl">In progress</span></a>
        <a class="stat card" routerLink="/overdue"><span class="num bad">{{ d.overdue }}</span><span class="lbl">Overdue</span></a>
      </div>
      <div class="card">
        <div class="row between"><h2>Completion</h2><strong>{{ d.completionPercentage }}%</strong></div>
        <div class="progress" role="progressbar" [attr.aria-valuenow]="d.completionPercentage" aria-valuemin="0" aria-valuemax="100"><div [style.width.%]="d.completionPercentage"></div></div>
        <p class="muted small">Completed tasks ÷ all tasks. One-off tasks only; recurring series are counted once.</p>
      </div>
      <div class="cols">
        <div class="card"><h2>This week</h2><app-daily-chart [data]="d.weekly" /></div>
        <div class="card">
          <h2>Upcoming deadlines</h2>
          @for (i of d.upcoming; track i.key) { <app-cal-row [item]="i" /> } @empty { <p class="muted">No upcoming deadlines.</p> }
        </div>
      </div>
      <div class="cols">
        <div class="card"><h2>Recently created</h2>
          @for (t of d.recentlyCreated; track t.id) { <div class="mini"><span class="grow">{{ t.title }}</span><app-priority [value]="t.priority" /><app-status [value]="t.status" /></div> } @empty { <p class="muted">Nothing yet.</p> }
        </div>
        <div class="card"><h2>Recently updated</h2>
          @for (t of d.recentlyUpdated; track t.id) { <div class="mini"><span class="grow">{{ t.title }}</span><span class="muted small">{{ fmt(t.updatedAt) }}</span></div> } @empty { <p class="muted">Nothing yet.</p> }
        </div>
      </div>
      <div class="card"><h2>Recent activity</h2>
        @for (a of activity(); track a.id) { <div class="mini"><span class="chip">{{ a.action.replaceAll('_', ' ').toLowerCase() }}</span><span class="grow">{{ a.summary }}</span><span class="muted small">{{ fmt(a.createdAt) }}</span></div> } @empty { <p class="muted">No activity yet.</p> }
      </div>
    }`,
})
export class DashboardPage {
  private readonly api = inject(ApiService);
  private readonly form = inject(TaskFormService);
  private readonly ui = inject(UiService);
  readonly d = signal<Dashboard | null>(null);
  readonly activity = signal<Activity[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  fmt = fmtDateTime;
  constructor() { effect(() => { this.ui.changed(); this.load(); }); }
  load() {
    this.loading.set(true);
    this.api.dashboard().subscribe({
      next: (d) => { this.d.set(d); this.error.set(''); this.loading.set(false); },
      error: (e) => { this.error.set(errorMessage(e)); this.loading.set(false); },
    });
    this.api.activity(0, 8).subscribe({ next: (p) => this.activity.set(p.content), error: () => undefined });
  }
  add() { this.form.open(); }
}
