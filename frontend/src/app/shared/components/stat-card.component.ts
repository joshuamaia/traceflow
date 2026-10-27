import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Skeleton } from 'primeng/skeleton';
import { Tooltip } from 'primeng/tooltip';

/** KPI tile: label, value, optional hint. Shows a skeleton while loading. */
@Component({
  selector: 'tf-stat-card',
  imports: [Skeleton, Tooltip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="head">
      <span class="label">{{ label() }}</span>
      <i [class]="icon()" aria-hidden="true" [style.color]="accent()"></i>
    </div>
    @if (loading()) {
      <p-skeleton width="60%" height="2rem" />
      <p-skeleton width="40%" height=".75rem" styleClass="mt-2" />
    } @else {
      <div class="value" [attr.data-testid]="'stat-' + label()">{{ value() }}</div>
      @if (hint()) {
        <div class="hint" [pTooltip]="hintTooltip() ?? undefined" tooltipPosition="bottom">{{ hint() }}</div>
      }
    }
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: .4rem; padding: 1rem 1.1rem; border-radius: 12px;
      background: var(--tf-surface); border: 1px solid var(--tf-border); min-width: 0; }
    .head { display: flex; justify-content: space-between; align-items: center; }
    .label { font-size: .78rem; text-transform: uppercase; letter-spacing: .04em; color: var(--tf-text-muted); font-weight: 600; }
    .head i { font-size: 1rem; }
    .value { font-size: 1.75rem; font-weight: 700; color: var(--tf-text); line-height: 1.15; }
    .hint { font-size: .78rem; color: var(--tf-text-muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  `
})
export class StatCardComponent {
  readonly label = input.required<string>();
  readonly value = input<string | number>('—');
  readonly hint = input<string | null>(null);
  readonly hintTooltip = input<string | null>(null);
  readonly icon = input('pi pi-chart-line');
  readonly accent = input('var(--tf-accent)');
  readonly loading = input(false);
}
