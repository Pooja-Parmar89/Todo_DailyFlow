import { Component, input } from '@angular/core';
import { Priority, STATUS_LABEL, TaskStatus } from '../core/models';

@Component({
  selector: 'app-priority',
  template: `<span class="badge prio-{{ value().toLowerCase() }}"><span class="dot"></span>{{ value() }}</span>`,
})
export class PriorityBadge { readonly value = input.required<Priority>(); }

@Component({
  selector: 'app-status',
  template: `<span class="badge st-{{ value().toLowerCase() }}">{{ label(value()) }}</span>`,
})
export class StatusBadge {
  readonly value = input.required<TaskStatus>();
  label(s: TaskStatus) { return STATUS_LABEL[s]; }
}
