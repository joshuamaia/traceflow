import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { Tooltip } from 'primeng/tooltip';
import { ServiceWindow, TraceSpan } from '../../core/api/models';
import { formatDuration } from '../../core/util/format';
import { EmptyStateComponent } from './empty-state.component';

interface Row {
  key: string;
  label: string;
  service: string;
  sublabel: string;
  depth: number;
  leftPct: number;
  widthPct: number;
  duration: string;
  status: string;
  observed: boolean;
  async: boolean;
  kind: string | null;
  tooltip: string;
  spanId: string | null;
}

/**
 * Waterfall of spans (or, without span data, observed per-service windows). Bars are positioned on a shared time
 * axis starting at the first event. OBSERVED bars are hatched to distinguish them from REPORTED durations.
 */
@Component({
  selector: 'tf-waterfall',
  imports: [Tooltip, EmptyStateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (rows().length === 0) {
      <tf-empty-state icon="pi pi-align-left" title="No timing information"
        message="The events of this trace have no usable timestamps, so no waterfall can be drawn." compact />
    } @else {
      <div class="axis" aria-hidden="true">
        <span class="axis-label">{{ basis() === 'SPANS' ? 'Span' : 'Service (observed window)' }}</span>
        <div class="ticks">
          @for (tick of ticks(); track tick.pct) {
            <span [style.left.%]="tick.pct">{{ tick.label }}</span>
          }
        </div>
      </div>
      <ul class="rows" role="list">
        @for (row of rows(); track row.key) {
          <li class="row" [class.selected]="selectedService() === row.service"
              [class.dimmed]="selectedService() && selectedService() !== row.service"
              (click)="spanSelected.emit(row.spanId)" tabindex="0" (keydown.enter)="spanSelected.emit(row.spanId)">
            <div class="label" [style.padding-left.rem]="row.depth * 0.9">
              <span class="svc-dot" [style.background]="serviceColor()(row.service)"></span>
              <span class="name" [title]="row.label">{{ row.label }}</span>
              <span class="sub">{{ row.sublabel }}</span>
            </div>
            <div class="track">
              <div class="bar" [class.error]="row.status === 'ERROR'" [class.warning]="row.status === 'WARNING'"
                   [class.observed]="row.observed" [class.async]="row.async"
                   [style.left.%]="row.leftPct" [style.width.%]="row.widthPct"
                   [style.background-color]="serviceColor()(row.service)"
                   [pTooltip]="row.tooltip" tooltipPosition="top" [escape]="true"></div>
              <span class="dur" [style.left.%]="Math.min(row.leftPct + row.widthPct, 86)">
                @if (row.status === 'ERROR') { <i class="pi pi-times-circle" aria-label="error"></i> }
                {{ row.duration }}
              </span>
            </div>
          </li>
        }
      </ul>
      <div class="legend">
        <span><i class="sw reported"></i>Reported duration</span>
        <span><i class="sw observed"></i>Observed (between log timestamps)</span>
        <span><i class="sw err"></i>Error</span>
      </div>
    }
  `,
  styles: `
    :host { display: block; }
    .axis { display: grid; grid-template-columns: minmax(160px, 34%) 1fr; font-size: .7rem; color: var(--tf-text-muted);
      border-bottom: 1px solid var(--tf-border); padding-bottom: .3rem; }
    .axis-label { text-transform: uppercase; letter-spacing: .04em; font-weight: 600; }
    .ticks { position: relative; height: 1rem; }
    .ticks span { position: absolute; transform: translateX(-50%); white-space: nowrap; }
    .ticks span:first-child { transform: none; }
    .ticks span:last-child { transform: translateX(-100%); }
    .rows { list-style: none; margin: 0; padding: 0; }
    .row { display: grid; grid-template-columns: minmax(160px, 34%) 1fr; align-items: center; min-height: 32px;
      border-bottom: 1px solid var(--tf-border-soft); cursor: pointer; }
    .row:hover, .row.selected { background: var(--tf-hover); }
    .row.dimmed { opacity: .45; }
    .label { display: flex; align-items: center; gap: .4rem; min-width: 0; font-size: .8rem; }
    .svc-dot { width: 8px; height: 8px; border-radius: 2px; flex: none; }
    .name { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; color: var(--tf-text); }
    .sub { color: var(--tf-text-muted); font-size: .7rem; white-space: nowrap; }
    .track { position: relative; height: 32px; }
    .bar { position: absolute; top: 9px; height: 14px; border-radius: 4px; min-width: 3px; opacity: .9; }
    .bar.observed { background-image: repeating-linear-gradient(45deg, rgba(255,255,255,.35) 0 3px, transparent 3px 7px); }
    .bar.error { outline: 2px solid var(--tf-critical); outline-offset: 1px; }
    .bar.warning { outline: 2px solid var(--tf-warning); outline-offset: 1px; }
    .bar.async { border: 1px dashed var(--tf-text); }
    .dur { position: absolute; top: 8px; margin-left: 6px; font-size: .72rem; color: var(--tf-text-secondary); white-space: nowrap; }
    .dur i { color: var(--tf-critical); font-size: .75rem; }
    .legend { display: flex; flex-wrap: wrap; gap: .9rem; margin-top: .6rem; font-size: .72rem; color: var(--tf-text-muted); }
    .legend span { display: inline-flex; gap: .35rem; align-items: center; }
    .sw { width: 18px; height: 9px; border-radius: 3px; background: var(--tf-text-muted); display: inline-block; }
    .sw.observed { background-image: repeating-linear-gradient(45deg, rgba(255,255,255,.5) 0 3px, transparent 3px 7px); }
    .sw.err { background: transparent; outline: 2px solid var(--tf-critical); }
    @media (max-width: 640px) { .axis, .row { grid-template-columns: 120px 1fr; } .sub { display: none; } }
  `
})
export class WaterfallComponent {
  readonly spans = input<TraceSpan[]>([]);
  readonly windows = input<ServiceWindow[]>([]);
  readonly basis = input<'SPANS' | 'SERVICE_WINDOWS' | 'NONE'>('SPANS');
  readonly serviceColor = input<(service: string) => string>(() => '#3987e5');
  readonly selectedService = input<string | null>(null);
  readonly spanSelected = output<string | null>();
  protected readonly Math = Math;

  private readonly total = computed(() => {
    if (this.basis() === 'SPANS') {
      return Math.max(1, ...this.spans().map((s) => (s.startOffsetMs ?? 0) + (s.durationMs ?? 0)));
    }
    return Math.max(1, ...this.windows().map((w) => (w.startOffsetMs ?? 0) + (w.durationMs ?? 0)));
  });

  protected readonly ticks = computed(() => {
    const total = this.total();
    return [0, 0.25, 0.5, 0.75, 1].map((f) => ({ pct: f * 100, label: formatDuration(total * f) }));
  });

  protected readonly rows = computed<Row[]>(() => {
    const total = this.total();
    if (this.basis() === 'SPANS') {
      return this.spans()
        .filter((s) => s.startOffsetMs !== null)
        .map((s) => {
          const duration = s.durationMs ?? 0;
          const kind = s.kind && s.kind !== 'SERVER' ? s.kind.toLowerCase() : null;
          return {
            key: s.spanId,
            label: s.operation ?? s.spanId,
            service: s.service ?? 'unknown',
            sublabel: [s.service, kind, s.peerService ? `→ ${s.peerService}` : null].filter(Boolean).join(' · '),
            depth: Math.min(s.depth, 8),
            leftPct: ((s.startOffsetMs ?? 0) / total) * 100,
            widthPct: Math.max((duration / total) * 100, 0.4),
            duration: s.durationMs === null ? 'unknown' : formatDuration(s.durationMs),
            status: s.status,
            observed: s.durationKind === 'OBSERVED',
            async: s.async,
            kind,
            spanId: s.spanId,
            tooltip: `${s.service ?? ''} · ${s.operation ?? ''}\nStart +${formatDuration(s.startOffsetMs)} · `
              + `${s.durationKind === 'UNKNOWN' ? 'duration unknown' : formatDuration(s.durationMs) + ' (' + s.durationKind.toLowerCase() + ')'}`
              + `${s.httpStatus ? ' · HTTP ' + s.httpStatus : ''}${s.parentMissing ? ' · parent span not in logs' : ''}`
          };
        });
    }
    return this.windows().map((w) => ({
      key: w.service,
      label: w.service,
      service: w.service,
      sublabel: `${w.eventCount} events`,
      depth: 0,
      leftPct: ((w.startOffsetMs ?? 0) / total) * 100,
      widthPct: Math.max(((w.durationMs ?? 0) / total) * 100, 0.4),
      duration: formatDuration(w.durationMs),
      status: w.status,
      observed: true,
      async: false,
      kind: null,
      spanId: null,
      tooltip: `${w.service}: first to last log line ${formatDuration(w.durationMs)} (observed, not a request duration)`
    }));
  });
}
