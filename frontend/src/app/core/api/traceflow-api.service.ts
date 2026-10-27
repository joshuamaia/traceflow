import { HttpClient, HttpEvent, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Analysis,
  Analytics,
  Dashboard,
  ErrorDetail,
  ErrorFacets,
  ErrorGroup,
  ErrorQuery,
  EventQuery,
  FileLines,
  Job,
  LiveAnalysis,
  LogEvent,
  Page,
  ParseError,
  Scenario,
  ScenarioRun,
  Scope,
  SystemInfo,
  TraceDetail,
  TraceQuery,
  TraceSummary
} from './models';

export const API_BASE = '/api';

/** Builds HttpParams skipping empty values; arrays become repeated parameters. */
export function toParams(query: object): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') {
      continue;
    }
    if (Array.isArray(value)) {
      for (const item of value) {
        params = params.append(key, String(item));
      }
    } else {
      params = params.set(key, String(value));
    }
  }
  return params;
}

/** Typed client for every TraceFlow backend endpoint. */
@Injectable({ providedIn: 'root' })
export class TraceflowApi {
  private readonly http = inject(HttpClient);

  // dashboard & analytics
  dashboard(scope: Scope): Observable<Dashboard> {
    return this.http.get<Dashboard>(`${API_BASE}/dashboard`, { params: toParams({ scope }) });
  }

  analytics(scope: Scope, from?: string, to?: string): Observable<Analytics> {
    return this.http.get<Analytics>(`${API_BASE}/analytics`, { params: toParams({ scope, from, to }) });
  }

  // traces
  traces(query: TraceQuery): Observable<Page<TraceSummary>> {
    return this.http.get<Page<TraceSummary>>(`${API_BASE}/traces`, { params: toParams(query) });
  }

  trace(id: string): Observable<TraceDetail> {
    return this.http.get<TraceDetail>(`${API_BASE}/traces/${encodeURIComponent(id)}`);
  }

  tracesByCorrelation(correlationId: string): Observable<TraceSummary[]> {
    return this.http.get<TraceSummary[]>(`${API_BASE}/traces/by-correlation/${encodeURIComponent(correlationId)}`);
  }

  tracesByTraceId(traceId: string): Observable<TraceSummary[]> {
    return this.http.get<TraceSummary[]>(`${API_BASE}/traces/by-trace-id/${encodeURIComponent(traceId)}`);
  }

  search(q: string): Observable<{ traces: TraceSummary[]; query: string }> {
    return this.http.get<{ traces: TraceSummary[]; query: string }>(`${API_BASE}/search`, { params: toParams({ q }) });
  }

  // events
  events(query: EventQuery): Observable<Page<LogEvent>> {
    return this.http.get<Page<LogEvent>>(`${API_BASE}/events`, { params: toParams(query) });
  }

  event(id: string): Observable<LogEvent> {
    return this.http.get<LogEvent>(`${API_BASE}/events/${encodeURIComponent(id)}`);
  }

  eventsCsvUrl(query: EventQuery): string {
    const { page, size, sort, includeRaw, ...filters } = query;
    const params = toParams(filters).toString();
    return `${API_BASE}/events/export.csv${params ? '?' + params : ''}`;
  }

  // errors
  errors(query: ErrorQuery): Observable<Page<LogEvent>> {
    return this.http.get<Page<LogEvent>>(`${API_BASE}/errors`, { params: toParams(query) });
  }

  errorGroups(query: ErrorQuery): Observable<Page<ErrorGroup>> {
    return this.http.get<Page<ErrorGroup>>(`${API_BASE}/errors/groups`, { params: toParams(query) });
  }

  errorDetail(eventId: string): Observable<ErrorDetail> {
    return this.http.get<ErrorDetail>(`${API_BASE}/errors/${encodeURIComponent(eventId)}`);
  }

  errorFacets(scope: Scope): Observable<ErrorFacets> {
    return this.http.get<ErrorFacets>(`${API_BASE}/errors/facets`, { params: toParams({ scope }) });
  }

  // scenarios
  scenarios(): Observable<Scenario[]> {
    return this.http.get<Scenario[]>(`${API_BASE}/scenarios`);
  }

  executeScenario(key: string): Observable<ScenarioRun> {
    return this.http.post<ScenarioRun>(`${API_BASE}/scenarios/${encodeURIComponent(key)}/execute`, null);
  }

  scenarioRuns(key?: string, page = 0, size = 10): Observable<Page<ScenarioRun>> {
    return this.http.get<Page<ScenarioRun>>(`${API_BASE}/scenarios/runs`, { params: toParams({ key, page, size }) });
  }

  // imports & analyses
  upload(files: File[], name?: string): Observable<HttpEvent<Analysis>> {
    const form = new FormData();
    for (const file of files) {
      form.append('files', file, file.name);
    }
    if (name) {
      form.append('name', name);
    }
    return this.http.post<Analysis>(`${API_BASE}/imports`, form, { reportProgress: true, observe: 'events' });
  }

  analyses(kind: 'ALL' | 'IMPORT' | 'DEMO', page = 0, size = 20): Observable<Page<Analysis>> {
    return this.http.get<Page<Analysis>>(`${API_BASE}/analyses`, { params: toParams({ kind, page, size }) });
  }

  analysis(id: string): Observable<Analysis> {
    return this.http.get<Analysis>(`${API_BASE}/analyses/${encodeURIComponent(id)}`);
  }

  job(id: string): Observable<Job> {
    return this.http.get<Job>(`${API_BASE}/analyses/${encodeURIComponent(id)}/job`);
  }

  reprocess(id: string): Observable<Job> {
    return this.http.post<Job>(`${API_BASE}/analyses/${encodeURIComponent(id)}/reprocess`, null);
  }

  deleteAnalysis(id: string): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/analyses/${encodeURIComponent(id)}`);
  }

  fileLines(analysisId: string, fileId: string, from: number, limit: number): Observable<FileLines> {
    return this.http.get<FileLines>(
      `${API_BASE}/analyses/${encodeURIComponent(analysisId)}/files/${encodeURIComponent(fileId)}/lines`,
      { params: toParams({ from, limit }) }
    );
  }

  parseErrors(analysisId: string, fileId?: string, page = 0, size = 200): Observable<Page<ParseError>> {
    return this.http.get<Page<ParseError>>(`${API_BASE}/analyses/${encodeURIComponent(analysisId)}/parse-errors`, {
      params: toParams({ fileId, page, size })
    });
  }

  // live analyzer & system
  parseLive(content: string, fileName?: string): Observable<LiveAnalysis> {
    return this.http.post<LiveAnalysis>(`${API_BASE}/analyzer/parse`, { content, fileName });
  }

  systemInfo(): Observable<SystemInfo> {
    return this.http.get<SystemInfo>(`${API_BASE}/system/info`);
  }
}
