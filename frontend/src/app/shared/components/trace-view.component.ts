import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonDirective } from 'primeng/button';
import { Tab, TabList, TabPanel, TabPanels, Tabs } from 'primeng/tabs';
import { Tooltip } from 'primeng/tooltip';
import { LogEvent, TimelineEvent, TraceDetail } from '../../core/api/models';
import { TraceflowApi } from '../../core/api/traceflow-api.service';
import { AppState } from '../../core/state/app-state';
import { downloadJson } from '../../core/util/download';
import { DurationPipe, ShortIdPipe, TimestampPipe } from '../pipes/format.pipes';
import { LevelBadgeComponent, StatusBadgeComponent } from './badges.component';
import { CATEGORICAL } from './chart-theme';
import { CopyButtonComponent } from './copy-button.component';
import { EventTimelineComponent } from './event-timeline.component';
import { ServiceGraphComponent } from './service-graph.component';
import { StackTraceComponent } from './stack-trace.component';
import { WaterfallComponent } from './waterfall.component';

/** Full visualization of one trace: graph, waterfall, timeline, evidence and event details. */
@Component({
  selector: 'tf-trace-view',
  imports: [
    RouterLink, ButtonDirective, Tabs, TabList, Tab, TabPanels, TabPanel, Tooltip, ServiceGraphComponent, WaterfallComponent,
    EventTimelineComponent, StackTraceComponent, StatusBadgeComponent, LevelBadgeComponent, CopyButtonComponent,
    DurationPipe, TimestampPipe, ShortIdPipe
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './trace-view.component.html',
  styleUrl: './trace-view.component.scss'
})
export class TraceViewComponent {
  private readonly api = inject(TraceflowApi);
  private readonly state = inject(AppState);

  readonly detail = input.required<TraceDetail>();
  readonly compact = input(false);

  protected readonly dark = this.state.isDark;
  protected readonly selectedService = signal<string | null>(null);
  protected readonly selectedEvent = signal<LogEvent | null>(null);
  protected readonly loadingEvent = signal(false);
  protected readonly activeTab = signal<string | number | undefined>('waterfall');

  protected readonly selectedNode = computed(() => this.detail().nodes.find((n) => n.id === this.selectedService()) ?? null);
  protected readonly selectedNodeEdges = computed(() => {
    const id = this.selectedService();
    return id ? this.detail().edges.filter((e) => e.source === id || e.target === id) : [];
  });
  protected readonly originService = computed(() => this.detail().errorOrigin?.service ?? null);
  protected readonly errorEvents = computed(() => this.detail().timeline.filter((e) => e.error));

  /** Color follows the service identity: fixed categorical slot by order of appearance (never by rank). */
  protected readonly serviceColor = computed(() => {
    const palette = this.dark() ? CATEGORICAL.dark : CATEGORICAL.light;
    const order = new Map(this.detail().nodes.map((n, i) => [n.id, i]));
    return (service: string) => palette[(order.get(service) ?? 0) % palette.length];
  });

  selectService(service: string | null): void {
    this.selectedService.set(service);
  }

  selectEvent(event: TimelineEvent): void {
    this.loadingEvent.set(true);
    this.api.event(event.id).subscribe({
      next: (full) => {
        this.selectedEvent.set(full);
        this.loadingEvent.set(false);
      },
      error: () => this.loadingEvent.set(false)
    });
  }

  selectSpan(spanId: string | null): void {
    if (!spanId) {
      return;
    }
    const span = this.detail().spans.find((s) => s.spanId === spanId);
    this.selectedService.set(span?.service ?? null);
  }

  exportJson(): void {
    const t = this.detail().trace;
    downloadJson(this.detail(), `trace-${t.correlationId ?? t.traceId ?? t.id}`);
  }

  protected attributes(event: LogEvent): [string, unknown][] {
    return Object.entries(event.attributes ?? {}).filter(([k]) => !k.startsWith('_'));
  }
}
