import { Component, effect, inject, signal } from '@angular/core';
import { ApiService } from '../core/api.service';
import { errorMessage } from '../core/auth';
import { NamedCount, ProductivityReport } from '../core/models';
import { UiService } from '../core/ui.service';
import { DailyChart } from '../shared/chart';

@Component({
  selector: 'app-reports',
  imports: [DailyChart],
  template: `
    <div class="page-head">
      <h1>Productivity reports</h1>
      <div class="tabs" role="tablist" aria-label="Report range">
        @for (d of ranges; track d) {
          <button type="button" role="tab" [attr.aria-selected]="days() === d" [class.active]="days() === d" (click)="days.set(d)">Last {{ d }} days</button>
        }
      </div>
    </div>
    @if (error()) { <div class="card"><p class="err">{{ error() }}</p><button class="btn" (click)="load()">Retry</button></div> }
    @else if (!r()) { <div class="card skeleton h200"></div> }
    @else {
      <div class="stats">
        <div class="stat card"><span class="num ok">{{ r()!.totalCompleted }}</span><span class="lbl">Completed</span></div>
        <div class="stat card"><span class="num">{{ r()!.totalCreated }}</span><span class="lbl">Created</span></div>
        <div class="stat card"><span class="num info">{{ r()!.averageCompletedPerDay }}</span><span class="lbl">Avg. completed / day</span></div>
        <div class="stat card"><span class="num">{{ r()!.currentStreakDays }}</span><span class="lbl">Day streak</span></div>
      </div>
      <div class="card"><h2>Daily activity</h2><app-daily-chart [data]="r()!.daily" /></div>
      <div class="cols">
        <div class="card"><h2>Completed by category</h2>
          @for (b of bars(r()!.byCategory); track b.name) {
            <div class="hbar"><span class="hl">{{ b.name }}</span><div class="ht"><div [style.width.%]="b.pct"></div></div><span class="hv">{{ b.count }}</span></div>
          } @empty { <p class="muted">No completed tasks in this period.</p> }
        </div>
        <div class="card"><h2>Completed by priority</h2>
          @for (b of bars(r()!.byPriority); track b.name) {
            <div class="hbar"><span class="hl">{{ b.name }}</span><div class="ht"><div [style.width.%]="b.pct"></div></div><span class="hv">{{ b.count }}</span></div>
          } @empty { <p class="muted">No completed tasks in this period.</p> }
        </div>
      </div>
    }`,
})
export class ReportsPage {
  private readonly api = inject(ApiService);
  private readonly ui = inject(UiService);
  readonly ranges = [7, 30, 90];
  readonly days = signal(30);
  readonly r = signal<ProductivityReport | null>(null);
  readonly error = signal('');

  constructor() { effect(() => { this.days(); this.ui.changed(); this.load(); }); }

  load() {
    this.api.productivity(this.days()).subscribe({
      next: (r) => { this.r.set(r); this.error.set(''); },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }
  bars(list: NamedCount[]) {
    const max = Math.max(1, ...list.map((x) => x.count));
    return list.map((x) => ({ ...x, pct: (x.count / max) * 100 }));
  }
}
