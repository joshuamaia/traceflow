import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Paginator, PaginatorState } from 'primeng/paginator';
import { Select } from 'primeng/select';
import { Skeleton } from 'primeng/skeleton';
import { Tooltip } from 'primeng/tooltip';
import { debounceTime } from 'rxjs';
import { TraceQuery } from '../../core/api/models';
import { TraceflowApi } from '../../core/api/traceflow-api.service';
import { errorMessage } from '../../core/api/interceptors';
import { AppState } from '../../core/state/app-state';
import { downloadJson } from '../../core/util/download';
import { StatusBadgeComponent } from '../../shared/components/badges.component';
import { CopyButtonComponent } from '../../shared/components/copy-button.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { DurationPipe, ShortIdPipe, TimestampPipe } from '../../shared/pipes/format.pipes';

@Component({
  selector: 'tf-trace-list',
  imports: [
    ReactiveFormsModule, ButtonDirective, InputText, Select, Paginator, Skeleton, Tooltip, StatusBadgeComponent,
    CopyButtonComponent, EmptyStateComponent, DurationPipe, TimestampPipe, ShortIdPipe
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './trace-list.page.html',
  styles: `
    .services { display: flex; flex-wrap: wrap; gap: .25rem; }
    .svc { font-size: .72rem; padding: 0 .35rem; border-radius: 4px; background: var(--tf-surface-2); border: 1px solid var(--tf-border-soft); }
    .op { font-weight: 600; color: var(--tf-text); }
    .ids { display: flex; align-items: center; gap: .15rem; }
    .kind { font-size: .68rem; color: var(--tf-text-muted); }
  `
})
export class TraceListPage {
  private readonly api = inject(TraceflowApi);
  private readonly router = inject(Router);
  protected readonly state = inject(AppState);

  /** Bound from the query string (?q=...) - used by the global search. */
  readonly q = input<string | undefined>();

  protected readonly form = new FormGroup({
    q: new FormControl('', { nonNullable: true }),
    status: new FormControl<string | null>(null),
    sort: new FormControl('-startedAt', { nonNullable: true })
  });
  protected readonly statusOptions = [
    { label: 'Any status', value: null },
    { label: 'OK', value: 'OK' },
    { label: 'Warning', value: 'WARNING' },
    { label: 'Error', value: 'ERROR' }
  ];
  protected readonly sortOptions = [
    { label: 'Newest first', value: '-startedAt' },
    { label: 'Oldest first', value: 'startedAt' },
    { label: 'Slowest first', value: '-durationMs' },
    { label: 'Most errors', value: '-errorCount' }
  ];

  protected readonly page = signal(0);
  protected readonly size = signal(20);
  private readonly filters = signal(this.form.getRawValue());

  protected readonly query = computed<TraceQuery>(() => ({
    scope: this.state.scope(),
    q: this.filters().q || undefined,
    status: this.filters().status ?? undefined,
    sort: this.filters().sort,
    page: this.page(),
    size: this.size()
  }));

  protected readonly traces = rxResource({ params: () => this.query(), stream: ({ params }) => this.api.traces(params) });
  protected readonly rows = computed(() => (this.traces.hasValue() ? this.traces.value().content : []));
  protected readonly total = computed(() => (this.traces.hasValue() ? this.traces.value().totalElements : 0));
  protected readonly error = computed(() => (this.traces.error() ? errorMessage(this.traces.error()) : null));

  constructor() {
    effect(() => {
      const q = this.q();
      if (q !== undefined) {
        untracked(() => this.form.controls.q.setValue(q));
      }
    });
    this.form.valueChanges.pipe(debounceTime(300)).subscribe(() => {
      this.page.set(0);
      this.filters.set(this.form.getRawValue());
    });
  }

  open(id: string): void {
    void this.router.navigate(['/traces', id]);
  }

  onPage(event: PaginatorState): void {
    this.page.set(event.page ?? 0);
    this.size.set(event.rows ?? 20);
  }

  clear(): void {
    this.form.reset({ q: '', status: null, sort: '-startedAt' });
  }

  exportPage(): void {
    downloadJson(this.rows(), `traces-page-${this.page() + 1}`);
  }
}
