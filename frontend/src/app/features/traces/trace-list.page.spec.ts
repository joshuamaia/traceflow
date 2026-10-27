import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { TraceflowApi } from '../../core/api/traceflow-api.service';
import { page, traceSummary } from '../../../testing/fixtures';
import { TraceListPage } from './trace-list.page';

describe('TraceListPage', () => {
  async function setup(traces = [traceSummary(), traceSummary({ id: 't-2', status: 'OK', httpStatus: 200, correlationId: 'corr-ok' })]) {
    const api = { traces: vi.fn(() => of(page(traces))) };
    TestBed.configureTestingModule({
      imports: [TraceListPage],
      providers: [provideRouter([]), MessageService, { provide: TraceflowApi, useValue: api }]
    });
    const fixture = TestBed.createComponent(TraceListPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, api, el: fixture.nativeElement as HTMLElement };
  }

  it('lists traces with status, ids, services and duration kind', async () => {
    const { el } = await setup();
    const rows = el.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('ERROR · 504');
    expect(rows[0].textContent).toContain('corr-123');
    expect(rows[0].textContent).toContain('payment-service');
    expect(rows[0].textContent).toContain('5.01 s');
    expect(rows[0].textContent).toContain('reported');
  });

  it('applies the search filter after debouncing and resets paging', async () => {
    const { fixture, api } = await setup();
    fixture.componentInstance['form'].controls.q.setValue('corr-123');
    await new Promise((resolve) => setTimeout(resolve, 350));
    fixture.detectChanges();
    expect(api.traces).toHaveBeenLastCalledWith(expect.objectContaining({ q: 'corr-123', page: 0, scope: 'ALL' }));
  });

  it('opens a trace on click', async () => {
    const { el } = await setup();
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    (el.querySelector('tbody tr') as HTMLElement).click();
    expect(navigate).toHaveBeenCalledWith(['/traces', 't-1']);
  });

  it('shows an empty state without results', async () => {
    const { el } = await setup([]);
    expect(el.textContent).toContain('No traces found');
  });
});
