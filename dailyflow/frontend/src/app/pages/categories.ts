import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { errorMessage, fieldErrors } from '../core/auth';
import { Category } from '../core/models';
import { UiService } from '../core/ui.service';

/** Icon names are stored as plain slugs on the server; the UI maps them to emoji. */
export const ICONS: Record<string, string> = {
  tag: '🏷️', 'graduation-cap': '🎓', building: '🏢', code: '💻', send: '📨', video: '🎬',
  sprout: '🌱', 'heart-pulse': '❤️', 'message-circle': '💬', book: '📚', star: '⭐', home: '🏠',
};

@Component({
  selector: 'app-categories',
  imports: [ReactiveFormsModule],
  template: `
    <div class="page-head"><h1>Categories</h1></div>
    <form class="card filters" [formGroup]="form" (ngSubmit)="save()" novalidate>
      <h2 class="full">{{ editing() ? 'Edit category' : 'New category' }}</h2>
      <label class="field grow2">Name *
        <input formControlName="name" maxlength="60" autocomplete="off" />
        @if ((form.controls.name.invalid && form.controls.name.touched) || serverErr()['name']) { <span class="err">{{ serverErr()['name'] || 'Name is required' }}</span> }
      </label>
      <label class="field">Color
        <input type="color" formControlName="color" />
      </label>
      <label class="field">Icon
        <select formControlName="icon">@for (k of iconKeys; track k) { <option [value]="k">{{ icons[k] }} {{ k }}</option> }</select>
      </label>
      <button class="btn primary" type="submit" [disabled]="saving()">{{ saving() ? 'Saving…' : editing() ? 'Save changes' : 'Add category' }}</button>
      @if (editing()) { <button type="button" class="btn" (click)="reset()">Cancel</button> }
      @if (formError()) { <p class="err full" role="alert">{{ formError() }}</p> }
    </form>

    @if (loading()) { <div class="card skeleton h200"></div> }
    @else if (error()) { <div class="card"><p class="err">{{ error() }}</p><button class="btn" (click)="load()">Retry</button></div> }
    @else {
      <div class="cat-grid">
        @for (c of categories(); track c.id) {
          <div class="card cat-card" [style.border-top-color]="c.color">
            <div class="row between">
              <strong><span aria-hidden="true">{{ iconOf(c) }}</span> {{ c.name }}</strong>
              <i class="swatch big" [style.background]="c.color"></i>
            </div>
            <p class="muted small">{{ c.taskCount }} task(s)</p>
            <div class="row">
              <button type="button" class="btn small" (click)="edit(c)">Edit</button>
              <button type="button" class="btn small danger-text" (click)="remove(c)">Delete</button>
            </div>
          </div>
        } @empty { <div class="card empty"><h2>No categories</h2><p class="muted">Create one above to organise your tasks.</p></div> }
      </div>
    }`,
})
export class CategoriesPage {
  private readonly api = inject(ApiService);
  private readonly ui = inject(UiService);
  private readonly fb = inject(FormBuilder);

  readonly icons = ICONS;
  readonly iconKeys = Object.keys(ICONS);
  readonly categories = signal<Category[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly saving = signal(false);
  readonly editing = signal<Category | null>(null);
  readonly formError = signal('');
  readonly serverErr = signal<Record<string, string>>({});
  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(60)]],
    color: ['#6366f1'],
    icon: ['tag'],
  });

  constructor() { this.load(); }
  iconOf(c: Category) { return ICONS[c.icon ?? 'tag'] ?? ICONS['tag']; }

  load() {
    this.loading.set(true);
    this.api.categories().subscribe({
      next: (c) => { this.categories.set(c); this.error.set(''); this.loading.set(false); },
      error: (e) => { this.error.set(errorMessage(e)); this.loading.set(false); },
    });
  }
  edit(c: Category) {
    this.editing.set(c);
    this.form.reset({ name: c.name, color: c.color, icon: c.icon ?? 'tag' });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
  reset() { this.editing.set(null); this.formError.set(''); this.serverErr.set({}); this.form.reset({ name: '', color: '#6366f1', icon: 'tag' }); }

  save() {
    this.formError.set(''); this.serverErr.set({});
    if (this.form.invalid || this.saving()) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    const body = { name: v.name.trim(), color: v.color, icon: v.icon };
    const cur = this.editing();
    this.saving.set(true);
    (cur ? this.api.updateCategory(cur.id, body) : this.api.createCategory(body)).subscribe({
      next: (c) => { this.saving.set(false); this.ui.success(cur ? `Updated “${c.name}”` : `Added “${c.name}”`); this.reset(); this.load(); },
      error: (e) => { this.saving.set(false); this.serverErr.set(fieldErrors(e)); this.formError.set(errorMessage(e)); },
    });
  }
  async remove(c: Category) {
    if (!(await this.ui.confirm({ title: 'Delete category?', message: `“${c.name}” will be deleted. Categories that still have tasks cannot be deleted.`, confirmText: 'Delete', danger: true }))) return;
    this.api.deleteCategory(c.id).subscribe({
      next: () => { this.ui.success('Category deleted'); if (this.editing()?.id === c.id) this.reset(); this.load(); },
      error: (e) => this.ui.error(errorMessage(e)),
    });
  }
}
