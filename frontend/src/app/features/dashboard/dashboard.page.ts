import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { ButtonDirective } from 'primeng/button';
import { Skeleton } from 'primeng/skeleton';
import { TraceflowApi } from '../../core/api/traceflow-api.service';
import { errorMessage } from '../../core/api/interceptors';
import { AppState } from '../../core/state/app-state';
import { formatDuration, formatNumber } from '../../core/util/format';
import { ChartComponent } from '../../shared/components/chart.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { StatCardComponent } from '../../shared/components/stat-card.component';
import { TimestampPipe } from '../../shared/pipes/format.pipes';
import { STATUS } from '../../shared/components/chart-theme';
import { durationHistogramOption, eventsOverTimeOption, exceptionsOption, horizontalBarOption, severityOption } from './dashboard-charts';

@Component({
  selector: 'tf-dashboard',
  imports: [RouterLink, ButtonDirective, Skeleton, StatCardComponent, ChartComponent, EmptyStateComponent, TimestampPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.page.html',
  styles: `
    .charts-main { grid-template-columns: minmax(0, 2fr) minmax(0, 1fr); }
    @media (max-width: 1100px) { .charts-main { grid-template-columns: minmax(0, 1fr); } }
    .range { font-size: .8rem; }
  `
})
export class DashboardPage {
  private readonly api = inject(TraceflowApi);
  protected readonly state = inject(AppState);
  protected readonly STATUS = STATUS;

  protected readonly dashboard = rxResource({
    params: () => this.state.scope(),
    stream: ({ params }) => this.api.dashboard(params)
  });

  protected readonly data = computed(() => (this.dashboard.hasValue() ? this.dashboard.value() : null));
  protected readonly loading = computed(() => this.dashboard.isLoading() && !this.data());
  protected readonly error = computed(() => (this.dashboard.error() ? errorMessage(this.dashboard.error()) : null));
  protected readonly empty = computed(() => this.data()?.totalEvents === 0);

  protected readonly cards = computed(() => {
    const d = this.data();
    return [
      { label: 'Traces', value: formatNumber(d?.totalTraces), icon: 'pi pi-sitemap', hint: 'Correlated requests', accent: 'var(--tf-accent)' },
      { label: 'Events', value: formatNumber(d?.totalEvents), icon: 'pi pi-list', hint: 'Parsed log events', accent: 'var(--tf-accent)' },
      { label: 'Errors', value: formatNumber(d?.errorEvents), icon: 'pi pi-times-circle', hint: 'ERROR / FATAL events', accent: STATUS.critical },
      { label: 'Warnings', value: formatNumber(d?.warnEvents), icon: 'pi pi-exclamation-triangle', hint: 'WARN events', accent: STATUS.warning },
      { label: 'Services', value: formatNumber(d?.servicesCount), icon: 'pi pi-server', hint: 'Distinct services', accent: 'var(--tf-accent)' },
      {
        label: 'Avg duration',
        value: d?.avgTraceDurationMs != null ? formatDuration(d.avgTraceDurationMs) : '—',
        icon: 'pi pi-clock',
        hint: d?.avgDurationBasis ? `${d.tracesWithDuration} traces · ${d.avgDurationBasis.toLowerCase()}` : 'No durations available',
        accent: 'var(--tf-accent)'
      }
    ];
  });

  protected readonly eventsChart = computed(() => eventsOverTimeOption(this.data()?.eventsOverTime ?? null, this.state.isDark()));
  protected readonly errorsByServiceChart = computed(() =>
    horizontalBarOption(this.data()?.errorsByService ?? [], this.state.isDark(), { name: 'errors', color: () => STATUS.critical }));
  protected readonly severityChart = computed(() => severityOption(this.data()?.severityDistribution ?? [], this.state.isDark()));
  protected readonly exceptionsChart = computed(() => exceptionsOption(this.data()?.topExceptions ?? [], this.state.isDark()));
  protected readonly durationChart = computed(() => {
    const d = this.data();
    return d ? durationHistogramOption(d, this.state.isDark()) : {};
  });
}
