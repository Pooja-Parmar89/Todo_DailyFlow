import { Component, computed, effect, inject, signal } from '@angular/core';
import { ApiService } from '../core/api.service';
import { errorMessage } from '../core/auth';
import { CalendarItem } from '../core/models';
import { UiService } from '../core/ui.service';
import { addDays, fmtDate, isoDate, startOfWeek } from '../core/util';
import { CalendarRow } from '../shared/cal-row';
import { TaskFormService } from '../shared/task-form';

interface Cell { date: string; day: number; inMonth: boolean; today: boolean; items: CalendarItem[] }

/** Monthly calendar. Recurring occurrences come from the same endpoint, so they appear on every due day. */
@Component({
  selector: 'app-calendar',
  imports: [CalendarRow],
  template: `
    <div class="page-head">
      <h1>Calendar</h1>
      <button type="button" class="btn primary" (click)="form.open()">＋ Add task</button>
    </div>
    <div class="card">
      <div class="row between cal-nav">
        <button type="button" class="btn small" (click)="shift(-1)" aria-label="Previous month">‹ Prev</button>
        <h2>{{ monthLabel() }}</h2>
        <div class="row">
          <button type="button" class="btn small" (click)="goToday()">Today</button>
          <button type="button" class="btn small" (click)="shift(1)" aria-label="Next month">Next ›</button>
        </div>
      </div>
      @if (error()) { <p class="err" role="alert">{{ error() }}</p> }
      <div class="cal-grid" [class.busy]="loading()" role="grid" [attr.aria-label]="monthLabel()">
        @for (w of weekdays; track w) { <div class="cal-head" role="columnheader">{{ w }}</div> }
        @for (c of cells(); track c.date) {
          <button type="button" class="cal-cell" role="gridcell" [class.out]="!c.inMonth" [class.today]="c.today" [class.sel]="selected() === c.date"
                  (click)="selected.set(c.date)" [attr.aria-label]="label(c)">
            <span class="d">{{ c.day }}</span>
            @for (i of c.items.slice(0, 3); track i.key) {
              <span class="pill" [class.done]="i.status === 'COMPLETED'" [style.border-left-color]="i.categoryColor || 'var(--border)'">{{ i.title }}</span>
            }
            @if (c.items.length > 3) { <span class="more">+{{ c.items.length - 3 }} more</span> }
          </button>
        }
      </div>
    </div>
    <div class="card">
      <h2>{{ fmt(selected()) }} <span class="muted small">({{ selectedItems().length }})</span></h2>
      @for (i of selectedItems(); track i.key) { <app-cal-row [item]="i" /> }
      @empty { <p class="muted">No tasks on this day.</p> }
    </div>`,
})
export class CalendarPage {
  private readonly api = inject(ApiService);
  private readonly ui = inject(UiService);
  readonly form = inject(TaskFormService);

  readonly weekdays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  readonly cursor = signal(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  readonly selected = signal(isoDate(new Date()));
  readonly items = signal<CalendarItem[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  fmt = fmtDate;

  readonly monthLabel = computed(() => this.cursor().toLocaleDateString(undefined, { month: 'long', year: 'numeric' }));
  private readonly range = computed(() => {
    const first = startOfWeek(this.cursor());
    return { from: first, to: addDays(first, 41) };
  });
  readonly cells = computed<Cell[]>(() => {
    const { from } = this.range();
    const byDay = new Map<string, CalendarItem[]>();
    for (const i of this.items()) { const k = i.dueAt.slice(0, 10); (byDay.get(k) ?? byDay.set(k, []).get(k)!).push(i); }
    const today = isoDate(new Date());
    return Array.from({ length: 42 }, (_, n) => {
      const d = addDays(from, n);
      const date = isoDate(d);
      return { date, day: d.getDate(), inMonth: d.getMonth() === this.cursor().getMonth(), today: date === today, items: byDay.get(date) ?? [] };
    });
  });
  readonly selectedItems = computed(() => this.items().filter((i) => i.dueAt.slice(0, 10) === this.selected()));

  constructor() {
    effect(() => { this.range(); this.ui.changed(); this.load(); });
  }

  label(c: Cell) { return `${fmtDate(c.date)}, ${c.items.length} task(s)`; }
  shift(n: number) { const c = this.cursor(); this.cursor.set(new Date(c.getFullYear(), c.getMonth() + n, 1)); }
  goToday() {
    const t = new Date();
    this.cursor.set(new Date(t.getFullYear(), t.getMonth(), 1));
    this.selected.set(isoDate(t));
  }
  load() {
    const { from, to } = this.range();
    this.loading.set(true);
    this.api.calendar(isoDate(from), isoDate(to)).subscribe({
      next: (r) => { this.items.set(r); this.error.set(''); this.loading.set(false); },
      error: (e) => { this.error.set(errorMessage(e)); this.loading.set(false); },
    });
  }
}
