import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import type { EChartsOption } from 'echarts';
import { ButtonDirective } from 'primeng/button';
import { DatePicker } from 'primeng/datepicker';
import { SelectButton } from 'primeng/selectbutton';
import { Skeleton } from 'primeng/skeleton';
import { Tooltip } from 'primeng/tooltip';
import { DurationStats, TimeSeries } from '../../core/api/models';
import { TraceflowApi } from '../../core/api/traceflow-api.service';
import { errorMessage } from '../../core/api/interceptors';
import { AppState } from '../../core/state/app-state';
import { downloadJson } from '../../core/util/download';
import { formatNumber, formatPercent, formatTimestamp } from '../../core/util/format';
import { baseOption, categoryAxis, STATUS, valueAxis } from '../../shared/components/chart-theme';
import { ChartComponent } from '../../shared/components/chart.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { StatCardComponent } from '../../shared/components/stat-card.component';
import { DurationPipe, NumberPipe, PercentPipe, SimpleClassPipe } from '../../shared/pipes/format.pipes';
import { eventsOverTimeOption, severityOption } from '../dashboard/dashboard-charts';

type Preset = 'all' | '1h' | '24h' | '7d' | 'custom';

/** Error rate per bucket, only for buckets with events (an empty bucket has no rate, not 0%). */
export function errorRateOption(series: TimeSeries | null, dark: boolean): EChartsOption {
  const buckets = series?.buckets ?? [];
  const labels = buckets.map((b) => formatTimestamp(b.start, (series?.bucketSeconds ?? 0) >= 86400).slice(0, 16));
  return {
    ...baseOption(dark),
    tooltip: { ...(baseOption(dark).tooltip as object), trigger: 'axis', valueFormatter: (v: unknown) => (v == null ? 'no events' : `${v}%`) },
    legend: { show: false },
    xAxis: categoryAxis(dark, labels, { boundaryGap: false }),
    yAxis: valueAxis(dark, { max: 100, axisLabel: { formatter: '{value}%' } }),
    series: [{
      name: 'Error rate',
      type: 'line',
      connectNulls: false,
      symbolSize: 8,
      lineStyle: { width: 2, color: STATUS.critical },
      itemStyle: { color: STATUS.critical },
      data: buckets.map((b) => (b.events > 0 ? Math.round((b.errors / b.events) * 1000) / 10 : null))
    }]
  };
}

export function describePercentile(stats: DurationStats, key: 'p50Ms' | 'p95Ms' | 'p99Ms'): string | null {
  return stats[key] === null ? (key === 'p50Ms' ? 'needs ≥ 5 samples' : key === 'p95Ms' ? 'needs ≥ 20 samples' : 'needs ≥ 100 samples') : null;
}

@Component({
  selector: 'tf-analytics',
  imports: [FormsModule, ButtonDirective, DatePicker, SelectButton, Skeleton, Tooltip, ChartComponent, EmptyStateComponent,
    StatCardComponent, DurationPipe, NumberPipe, PercentPipe, SimpleClassPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analytics.page.html',
  styles: `
    .bar { height: 6px; border-radius: 3px; background: var(--tf-critical); min-width: 2px; }
    .rate-cell { min-width: 140px; }
    .insufficient { font-size: .72rem; color: var(--tf-text-muted); font-style: italic; }
    .exc { color: var(--tf-critical); font-weight: 600; }
    .notes { margin: 0; padding-left: 1.1rem; display: flex; flex-direction: column; gap: .3rem; font-size: .84rem; color: var(--tf-text-secondary); }
    .range { width: 260px; }
  `
})
export class AnalyticsPage {
  private readonly api = inject(TraceflowApi);
  protected readonly state = inject(AppState);
  protected readonly describe = describePercentile;

  protected readonly preset = signal<Preset>('all');
  protected readonly customRange = signal<Date[] | null>(null);
  protected readonly presetOptions = [
    { label: 'All data', value: 'all' }, { label: 'Last hour', value: '1h' }, { label: 'Last 24h', value: '24h' },
    { label: 'Last 7 days', value: '7d' }, { label: 'Custom', value: 'custom' }
  ];

  private readonly range = computed(() => {
    const now = Date.now();
    switch (this.preset()) {
      case '1h': return { from: new Date(now - 3600_000).toISOString() };
      case '24h': return { from: new Date(now - 86400_000).toISOString() };
      case '7d': return { from: new Date(now - 7 * 86400_000).toISOString() };
      case 'custom': {
        const [from, to] = this.customRange() ?? [];
        const end = to ?? from;
        return {
          from: from ? new Date(new Date(from).setHours(0, 0, 0, 0)).toISOString() : undefined,
          to: end ? new Date(new Date(end).setHours(23, 59, 59, 999)).toISOString() : undefined
        };
      }
      default: return {};
    }
  });

  protected readonly analytics = rxResource({
    params: () => ({ scope: this.state.scope(), ...this.range() }),
    stream: ({ params }) => this.api.analytics(params.scope, params.from, params.to)
  });
  protected readonly data = computed(() => (this.analytics.hasValue() ? this.analytics.value() : null));
  protected readonly error = computed(() => (this.analytics.error() ? errorMessage(this.analytics.error()) : null));
  protected readonly maxErrors = computed(() => Math.max(1, ...(this.data()?.serviceFailures ?? []).map((s) => s.errors)));

  protected readonly cards = computed(() => {
    const d = this.data();
    return [
      { label: 'Events', value: formatNumber(d?.totalEvents), hint: `${formatNumber(d?.errorEvents)} errors`, icon: 'pi pi-list' },
      { label: 'Event error rate', value: formatPercent(d?.eventErrorRate), hint: 'ERROR/FATAL events ÷ all events', icon: 'pi pi-percentage' },
      { label: 'Traces', value: formatNumber(d?.totalTraces), hint: `${formatNumber(d?.errorTraces)} failed`, icon: 'pi pi-sitemap' },
      { label: 'Trace error rate', value: formatPercent(d?.traceErrorRate), hint: 'Traces with status ERROR', icon: 'pi pi-times-circle' }
    ];
  });

  protected readonly volumeChart = computed(() => eventsOverTimeOption(this.data()?.volume ?? null, this.state.isDark()));
  protected readonly rateChart = computed(() => errorRateOption(this.data()?.volume ?? null, this.state.isDark()));
  protected readonly severityChart = computed(() => severityOption(this.data()?.severityDistribution ?? [], this.state.isDark()));

  setPreset(preset: Preset | null): void {
    if (preset) {
      this.preset.set(preset);
    }
  }

  export(): void {
    const d = this.data();
    if (d) {
      downloadJson(d, `traceflow-analytics-${d.scope.toLowerCase()}`);
    }
  }
}
