import { Component, inject } from '@angular/core';
import { UiService } from '../core/ui.service';

@Component({
  selector: 'app-toasts',
  template: `
    <div class="toasts" aria-live="polite" role="status">
      @for (t of ui.toasts(); track t.id) {
        <div class="toast {{ t.kind }}">
          <span>{{ t.text }}</span>
          <button type="button" class="icon-btn" (click)="ui.dismiss(t.id)" aria-label="Dismiss message">✕</button>
        </div>
      }
    </div>`,
})
export class ToastHost { readonly ui = inject(UiService); }

@Component({
  selector: 'app-confirm',
  template: `
    @if (ui.confirmState(); as c) {
      <div class="backdrop" (click)="ui.answer(false)">
        <div class="dialog" role="alertdialog" aria-modal="true" aria-labelledby="cfm-t" (click)="$event.stopPropagation()" (keydown.escape)="ui.answer(false)">
          <h2 id="cfm-t">{{ c.title }}</h2>
          <p class="muted">{{ c.message }}</p>
          <div class="row end">
            <button type="button" class="btn" (click)="ui.answer(false)">Cancel</button>
            <button type="button" class="btn {{ c.danger ? 'danger' : 'primary' }}" (click)="ui.answer(true)" autofocus>{{ c.confirmText ?? 'Confirm' }}</button>
          </div>
        </div>
      </div>
    }`,
})
export class ConfirmDialog { readonly ui = inject(UiService); }
