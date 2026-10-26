import { Component, computed, input } from '@angular/core';
import { DailyCount } from '../core/models';
import { parseIsoDate } from '../core/util';

/** Dependency-free SVG grouped bar chart: completed vs created per day. */
@Component({
  selector: 'app-daily-chart',
  template: `
    <svg [attr.viewBox]="'0 0 ' + w + ' ' + h" class="chart" role="img" [attr.aria-label]="label()">
      @for (t of ticks(); track t) {
        <line [attr.x1]="left" [attr.x2]="w" [attr.y1]="y(t)" [attr.y2]="y(t)" class="grid" />
        <text [attr.x]="left - 6" [attr.y]="y(t) + 4" class="axis" text-anchor="end">{{ t }}</text>
      }
      @for (b of bars(); track b.date) {
        <rect [attr.x]="b.x" [attr.y]="y(b.completed)" [attr.width]="b.bw" [attr.height]="base - y(b.completed)" class="bar-done" rx="2"><title>{{ b.date }}: {{ b.completed }} completed</title></rect>
        <rect [attr.x]="b.x + b.bw + 1" [attr.y]="y(b.created)" [attr.width]="b.bw" [attr.height]="base - y(b.created)" class="bar-new" rx="2"><title>{{ b.date }}: {{ b.created }} created</title></rect>
        @if (b.showLabel) { <text [attr.x]="b.x + b.bw" [attr.y]="h - 6" class="axis" text-anchor="middle">{{ b.label }}</text> }
      }
    </svg>
    <div class="legend"><span><i class="sw done"></i>Completed</span><span><i class="sw new"></i>Created</span></div>`,
})
export class DailyChart {
  readonly data = input.required<DailyCount[]>();
  readonly w = 640; readonly h = 220; readonly left = 30; readonly base = 190; private top = 12;
  readonly max = computed(() => Math.max(4, ...this.data().flatMap((d) => [d.completed, d.created])));
  readonly ticks = computed(() => { const m = this.max(); const step = Math.ceil(m / 4); return [0, step, step * 2, step * 3, step * 4].filter((t) => t <= step * 4); });
  y(v: number) { const top = Math.ceil(this.max() / 4) * 4; return this.base - (v / top) * (this.base - this.top); }
  readonly label = computed(() => `Bar chart of tasks completed and created per day, ${this.data().length} days`);
  readonly bars = computed(() => {
    const d = this.data(); const n = Math.max(d.length, 1);
    const slot = (this.w - this.left) / n; const bw = Math.max(2, Math.min(18, slot / 2 - 2));
    const every = n > 14 ? Math.ceil(n / 8) : 1;
    return d.map((x, i) => {
      const dt = parseIsoDate(x.date);
      return { ...x, bw, x: this.left + i * slot + (slot - 2 * bw - 1) / 2, showLabel: i % every === 0,
        label: n <= 7 ? dt.toLocaleDateString(undefined, { weekday: 'short' }) : `${dt.getDate()}/${dt.getMonth() + 1}` };
    });
  });
}
