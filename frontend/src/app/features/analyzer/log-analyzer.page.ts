import { ChangeDetectionStrategy, Component, computed, effect, ElementRef, inject, input, signal, untracked, viewChild } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormControl, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { MultiSelect } from 'primeng/multiselect';
import { Paginator, PaginatorState } from 'primeng/paginator';
import { Select } from 'primeng/select';
import { SelectButton } from 'primeng/selectbutton';
import { Skeleton } from 'primeng/skeleton';
import { Textarea } from 'primeng/textarea';
import { Tooltip } from 'primeng/tooltip';
import { debounceTime, finalize, of } from 'rxjs';
import { LiveAnalysis, LogEvent } from '../../core/api/models';
import { TraceflowApi } from '../../core/api/traceflow-api.service';
import { AppState } from '../../core/state/app-state';
import { downloadBlob, downloadUrl, toCsv } from '../../core/util/download';
import { LevelBadgeComponent } from '../../shared/components/badges.component';
import { CopyButtonComponent } from '../../shared/components/copy-button.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { StackTraceComponent } from '../../shared/components/stack-trace.component';
import { BytesPipe, TimestampPipe } from '../../shared/pipes/format.pipes';
import { tokenize, Token } from './log-highlight';

/** Parser-agnostic event shown in the "Parsed" column (stored or live). */
export interface ViewEvent {
  key: string;
  eventId: string | null;
  traceRef: string | null;
  lineStart: number;
  lineEnd: number;
  level: string;
  timestamp: string | null;
  timestampRaw: string | null;
  service: string | null;
  thread: string | null;
  logger: string | null;
  correlationId: string | null;
  traceId: string | null;
  spanId: string | null;
  parentSpanId: string | null;
  operation: string | null;
  message: string | null;
  exceptionClass: string | null;
  exceptionMessage: string | null;
  stackTrace: string | null;
  hasStackTrace: boolean;
  durationMs: number | null;
  httpStatus: number | null;
  parser: string;
  attributes: Record<string, unknown>;
}

export function fromStored(e: LogEvent): ViewEvent {
  return { ...e, key: e.id, eventId: e.id, traceRef: e.traceRef, stackTrace: e.stackTrace ?? null };
}

export function fromLive(live: LiveAnalysis): ViewEvent[] {
  return live.events.map((e) => ({ ...e, key: `live-${e.index}`, eventId: null, traceRef: null, hasStackTrace: !!e.stackTrace }));
}

const WINDOW = 400;
const LEVELS = ['FATAL', 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE', 'UNKNOWN'];
const EXAMPLE = `2026-10-27 10:15:30 INFO [order-service] [correlationId=abc123] Starting order processing
2026-10-27 10:15:30.120 INFO [order-service] [correlationId=abc123] Calling payment-service Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl
{"@timestamp":"2026-10-27T10:15:30.250Z","level":"INFO","service":"payment-service","correlationId":"abc123","message":"Charging card","durationMs":42}
2026-10-27 10:15:31.500 ERROR [order-service] [correlationId=abc123] Payment failed
java.lang.IllegalStateException: payment declined
\tat com.acme.order.PaymentClient.charge(PaymentClient.java:42)
\tat com.acme.order.OrderService.place(OrderService.java:88)
Caused by: java.net.SocketTimeoutException: Read timed out
\tat com.acme.order.HttpTransport.read(HttpTransport.java:19)
this line does not match any known format
2026-10-27T10:15:32.000Z  WARN 4242 --- [order-service] [nio-8080-exec-1] d.t.OrderController : Slow request took 1830 ms`;

@Component({
  selector: 'tf-log-analyzer',
  imports: [FormsModule, ReactiveFormsModule, RouterLink, ButtonDirective, InputText, Select, SelectButton, MultiSelect,
    Paginator, Skeleton, Textarea, Tooltip, LevelBadgeComponent, CopyButtonComponent, EmptyStateComponent,
    StackTraceComponent, TimestampPipe, BytesPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './log-analyzer.page.html',
  styleUrl: './log-analyzer.page.scss'
})
export class LogAnalyzerPage {
  private readonly api = inject(TraceflowApi);
  protected readonly state = inject(AppState);
  private readonly rawPane = viewChild<ElementRef<HTMLElement>>('rawPane');

  /** ?analysis=<id> preselects a stored analysis. */
  readonly analysis = input<string | undefined>();

  protected readonly mode = signal<'stored' | 'paste'>('stored');
  protected readonly modeOptions = [{ label: 'Stored analyses', value: 'stored' }, { label: 'Paste logs (not stored)', value: 'paste' }];
  protected readonly levelOptions = LEVELS.map((l) => ({ label: l, value: l }));

  // ------------------------------------------------------------ stored mode
  protected readonly analysisId = signal<string | null>(null);
  protected readonly fileId = signal<string | null>(null);
  protected readonly windowStart = signal(1);
  protected readonly levels = signal<string[]>([]);
  protected readonly search = new FormControl('', { nonNullable: true });
  private readonly q = signal('');
  protected readonly eventsPage = signal(0);

  protected readonly analysesList = rxResource({ stream: () => this.api.analyses('ALL', 0, 100) });
  protected readonly analysisOptions = computed(() =>
    (this.analysesList.hasValue() ? this.analysesList.value().content : [])
      .filter((a) => a.status === 'COMPLETED')
      .map((a) => ({ label: `${a.kind === 'DEMO' ? '[demo] ' : ''}${a.name}`, value: a.id, analysis: a })));
  protected readonly selectedAnalysis = computed(() => this.analysisOptions().find((o) => o.value === this.analysisId())?.analysis ?? null);
  protected readonly fileOptions = computed(() => (this.selectedAnalysis()?.files ?? []).map((f) => ({ label: f.originalName, value: f.id, file: f })));
  protected readonly selectedFile = computed(() => this.fileOptions().find((f) => f.value === this.fileId())?.file ?? null);

  protected readonly lines = rxResource({
    params: () => ({ a: this.analysisId(), f: this.fileId(), from: this.windowStart() }),
    stream: ({ params }) => (params.a && params.f ? this.api.fileLines(params.a, params.f, params.from, WINDOW) : of(null))
  });
  protected readonly storedEvents = rxResource({
    params: () => ({ a: this.analysisId(), f: this.fileId(), level: this.levels(), q: this.q(), page: this.eventsPage() }),
    stream: ({ params }) => (params.a && params.f
      ? this.api.events({ analysisId: params.a, fileId: params.f, level: params.level, q: params.q || undefined, page: params.page, size: 100, sort: 'lineStart' })
      : of(null))
  });
  protected readonly parseErrors = rxResource({
    params: () => ({ a: this.analysisId(), f: this.fileId() }),
    stream: ({ params }) => (params.a && params.f ? this.api.parseErrors(params.a, params.f) : of(null))
  });

  // ------------------------------------------------------------ paste mode
  protected readonly pasteContent = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  protected readonly pasteFileName = new FormControl('', { nonNullable: true });
  protected readonly live = signal<LiveAnalysis | null>(null);
  protected readonly analyzing = signal(false);
  protected readonly liveWindowStart = signal(1);

  // ------------------------------------------------------------ shared view
  protected readonly selected = signal<ViewEvent | null>(null);
  protected readonly selectedFull = signal<ViewEvent | null>(null);

  protected readonly viewEvents = computed<ViewEvent[]>(() => {
    if (this.mode() === 'paste') {
      const live = this.live();
      if (!live) {
        return [];
      }
      const q = this.q().toLowerCase();
      const levels = this.levels();
      return fromLive(live).filter((e) => (!levels.length || levels.includes(e.level))
        && (!q || (e.message ?? '').toLowerCase().includes(q) || e.correlationId === this.q() || e.traceId === this.q()));
    }
    return this.storedEvents.hasValue() && this.storedEvents.value() ? this.storedEvents.value()!.content.map(fromStored) : [];
  });
  protected readonly totalEvents = computed(() => this.mode() === 'paste'
    ? this.viewEvents().length
    : this.storedEvents.value()?.totalElements ?? 0);

  protected readonly problems = computed(() => this.mode() === 'paste'
    ? this.live()?.problems ?? []
    : (this.parseErrors.value()?.content ?? []).map((p) => ({ lineNumber: p.lineNumber, severity: p.severity, reason: p.reason })));

  protected readonly rawLines = computed<{ number: number; tokens: Token[]; problem: boolean }[]>(() => {
    const problemLines = new Set(this.problems().filter((p) => p.severity === 'ERROR').map((p) => p.lineNumber));
    if (this.mode() === 'paste') {
      const live = this.live();
      if (!live) {
        return [];
      }
      const start = this.liveWindowStart();
      return live.lines.slice(start - 1, start - 1 + WINDOW)
        .map((text, i) => ({ number: start + i, tokens: tokenize(text), problem: problemLines.has(start + i) }));
    }
    const page = this.lines.value();
    return page ? page.lines.map((l) => ({ number: l.number, tokens: tokenize(l.text), problem: problemLines.has(l.number) })) : [];
  });
  protected readonly rawTotal = computed(() => this.mode() === 'paste' ? this.live()?.lines.length ?? 0 : this.selectedFile()?.lineCount ?? 0);
  protected readonly rawStart = computed(() => this.mode() === 'paste' ? this.liveWindowStart() : this.windowStart());

  constructor() {
    effect(() => {
      const requested = this.analysis();
      const options = this.analysisOptions();
      untracked(() => {
        if (this.analysisId() || !options.length) {
          return;
        }
        const match = options.find((o) => o.value === requested) ?? options.find((o) => o.analysis.kind === 'IMPORT') ?? options[0];
        this.selectAnalysis(match.value);
      });
    });
    this.search.valueChanges.pipe(debounceTime(300)).subscribe((v) => {
      this.eventsPage.set(0);
      this.q.set(v.trim());
    });
  }

  setMode(mode: 'stored' | 'paste' | null): void {
    if (mode) {
      this.mode.set(mode);
      this.selected.set(null);
      this.selectedFull.set(null);
    }
  }

  selectAnalysis(id: string | null): void {
    this.analysisId.set(id);
    const analysis = this.analysisOptions().find((o) => o.value === id)?.analysis;
    this.fileId.set(analysis?.files[0]?.id ?? null);
    this.resetView();
  }

  selectFile(id: string | null): void {
    this.fileId.set(id);
    this.resetView();
  }

  setLevels(levels: string[]): void {
    this.levels.set(levels ?? []);
    this.eventsPage.set(0);
  }

  onEventsPage(event: PaginatorState): void {
    this.eventsPage.set(event.page ?? 0);
  }

  /** Selects an event and highlights its origin lines in the raw view. */
  selectEvent(event: ViewEvent): void {
    this.selected.set(event);
    this.selectedFull.set(event);
    if (event.eventId && event.hasStackTrace && !event.stackTrace) {
      this.api.event(event.eventId).subscribe((full) => this.selectedFull.set(fromStored(full)));
    }
    this.goToLine(event.lineStart);
  }

  goToLine(line: number): void {
    const start = this.rawStart();
    if (line < start || line >= start + WINDOW) {
      const newStart = Math.max(1, line - 20);
      if (this.mode() === 'paste') {
        this.liveWindowStart.set(newStart);
      } else {
        this.windowStart.set(newStart);
      }
    }
    // wait for the window to render before scrolling
    setTimeout(() => this.rawPane()?.nativeElement.querySelector(`[data-line="${line}"]`)?.scrollIntoView({ block: 'center', behavior: 'smooth' }), 150);
  }

  shiftWindow(direction: 1 | -1): void {
    const next = Math.max(1, this.rawStart() + direction * WINDOW);
    if (this.mode() === 'paste') {
      this.liveWindowStart.set(next);
    } else {
      this.windowStart.set(next);
    }
  }

  isHighlighted(line: number): boolean {
    const e = this.selected();
    return !!e && line >= e.lineStart && line <= e.lineEnd;
  }

  loadExample(): void {
    this.pasteContent.setValue(EXAMPLE);
    this.pasteFileName.setValue('order-service.log');
  }

  analyzePaste(): void {
    if (this.pasteContent.invalid || this.analyzing()) {
      return;
    }
    this.analyzing.set(true);
    this.selected.set(null);
    this.api.parseLive(this.pasteContent.value, this.pasteFileName.value || undefined)
      .pipe(finalize(() => this.analyzing.set(false)))
      .subscribe((result) => {
        this.live.set(result);
        this.liveWindowStart.set(1);
      });
  }

  exportCsv(): void {
    if (this.mode() === 'stored') {
      const a = this.analysisId();
      const f = this.fileId();
      if (a && f) {
        downloadUrl(this.api.eventsCsvUrl({ analysisId: a, fileId: f, level: this.levels(), q: this.q() || undefined }), 'traceflow-events.csv');
      }
      return;
    }
    const columns = ['lineStart', 'timestamp', 'level', 'service', 'correlationId', 'traceId', 'spanId', 'operation', 'durationMs', 'httpStatus', 'exceptionClass', 'message', 'logger', 'thread'];
    const rows = this.viewEvents().map((e) => ({ ...e }) as unknown as Record<string, unknown>);
    downloadBlob(new Blob([toCsv(rows, columns)], { type: 'text/csv' }), 'traceflow-live-events.csv');
  }

  fieldList(e: ViewEvent): [string, string | number | null][] {
    return [
      ['Timestamp', e.timestamp ?? (e.timestampRaw ? `${e.timestampRaw} (invalid)` : null)],
      ['Level', e.level], ['Service', e.service], ['Thread', e.thread], ['Logger', e.logger],
      ['CorrelationId', e.correlationId], ['TraceId', e.traceId], ['SpanId', e.spanId], ['ParentSpanId', e.parentSpanId],
      ['Operation', e.operation], ['Duration (ms)', e.durationMs], ['HTTP status', e.httpStatus],
      ['Exception', e.exceptionClass], ['Exception message', e.exceptionMessage],
      ['Lines', e.lineEnd > e.lineStart ? `${e.lineStart}–${e.lineEnd}` : e.lineStart], ['Parser', e.parser]
    ];
  }

  attributeList(e: ViewEvent): [string, unknown][] {
    return Object.entries(e.attributes ?? {});
  }

  private resetView(): void {
    this.windowStart.set(1);
    this.eventsPage.set(0);
    this.selected.set(null);
    this.selectedFull.set(null);
  }
}
