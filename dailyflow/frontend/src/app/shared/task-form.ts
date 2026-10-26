import { Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { Injectable } from '@angular/core';
import { ApiService } from '../core/api.service';
import { errorMessage, fieldErrors } from '../core/auth';
import { Category, PRIORITIES, STATUSES, STATUS_LABEL, Task, TaskRequest } from '../core/models';
import { UiService } from '../core/ui.service';
import { fromInput, toInput } from '../core/util';

interface FormState { task: Task | null; resolve: (t: Task | null) => void }

/** Opens the add/edit modal from anywhere; resolves with the saved task, or null when cancelled. */
@Injectable({ providedIn: 'root' })
export class TaskFormService {
  readonly state = signal<FormState | null>(null);
  open(task: Task | null = null): Promise<Task | null> {
    return new Promise((resolve) => this.state.set({ task, resolve }));
  }
  close(result: Task | null) {
    const s = this.state();
    this.state.set(null);
    s?.resolve(result);
  }
}

function dateRange(group: AbstractControl): ValidationErrors | null {
  const start = group.get('startAt')?.value, due = group.get('dueAt')?.value;
  return start && due && due < start ? { range: true } : null;
}
function recurrenceRule(group: AbstractControl): ValidationErrors | null {
  const type = group.get('recurrenceType')?.value;
  if (type !== 'NONE' && !group.get('dueAt')?.value) return { needsDue: true };
  if (type === 'CUSTOM' && !(group.get('recurrenceInterval')?.value >= 1)) return { needsInterval: true };
  return null;
}

@Component({
  selector: 'app-task-form',
  imports: [ReactiveFormsModule],
  template: `
    @if (svc.state(); as s) {
      <div class="backdrop" (click)="cancel()">
        <form class="dialog wide" [formGroup]="form" (ngSubmit)="save()" role="dialog" aria-modal="true" aria-labelledby="tf-title"
              (click)="$event.stopPropagation()" (keydown.escape)="cancel()" novalidate>
          <h2 id="tf-title">{{ s.task ? 'Edit task' : 'New task' }}</h2>

          <label class="field">Title *
            <input formControlName="title" maxlength="200" autocomplete="off" [attr.aria-invalid]="show('title')" />
            @if (show('title')) { <span class="err">{{ serverErr()['title'] || 'Title is required' }}</span> }
          </label>

          <label class="field">Description
            <textarea formControlName="description" rows="3" maxlength="5000"></textarea>
          </label>

          <div class="grid2">
            <label class="field">Category
              <select formControlName="categoryId">
                <option [ngValue]="null">No category</option>
                @for (c of categories(); track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }
              </select>
            </label>
            <label class="field">Priority *
              <select formControlName="priority">
                @for (p of priorities; track p) { <option [value]="p">{{ p }}</option> }
              </select>
            </label>
            <label class="field">Status
              <select formControlName="status">
                @for (st of statuses; track st) { <option [value]="st">{{ label[st] }}</option> }
              </select>
            </label>
            <span></span>
            <label class="field">Start
              <input type="datetime-local" formControlName="startAt" />
            </label>
            <label class="field">Due
              <input type="datetime-local" formControlName="dueAt" />
            </label>
          </div>
          @if (form.hasError('range') || serverErr()['dueAt']) {
            <p class="err">{{ serverErr()['dueAt'] || 'Due date must not be before the start date' }}</p>
          }

          <fieldset class="field-set">
            <legend>Repeat</legend>
            <div class="grid2">
              <label class="field">Recurrence
                <select formControlName="recurrenceType">
                  <option value="NONE">Does not repeat</option>
                  <option value="DAILY">Daily</option>
                  <option value="WEEKLY">Weekly</option>
                  <option value="CUSTOM">Custom interval (days)</option>
                </select>
              </label>
              @if (form.value.recurrenceType === 'CUSTOM') {
                <label class="field">Every N days
                  <input type="number" min="1" max="365" formControlName="recurrenceInterval" />
                </label>
              }
              @if (form.value.recurrenceType !== 'NONE') {
                <label class="field">Repeat until (optional)
                  <input type="date" formControlName="recurrenceEndDate" />
                </label>
              }
            </div>
            @if (form.hasError('needsDue')) { <p class="err">Recurring tasks need a due date/time.</p> }
            @if (form.hasError('needsInterval')) { <p class="err">Enter how many days between repeats (1–365).</p> }
          </fieldset>

          @if (formError()) { <p class="err" role="alert">{{ formError() }}</p> }

          <div class="row end">
            <button type="button" class="btn" (click)="cancel()">Cancel</button>
            <button type="submit" class="btn primary" [disabled]="saving()">{{ saving() ? 'Saving…' : s.task ? 'Save changes' : 'Create task' }}</button>
          </div>
        </form>
      </div>
    }`,
})
export class TaskFormComponent {
  readonly svc = inject(TaskFormService);
  private readonly api = inject(ApiService);
  private readonly ui = inject(UiService);
  private readonly fb = inject(FormBuilder);

  readonly priorities = PRIORITIES;
  readonly statuses = STATUSES;
  readonly label = STATUS_LABEL;
  readonly categories = signal<Category[]>([]);
  readonly saving = signal(false);
  readonly formError = signal('');
  readonly serverErr = signal<Record<string, string>>({});
  private submitted = false;

  readonly form = this.fb.group(
    {
      title: ['', [Validators.required, Validators.maxLength(200), (c: AbstractControl) => (String(c.value ?? '').trim() ? null : { required: true })]],
      description: [''],
      categoryId: [null as number | null],
      priority: ['MEDIUM'],
      status: ['TODO'],
      startAt: [''],
      dueAt: [''],
      recurrenceType: ['NONE'],
      recurrenceInterval: [null as number | null],
      recurrenceEndDate: [''],
    },
    { validators: [dateRange, recurrenceRule] },
  );

  constructor() {
    effect(() => {
      const s = this.svc.state();
      if (!s) return;
      this.submitted = false;
      this.formError.set('');
      this.serverErr.set({});
      this.saving.set(false);
      const t = s.task;
      // Edit mode pre-fills every stored value; create mode starts from defaults.
      this.form.reset({
        title: t?.title ?? '', description: t?.description ?? '', categoryId: t?.category?.id ?? null,
        priority: t?.priority ?? 'MEDIUM', status: t?.status ?? 'TODO', startAt: toInput(t?.startAt), dueAt: toInput(t?.dueAt),
        recurrenceType: t?.recurrenceType ?? 'NONE', recurrenceInterval: t?.recurrenceInterval ?? null,
        recurrenceEndDate: t?.recurrenceEndDate ?? '',
      });
      this.api.categories().subscribe({ next: (c) => this.categories.set(c) });
    });
  }

  show(name: string) {
    const c = this.form.get(name)!;
    return (c.invalid && (c.touched || this.submitted)) || !!this.serverErr()[name];
  }

  async cancel() {
    if (this.saving()) return;
    if (this.form.dirty && !(await this.ui.confirm({ title: 'Discard changes?', message: 'You have unsaved changes. They will be lost.', confirmText: 'Discard', danger: true }))) return;
    this.svc.close(null);
  }

  save() {
    this.submitted = true;
    this.serverErr.set({});
    this.formError.set('');
    if (this.form.invalid || this.saving()) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    const recurring = v.recurrenceType !== 'NONE';
    const body: TaskRequest = {
      title: v.title!.trim(),
      description: v.description?.trim() || null,
      categoryId: v.categoryId,
      priority: v.priority as TaskRequest['priority'],
      status: v.status as TaskRequest['status'],
      startAt: fromInput(v.startAt),
      dueAt: fromInput(v.dueAt),
      recurrenceType: v.recurrenceType as TaskRequest['recurrenceType'],
      recurrenceInterval: v.recurrenceType === 'CUSTOM' ? Number(v.recurrenceInterval) : null,
      recurrenceEndDate: recurring && v.recurrenceEndDate ? v.recurrenceEndDate : null,
    };
    const editing = this.svc.state()!.task;
    this.saving.set(true);
    (editing ? this.api.updateTask(editing.id, body) : this.api.createTask(body)).subscribe({
      next: (t) => {
        this.ui.success(editing ? `Updated “${t.title}”` : `Created “${t.title}”`);
        this.ui.notifyChange();
        this.saving.set(false);
        this.svc.close(t);
      },
      error: (e) => {
        this.saving.set(false);
        this.serverErr.set(fieldErrors(e));
        this.formError.set(errorMessage(e));
      },
    });
  }
}
