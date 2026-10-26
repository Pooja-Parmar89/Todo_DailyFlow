import { Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { ApiService } from '../core/api.service';
import { errorMessage } from '../core/auth';
import { CalendarItem } from '../core/models';
import { UiService } from '../core/ui.service';
import { fmtDate, fmtTime } from '../core/util';
import { PriorityBadge } from './badges';
import { TaskFormService } from './task-form';

/** One task or recurring occurrence with its quick actions (complete / reopen / skip / reschedule / edit / delete). */
@Component({
  selector: 'app-cal-row',
  imports: [PriorityBadge, FormsModule],
  template: `
    <div class="item" [class.done]="done()" [class.skipped]="item().occurrenceStatus === 'SKIPPED'">
      <button type="button" class="check" [class.on]="done()" [disabled]="busy() || item().occurrenceStatus === 'SKIPPED'" (click)="toggle()"
              [attr.aria-label]="done() ? 'Mark as not done' : 'Mark as done'" [attr.aria-pressed]="done()">{{ done() ? '✓' : '' }}</button>
      <div class="grow">
        <div class="title">
          <span class="swatch" [style.background]="item().categoryColor || 'var(--border)'"></span>
          {{ item().title }}
          @if (item().recurring) { <span class="chip" title="Recurring task">↻ repeats</span> }
          @if (item().occurrenceStatus === 'SKIPPED') { <span class="chip">skipped</span> }
          @if (item().occurrenceStatus === 'RESCHEDULED') { <span class="chip">moved from {{ date(item().originalDate!) }}</span> }
          @if (item().overdue && !done()) { <span class="chip danger">Overdue</span> }
        </div>
        <div class="meta muted">
          {{ date(item().dueAt) }} · {{ time(item().dueAt) }}
          @if (item().categoryName) { · {{ item().categoryName }} }
        </div>
        @if (rescheduling()) {
          <div class="row inline-edit">
            <input type="date" [(ngModel)]="newDate" aria-label="New date" />
            <button type="button" class="btn small primary" [disabled]="!newDate || busy()" (click)="reschedule()">Move</button>
            <button type="button" class="btn small" (click)="rescheduling.set(false)">Cancel</button>
          </div>
        }
      </div>
      <app-priority [value]="item().priority" />
      <div class="row actions">
        @if (item().recurring) {
          @if (item().occurrenceStatus === 'SKIPPED' || item().occurrenceStatus === 'RESCHEDULED' || item().occurrenceStatus === 'COMPLETED') {
            <button type="button" class="btn small" [disabled]="busy()" (click)="reset()">Reset</button>
          } @else {
            <button type="button" class="btn small" [disabled]="busy()" (click)="skip()">Skip</button>
            <button type="button" class="btn small" [disabled]="busy()" (click)="rescheduling.set(true)">Reschedule</button>
          }
        }
        <button type="button" class="btn small" (click)="edit()">{{ item().recurring ? 'Edit series' : 'Edit' }}</button>
        <button type="button" class="btn small danger-text" (click)="remove()">Delete</button>
      </div>
    </div>`,
})
export class CalendarRow {
  readonly item = input.required<CalendarItem>();
  readonly changed = output<void>();
  private readonly api = inject(ApiService);
  private readonly ui = inject(UiService);
  private readonly form = inject(TaskFormService);

  readonly busy = signal(false);
  readonly rescheduling = signal(false);
  newDate = '';
  date = fmtDate;
  time = fmtTime;

  done() { return this.item().status === 'COMPLETED'; }
  /** Key used by the occurrence endpoints: the date the occurrence originally belonged to. */
  private key() { const i = this.item(); return (i.originalDate ?? i.occurrenceDate)!; }

  private run(call: Observable<unknown>, msg: string) {
    if (this.busy()) return;
    this.busy.set(true);
    call.subscribe({
      next: () => { this.busy.set(false); this.rescheduling.set(false); this.ui.success(msg); this.ui.notifyChange(); this.changed.emit(); },
      error: (e: unknown) => { this.busy.set(false); this.ui.error(errorMessage(e)); },
    });
  }
  toggle() {
    const i = this.item();
    if (i.recurring) {
      this.run(this.done() ? this.api.resetOccurrence(i.taskId, this.key()) : this.api.completeOccurrence(i.taskId, this.key()),
        this.done() ? 'Occurrence reopened' : 'Occurrence completed');
    } else {
      this.run(this.done() ? this.api.reopenTask(i.taskId) : this.api.completeTask(i.taskId), this.done() ? 'Task reopened' : 'Task completed');
    }
  }
  skip() { this.run(this.api.skipOccurrence(this.item().taskId, this.key()), 'Occurrence skipped'); }
  reset() { this.run(this.api.resetOccurrence(this.item().taskId, this.key()), 'Occurrence reset'); }
  reschedule() { this.run(this.api.rescheduleOccurrence(this.item().taskId, this.key(), this.newDate), 'Occurrence rescheduled'); }

  edit() {
    this.api.getTask(this.item().taskId).subscribe({
      next: async (t) => { if (await this.form.open(t)) this.changed.emit(); },
      error: (e: unknown) => this.ui.error(errorMessage(e)),
    });
  }
  async remove() {
    const i = this.item();
    const ok = await this.ui.confirm({
      title: 'Delete task?',
      message: i.recurring ? `“${i.title}” and all its future occurrences will be permanently deleted.` : `“${i.title}” will be permanently deleted.`,
      confirmText: 'Delete', danger: true,
    });
    if (!ok) return;
    this.api.deleteTask(i.taskId).subscribe({
      next: () => { this.ui.success('Task deleted'); this.ui.notifyChange(); this.changed.emit(); },
      error: (e: unknown) => this.ui.error(errorMessage(e)),
    });
  }
}
