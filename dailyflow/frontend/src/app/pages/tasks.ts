import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { debounceTime } from 'rxjs';
import { ApiService } from '../core/api.service';
import { errorMessage } from '../core/auth';
import { Category, PRIORITIES, PageResponse, STATUSES, STATUS_LABEL, Task, TaskQuery, TaskStatus } from '../core/models';
import { UiService } from '../core/ui.service';
import { fmtDateTime } from '../core/util';
import { PriorityBadge } from '../shared/badges';
import { TaskFormService } from '../shared/task-form';

type Mode = 'all' | 'completed' | 'overdue';

/** One page component serves "All tasks", "Completed" and "Overdue"; the route's data.mode pins the base filter. */
@Component({
  selector: 'app-tasks',
  imports: [ReactiveFormsModule, PriorityBadge],
  template: `
    <div class="page-head">
      <h1>{{ title() }}</h1>
      <button type="button" class="btn primary" (click)="add()">＋ Add task</button>
    </div>

    <form class="card filters" [formGroup]="f" (ngSubmit)="$event.preventDefault()" role="search">
      <label class="field grow2">Search
        <input type="search" formControlName="q" placeholder="Title or description" />
      </label>
      <label class="field">Category
        <select formControlName="categoryId">
          <option [ngValue]="null">All</option>
          @for (c of categories(); track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }
        </select>
      </label>
      <label class="field">Priority
        <select formControlName="priority">
          <option value="">All</option>
          @for (p of priorities; track p) { <option [value]="p">{{ p }}</option> }
        </select>
      </label>
      @if (kind() === 'all') {
        <label class="field">Status
          <select formControlName="status">
            <option value="">All</option>
            @for (s of statuses; track s) { <option [value]="s">{{ label[s] }}</option> }
          </select>
        </label>
      }
      <label class="field">Due from<input type="date" formControlName="dueFrom" /></label>
      <label class="field">Due to<input type="date" formControlName="dueTo" /></label>
      <label class="field">Sort by
        <select formControlName="sort">
          <option value="dueAt">Due date</option><option value="createdAt">Created</option>
          <option value="priority">Priority</option><option value="title">Title</option>
        </select>
      </label>
      <label class="field">Order
        <select formControlName="direction"><option value="asc">Ascending</option><option value="desc">Descending</option></select>
      </label>
      <button type="button" class="btn" (click)="clear()">Clear filters</button>
    </form>

    @if (loading() && !page()) {
      <div class="card skeleton h200"></div>
    } @else if (error()) {
      <div class="card"><p class="err" role="alert">{{ error() }}</p><button class="btn" (click)="load()">Retry</button></div>
    } @else if (page(); as p) {
      @if (p.content.length === 0) {
        <div class="card empty">
          <h2>{{ filtered() ? 'No tasks match your filters' : kind() === 'completed' ? 'Nothing completed yet' : kind() === 'overdue' ? 'No overdue tasks 🎉' : 'No tasks yet' }}</h2>
          @if (filtered()) { <button class="btn" (click)="clear()">Clear filters</button> }
          @else if (kind() === 'all') { <button class="btn primary" (click)="add()">Create your first task</button> }
        </div>
      } @else {
        <div class="card table-wrap" [class.busy]="loading()">
          <table class="tasks">
            <thead><tr><th class="w-check"></th><th>Task</th><th>Category</th><th>Priority</th><th>Status</th><th>Due</th><th class="w-actions">Actions</th></tr></thead>
            <tbody>
              @for (t of p.content; track t.id) {
                <tr [class.done]="t.status === 'COMPLETED'">
                  <td data-label="Done"><button type="button" class="check" [class.on]="t.status === 'COMPLETED'" [disabled]="busyId() === t.id" (click)="toggle(t)" [attr.aria-label]="t.status === 'COMPLETED' ? 'Reopen ' + t.title : 'Complete ' + t.title" [attr.aria-pressed]="t.status === 'COMPLETED'">{{ t.status === 'COMPLETED' ? '✓' : '' }}</button></td>
                  <td data-label="Task"><div class="title">{{ t.title }} @if (t.recurrenceType !== 'NONE') { <span class="chip">↻ {{ t.recurrenceType.toLowerCase() }}</span> }</div>
                    @if (t.description) { <div class="muted small clamp">{{ t.description }}</div> }</td>
                  <td data-label="Category">@if (t.category) { <span class="cat"><i class="swatch" [style.background]="t.category.color"></i>{{ t.category.name }}</span> } @else { <span class="muted">—</span> }</td>
                  <td data-label="Priority"><app-priority [value]="t.priority" /></td>
                  <td data-label="Status">
                    <select class="status-select" [value]="t.status" (change)="move(t, $any($event.target).value)" [attr.aria-label]="'Status of ' + t.title" [disabled]="busyId() === t.id">
                      @for (s of statuses; track s) { <option [value]="s">{{ label[s] }}</option> }
                    </select>
                  </td>
                  <td data-label="Due">{{ fmt(t.dueAt) }} @if (t.overdue) { <span class="chip danger">Overdue</span> }</td>
                  <td data-label="Actions" class="actions"><button type="button" class="btn small" (click)="edit(t)">Edit</button><button type="button" class="btn small danger-text" (click)="remove(t)">Delete</button></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <div class="pager">
          <span class="muted">{{ p.totalElements }} task(s) · page {{ p.page + 1 }} of {{ p.totalPages || 1 }}</span>
          <div class="row">
            <select [value]="size()" (change)="setSize($any($event.target).value)" aria-label="Rows per page">
              @for (n of [10, 20, 50]; track n) { <option [value]="n">{{ n }} / page</option> }
            </select>
            <button type="button" class="btn small" [disabled]="p.page === 0" (click)="go(p.page - 1)">Previous</button>
            <button type="button" class="btn small" [disabled]="p.page + 1 >= p.totalPages" (click)="go(p.page + 1)">Next</button>
          </div>
        </div>
      }
    }`,
})
export class TasksPage {
  private readonly api = inject(ApiService);
  private readonly ui = inject(UiService);
  private readonly form = inject(TaskFormService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);

  readonly kind = computed(() => this.mode().mode);
  readonly mode = toSignal(inject(ActivatedRoute).data, { initialValue: { mode: 'all' } }) as unknown as () => { mode: Mode };
  readonly priorities = PRIORITIES;
  readonly statuses = STATUSES;
  readonly label = STATUS_LABEL;
  readonly categories = signal<Category[]>([]);
  readonly page = signal<PageResponse<Task> | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly busyId = signal<number | null>(null);
  readonly size = signal(10);
  private pageIndex = 0;
  private reqSeq = 0;
  fmt = fmtDateTime;

  readonly f = this.fb.group({
    q: [''], categoryId: [null as number | null], priority: [''], status: [''], dueFrom: [''], dueTo: [''],
    sort: ['dueAt'], direction: ['asc'],
  });

  constructor() {
    this.api.categories().subscribe({ next: (c) => this.categories.set(c) });
    this.f.valueChanges.pipe(debounceTime(250), takeUntilDestroyed()).subscribe(() => { this.pageIndex = 0; this.load(); });
    effect(() => { this.mode(); this.ui.changed(); this.load(); });
  }

  title() { const m = this.mode().mode; return m === 'completed' ? 'Completed tasks' : m === 'overdue' ? 'Overdue tasks' : 'All tasks'; }
  filtered() { const v = this.f.value; return !!(v.q || v.categoryId || v.priority || v.status || v.dueFrom || v.dueTo); }

  private query(): TaskQuery {
    const v = this.f.getRawValue();
    const m = this.mode().mode;
    const q: TaskQuery = {
      q: v.q?.trim() || undefined, categoryId: v.categoryId, dueFrom: v.dueFrom || undefined, dueTo: v.dueTo || undefined,
      priority: v.priority ? [v.priority as never] : undefined, page: this.pageIndex, size: this.size(),
      sort: v.sort ?? 'dueAt', direction: (v.direction as 'asc' | 'desc') ?? 'asc',
    };
    if (m === 'completed') q.status = ['COMPLETED'];
    else if (m === 'overdue') q.overdue = true;
    else if (v.status) q.status = [v.status as TaskStatus];
    return q;
  }

  load() {
    const seq = ++this.reqSeq;
    this.loading.set(true);
    this.api.listTasks(this.query()).subscribe({
      next: (p) => {
        if (seq !== this.reqSeq) return; // ignore out-of-order responses
        // If the last item on the last page was removed, step back one page.
        if (p.content.length === 0 && p.page > 0) { this.pageIndex = Math.max(p.totalPages - 1, 0); this.load(); return; }
        this.page.set(p); this.error.set(''); this.loading.set(false);
      },
      error: (e) => { if (seq === this.reqSeq) { this.error.set(errorMessage(e)); this.loading.set(false); } },
    });
  }
  go(i: number) { this.pageIndex = i; this.load(); }
  setSize(v: string) { this.size.set(Number(v)); this.pageIndex = 0; this.load(); }
  clear() { this.f.reset({ q: '', categoryId: null, priority: '', status: '', dueFrom: '', dueTo: '', sort: 'dueAt', direction: 'asc' }); }
  add() { this.form.open(); }
  edit(t: Task) { this.form.open(t); }

  private act(t: Task, call: ReturnType<ApiService['completeTask']>, msg: string) {
    this.busyId.set(t.id);
    call.subscribe({
      next: () => { this.busyId.set(null); this.ui.success(msg); this.ui.notifyChange(); },
      error: (e) => { this.busyId.set(null); this.ui.error(errorMessage(e)); this.load(); },
    });
  }
  toggle(t: Task) {
    if (t.status === 'COMPLETED') this.act(t, this.api.reopenTask(t.id), `Reopened “${t.title}”`);
    else this.act(t, this.api.completeTask(t.id), `Completed “${t.title}”`);
  }
  move(t: Task, status: TaskStatus) {
    if (status === t.status) return;
    this.act(t, this.api.setStatus(t.id, status), `Moved “${t.title}” to ${STATUS_LABEL[status]}`);
  }
  async remove(t: Task) {
    if (!(await this.ui.confirm({ title: 'Delete task?', message: `“${t.title}” will be permanently deleted.`, confirmText: 'Delete', danger: true }))) return;
    this.busyId.set(t.id);
    this.api.deleteTask(t.id).subscribe({
      next: () => { this.busyId.set(null); this.ui.success('Task deleted'); this.ui.notifyChange(); },
      error: (e) => { this.busyId.set(null); this.ui.error(errorMessage(e)); },
    });
  }
}
