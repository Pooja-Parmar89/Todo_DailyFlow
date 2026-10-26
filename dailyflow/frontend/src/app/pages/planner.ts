import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { errorMessage } from '../core/auth';
import { CalendarItem, Category, PRIORITIES, STATUSES, STATUS_LABEL } from '../core/models';
import { UiService } from '../core/ui.service';
import { addDays, fmtDate, isoDate, startOfWeek } from '../core/util';
import { CalendarRow } from '../shared/cal-row';
import { TaskFormService } from '../shared/task-form';

type View = 'today' | 'tomorrow' | 'week';

/** Today / Tomorrow / This week, built from the calendar endpoint so recurring occurrences are included. */
@Component({
  selector: 'app-planner',
  imports: [FormsModule, CalendarRow],
  template: `
    <div class="page-head">
      <h1>Today &amp; upcoming</h1>
      <button type="button" class="btn primary" (click)="add()">＋ Add task</button>
    </div>
    <div class="tabs" role="tablist">
      @for (v of views; track v.id) {
        <button type="button" role="tab" [attr.aria-selected]="view() === v.id" [class.active]="view() === v.id" (click)="setView(v.id)">{{ v.label }}</button>
      }
    </div>
    <div class="card filters">
      <label class="field">Category
        <select [ngModel]="cat()" (ngModelChange)="cat.set($event)"><option [ngValue]="null">All</option>@for (c of categories(); track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }</select>
      </label>
      <label class="field">Priority
        <select [ngModel]="prio()" (ngModelChange)="prio.set($event)"><option value="">All</option>@for (p of priorities; track p) { <option [value]="p">{{ p }}</option> }</select>
      </label>
      <label class="field">Status
        <select [ngModel]="stat()" (ngModelChange)="stat.set($event)"><option value="">All</option>@for (s of statuses; track s) { <option [value]="s">{{ label[s] }}</option> }</select>
      </label>
      <button type="button" class="btn" (click)="cat.set(null); prio.set(''); stat.set('')">Clear filters</button>
    </div>

    @if (error()) { <div class="card"><p class="err">{{ error() }}</p><button class="btn" (click)="load()">Retry</button></div> }
    @else if (loading() && !items().length) { <div class="card skeleton h200"></div> }
    @else {
      @if (overdue().length && view() !== 'week') {
        <div class="card"><h2 class="danger-text">Overdue ({{ overdue().length }})</h2>@for (i of overdue(); track i.key) { <app-cal-row [item]="i" /> }</div>
      }
      @for (g of groups(); track g.date) {
        <div class="card">
          <h2>{{ heading(g.date) }} <span class="muted small">({{ g.items.length }})</span></h2>
          @for (i of g.items; track i.key) { <app-cal-row [item]="i" /> }
        </div>
      } @empty {
        <div class="card empty"><h2>Nothing scheduled</h2><p class="muted">No tasks match for this period.</p><button class="btn primary" (click)="add()">Add a task</button></div>
      }
    }`,
})
export class PlannerPage {
  private readonly api = inject(ApiService);
  private readonly ui = inject(UiService);
  private readonly form = inject(TaskFormService);

  readonly views: { id: View; label: string }[] = [{ id: 'today', label: 'Today' }, { id: 'tomorrow', label: 'Tomorrow' }, { id: 'week', label: 'This week' }];
  readonly priorities = PRIORITIES; readonly statuses = STATUSES; readonly label = STATUS_LABEL;
  readonly view = signal<View>('today');
  readonly cat = signal<number | null>(null);
  readonly prio = signal('');
  readonly stat = signal('');
  readonly categories = signal<Category[]>([]);
  readonly all = signal<CalendarItem[]>([]);
  readonly overdueAll = signal<CalendarItem[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');

  constructor() {
    this.api.categories().subscribe({ next: (c) => this.categories.set(c) });
    effect(() => { this.view(); this.ui.changed(); this.load(); });
  }

  private match(i: CalendarItem) {
    return (this.cat() === null || i.categoryId === this.cat()) && (!this.prio() || i.priority === this.prio()) && (!this.stat() || i.status === this.stat());
  }
  items() { return this.all().filter((i) => this.match(i)); }
  overdue() { return this.overdueAll().filter((i) => this.match(i)); }
  groups() {
    const m = new Map<string, CalendarItem[]>();
    for (const i of this.items()) { const k = i.dueAt.slice(0, 10); (m.get(k) ?? m.set(k, []).get(k)!).push(i); }
    return [...m.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([date, items]) => ({ date, items }));
  }
  heading(date: string) {
    const t = isoDate(new Date());
    return date === t ? `Today · ${fmtDate(date)}` : date === isoDate(addDays(new Date(), 1)) ? `Tomorrow · ${fmtDate(date)}` : fmtDate(date);
  }
  setView(v: View) { this.view.set(v); }
  add() { this.form.open(); }

  load() {
    const today = new Date();
    let from = today, to = today;
    if (this.view() === 'tomorrow') { from = to = addDays(today, 1); }
    if (this.view() === 'week') { from = startOfWeek(today); to = addDays(from, 6); }
    this.loading.set(true);
    this.api.calendar(isoDate(from), isoDate(to)).subscribe({
      next: (r) => { this.all.set(r); this.error.set(''); this.loading.set(false); },
      error: (e) => { this.error.set(errorMessage(e)); this.loading.set(false); },
    });
    // Overdue strip: the last 30 days up to yesterday, only unfinished items.
    this.api.calendar(isoDate(addDays(today, -30)), isoDate(addDays(today, -1))).subscribe({
      next: (r) => this.overdueAll.set(r.filter((i) => i.overdue && i.status !== 'COMPLETED' && i.occurrenceStatus !== 'SKIPPED')),
      error: () => undefined,
    });
  }
}
