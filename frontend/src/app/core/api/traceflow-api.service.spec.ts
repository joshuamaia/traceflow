import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { errorMessage } from './interceptors';
import { toParams, TraceflowApi } from './traceflow-api.service';

describe('TraceflowApi', () => {
  let api: TraceflowApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(TraceflowApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('requests the dashboard for the selected scope', () => {
    let result: unknown;
    api.dashboard('DEMO').subscribe((d) => (result = d));
    const req = http.expectOne((r) => r.url === '/api/dashboard');
    expect(req.request.params.get('scope')).toBe('DEMO');
    req.flush({ totalEvents: 3 });
    expect(result).toEqual({ totalEvents: 3 });
  });

  it('sends trace filters and omits empty values', () => {
    api.traces({ scope: 'ALL', q: 'corr-1', status: undefined, page: 2, size: 50, sort: '-durationMs' }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/traces');
    expect(req.request.params.get('q')).toBe('corr-1');
    expect(req.request.params.has('status')).toBe(false);
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('sort')).toBe('-durationMs');
    req.flush({ content: [], page: 2, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('repeats array parameters for level filters', () => {
    api.events({ analysisId: 'a', level: ['ERROR', 'WARN'] }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/events');
    expect(req.request.params.getAll('level')).toEqual(['ERROR', 'WARN']);
    req.flush({ content: [] });
  });

  it('posts scenario executions and uploads multipart files with progress', () => {
    api.executeScenario('service-timeout').subscribe();
    const exec = http.expectOne('/api/scenarios/service-timeout/execute');
    expect(exec.request.method).toBe('POST');
    exec.flush({});

    api.upload([new File(['x'], 'a.log'), new File(['y'], 'b.json')], 'batch').subscribe();
    const upload = http.expectOne('/api/imports');
    const body = upload.request.body as FormData;
    expect(body.getAll('files')).toHaveLength(2);
    expect(body.get('name')).toBe('batch');
    expect(upload.request.reportProgress).toBe(true);
    upload.flush({});
  });

  it('builds the CSV export url without paging parameters', () => {
    expect(api.eventsCsvUrl({ scope: 'IMPORT', errorsOnly: true, page: 3, size: 10 }))
      .toBe('/api/events/export.csv?scope=IMPORT&errorsOnly=true');
  });

  it('encodes path segments', () => {
    api.tracesByCorrelation('a/b c').subscribe();
    http.expectOne('/api/traces/by-correlation/a%2Fb%20c').flush([]);
  });

  it('extracts server messages and connection failures', () => {
    const apiError = new HttpErrorResponse({ status: 400, error: { message: 'Validation failed', violations: [{ field: 'content', message: 'must not be blank' }] } });
    expect(errorMessage(apiError)).toBe('Validation failed (content: must not be blank)');
    expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toContain('Could not connect');
    expect(toParams({ a: '', b: null, c: 0 }).toString()).toBe('c=0');
  });
});
