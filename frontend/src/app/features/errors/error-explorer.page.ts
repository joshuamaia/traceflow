import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonDirective } from 'primeng/button';
import { DatePicker } from 'primeng/datepicker';
import { InputText } from 'primeng/inputtext';
import { Paginator, PaginatorState } from 'primeng/paginator';
import { Select } from 'primeng/select';
import { SelectButton } from 'primeng/selectbutton';
import { Skeleton } from 'primeng/skeleton';
import { Tooltip } from 'primeng/tooltip';
import { debounceTime, of } from 'rxjs';
import { ErrorQuery } from '../../core/api/models';
import { TraceflowApi } from '../../core/api/traceflow-api.service';
import { errorMessage } from '../../core/api/interceptors';
import { AppState } from '../../core/state/app-state';
import { downloadJson, downloadUrl } from '../../core/util/download';
import { LevelBadgeComponent, StatusBadgeComponent } from '../../shared/components/badges.component';
import { CopyButtonComponent } from '../../shared/components/copy-button.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { StackTraceComponent } from '../../shared/components/stack-trace.component';
import { NumberPipe, SimpleClassPipe, TimestampPipe } from '../../shared/pipes/format.pipes';

export interface ErrorFilterValue {
  service: string | null;
  exceptionClass: string | null;
  correlationId: string;
  q: string;
  period: Date[] | null;
}

/** Translates the filter form into API query parameters (exported for unit tests). */
export function toErrorQuery(value: ErrorFilterValue, scope: ErrorQuery['scope']): ErrorQuery {
  const [from, to] = value.period ?? [];
  const end = to ? new Date(to.getTime()) : from ? new Date(from.getTime()) : null;
  end?.setHours(23, 59, 59, 999);
  return {
    scope,
    service: value.service ?? undefined,
    exceptionClass: value.exceptionClass ?? undefined,
    correlationId: value.correlationId.trim() || undefined,
    q: value.q.trim() || undefined,
    from: from ? new Date(new Date(from).setHours(0, 0, 0, 0)).toISOString() : undefined,
    to: end ? end.toISOString() : undefined
  };
}

@Component({
  selector: 'tf-error-explorer',
  imports: [FormsModule, ReactiveFormsModule, RouterLink, ButtonDirective, DatePicker, InputText, Paginator, Select,
    SelectButton, Skeleton, Tooltip, LevelBadgeComponent, StatusBadgeComponent, CopyButtonComponent, EmptyStateComponent,
    StackTraceComponent, NumberPipe, SimpleClassPipe, TimestampPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './error-explorer.page.html',
  styleUrl: './error-explorer.page.scss'
})
export class ErrorExplorerPage {
  private readonly api = inject(TraceflowApi);
  protected readonly state = inject(AppState);

  /** ?event=<id> opens the detail of an error directly. */
  readonly event = input<string | undefined>();

  protected readonly form = new FormGroup({
    service: new FormControl<string | null>(null),
    exceptionClass: new FormControl<string | null>(null),
    correlationId: new FormControl('', { nonNullable: true }),
    q: new FormControl('', { nonNullable: true }),
    period: new FormControl<Date[] | null>(null)
  });
  private readonly filterValue = signal<ErrorFilterValue>(this.form.getRawValue());

  protected readonly view = signal<'groups' | 'list'>('groups');
  protected readonly viewOptions = [{ label: 'Grouped', value: 'groups' }, { label: 'All errors', value: 'list' }];
  protected readonly page = signal(0);
  protected readonly fingerprint = signal<string | null>(null);
  protected readonly selectedEventId = signal<string | null>(null);

  protected readonly query = computed(() => toErrorQuery(this.filterValue(), this.state.scope()));

  protected readonly facets = rxResource({ params: () => this.state.scope(), stream: ({ params }) => this.api.errorFacets(params) });
  protected readonly serviceOptions = computed(() => [{ label: 'All services', value: null },
    ...(this.facets.value()?.services ?? []).map((s) => ({ label: s, value: s }))]);
  protected readonly exceptionOptions = computed(() => [{ label: 'All exception classes', value: null },
    ...(this.facets.value()?.exceptionClasses ?? []).map((s) => ({ label: s, value: s }))]);

  protected readonly groups = rxResource({
    params: () => ({ ...this.query(), page: this.page(), size: 20, view: this.view() }),
    stream: ({ params }) => (params.view === 'groups' ? this.api.errorGroups(params) : of(null))
  });
  protected readonly list = rxResource({
    params: () => ({ ...this.query(), fingerprint: this.fingerprint() ?? undefined, page: this.page(), size: 25, view: this.view() }),
    stream: ({ params }) => (params.view === 'list' ? this.api.errors(params) : of(null))
  });
  protected readonly detail = rxResource({
    params: () => this.selectedEventId(),
    stream: ({ params }) => (params ? this.api.errorDetail(params) : of(null))
  });

  protected readonly loading = computed(() => (this.view() === 'groups' ? this.groups.isLoading() : this.list.isLoading()));
  protected readonly error = computed(() => {
    const e = this.view() === 'groups' ? this.groups.error() : this.list.error();
    return e ? errorMessage(e) : null;
  });
  protected readonly total = computed(() =>
    (this.view() === 'groups' ? this.groups.value()?.totalElements : this.list.value()?.totalElements) ?? 0);

  constructor() {
    this.form.valueChanges.pipe(debounceTime(300)).subscribe(() => {
      this.page.set(0);
      this.filterValue.set(this.form.getRawValue());
    });
    effect(() => {
      const id = this.event();
      if (id) {
        untracked(() => this.selectedEventId.set(id));
      }
    });
  }

  setView(view: 'groups' | 'list' | null): void {
    if (view) {
      this.view.set(view);
      this.page.set(0);
      if (view === 'groups') {
        this.fingerprint.set(null);
      }
    }
  }

  showOccurrences(fingerprint: string, event: Event): void {
    event.stopPropagation();
    this.fingerprint.set(fingerprint);
    this.view.set('list');
    this.page.set(0);
  }

  onPage(event: PaginatorState): void {
    this.page.set(event.page ?? 0);
  }

  clear(): void {
    this.form.reset({ service: null, exceptionClass: null, correlationId: '', q: '', period: null });
    this.fingerprint.set(null);
  }

  exportCsv(): void {
    downloadUrl(this.api.eventsCsvUrl({ ...this.query(), errorsOnly: true, fingerprint: this.fingerprint() ?? undefined }),
      'traceflow-errors.csv');
  }

  exportDetail(): void {
    const d = this.detail.value();
    if (d) {
      downloadJson(d, `error-${d.event.id}`);
    }
  }
}
