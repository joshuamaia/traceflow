import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Skeleton } from 'primeng/skeleton';
import { of } from 'rxjs';
import { Scenario, ScenarioRun } from '../../core/api/models';
import { TraceflowApi } from '../../core/api/traceflow-api.service';
import { errorMessage } from '../../core/api/interceptors';
import { StatusBadgeComponent } from '../../shared/components/badges.component';
import { CopyButtonComponent } from '../../shared/components/copy-button.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { TraceViewComponent } from '../../shared/components/trace-view.component';
import { TimestampPipe } from '../../shared/pipes/format.pipes';

/** Keys of scenarios whose flow is asynchronous (rendered with dashed arrows in the card). */
const ASYNC_SCENARIOS = new Set(['async-messaging']);

@Component({
  selector: 'tf-scenarios',
  imports: [RouterLink, ButtonDirective, Skeleton, StatusBadgeComponent, CopyButtonComponent, EmptyStateComponent,
    TraceViewComponent, TimestampPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './scenarios.page.html',
  styleUrl: './scenarios.page.scss'
})
export class ScenariosPage {
  private readonly api = inject(TraceflowApi);
  private readonly messages = inject(MessageService);

  protected readonly catalog = rxResource({ stream: () => this.api.scenarios() });
  protected readonly scenarios = computed(() => (this.catalog.hasValue() ? this.catalog.value() : []));
  protected readonly catalogError = computed(() => (this.catalog.error() ? errorMessage(this.catalog.error()) : null));

  protected readonly running = signal<ReadonlySet<string>>(new Set());
  protected readonly selectedKey = signal<string | null>(null);
  /** Runs executed in this session, overriding the catalog's last run. */
  private readonly sessionRuns = signal<Record<string, ScenarioRun>>({});

  protected readonly selected = computed(() => {
    const key = this.selectedKey() ?? this.scenarios()[0]?.key ?? null;
    return this.scenarios().find((s) => s.key === key) ?? null;
  });
  protected readonly selectedRun = computed(() => {
    const s = this.selected();
    return s ? this.lastRun(s) : null;
  });

  protected readonly trace = rxResource({
    params: () => this.selectedRun()?.traceRef ?? null,
    stream: ({ params }) => (params ? this.api.trace(params) : of(null))
  });

  protected readonly history = rxResource({
    params: () => ({ key: this.selected()?.key, runs: this.selectedRun()?.id }),
    stream: ({ params }) => (params.key ? this.api.scenarioRuns(params.key, 0, 8) : of(null))
  });

  lastRun(s: Scenario): ScenarioRun | null {
    return this.sessionRuns()[s.key] ?? s.lastRun;
  }

  isAsync(s: Scenario): boolean {
    return ASYNC_SCENARIOS.has(s.key);
  }

  isRunning(key: string): boolean {
    return this.running().has(key);
  }

  select(key: string): void {
    this.selectedKey.set(key);
  }

  execute(s: Scenario, event?: Event): void {
    event?.stopPropagation();
    if (this.isRunning(s.key)) {
      return;
    }
    this.selectedKey.set(s.key);
    this.running.update((set) => new Set([...set, s.key]));
    this.api.executeScenario(s.key).subscribe({
      next: (run) => {
        this.sessionRuns.update((runs) => ({ ...runs, [s.key]: run }));
        this.finish(s.key);
        this.messages.add({
          severity: run.status === 'COMPLETED' ? 'success' : 'error',
          summary: `${s.name} · ${run.status.toLowerCase()}`,
          detail: run.outcome ?? run.failureReason ?? '',
          life: 4000
        });
      },
      error: () => this.finish(s.key)
    });
  }

  detailEntries(run: ScenarioRun): { key: string; value: string }[] {
    return Object.entries(run.details ?? {}).map(([key, value]) => ({
      key: humanize(key),
      value: Array.isArray(value) ? value.join(', ') : typeof value === 'object' && value !== null ? JSON.stringify(value) : String(value)
    }));
  }

  private finish(key: string): void {
    this.running.update((set) => {
      const next = new Set(set);
      next.delete(key);
      return next;
    });
  }
}

function humanize(key: string): string {
  return key.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/^./, (c) => c.toUpperCase());
}
