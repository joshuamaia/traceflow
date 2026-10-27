import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { NEVER, of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { TraceflowApi } from '../../core/api/traceflow-api.service';
import { AppState } from '../../core/state/app-state';
import { ChartComponent } from '../../shared/components/chart.component';
import { dashboard } from '../../../testing/fixtures';
import { DashboardPage } from './dashboard.page';

@Component({ selector: 'tf-chart', template: '<div class="chart-stub" [attr.data-empty]="empty()">{{ label() }}</div>' })
class ChartStub {
  readonly options = input<unknown>();
  readonly height = input(260);
  readonly empty = input(false);
  readonly label = input('');
  readonly emptyTitle = input('');
  readonly emptyMessage = input('');
}

async function setup(response: unknown, settle = true) {
  const api = { dashboard: vi.fn(() => response) };
  TestBed.configureTestingModule({
    imports: [DashboardPage],
    providers: [provideRouter([]), MessageService, { provide: TraceflowApi, useValue: api }]
  });
  TestBed.overrideComponent(DashboardPage, { remove: { imports: [ChartComponent] }, add: { imports: [ChartStub] } });
  const fixture = TestBed.createComponent(DashboardPage);
  fixture.detectChanges();
  if (settle) {
    await fixture.whenStable();
    fixture.detectChanges();
  }
  return { fixture, api, el: fixture.nativeElement as HTMLElement };
}

describe('DashboardPage', () => {
  it('renders metric cards and charts from the API data', async () => {
    const { el, api } = await setup(of(dashboard()));
    expect(api.dashboard).toHaveBeenCalledWith('ALL');
    expect(el.querySelector('[data-testid="stat-Traces"]')?.textContent).toContain('5');
    expect(el.querySelector('[data-testid="stat-Errors"]')?.textContent).toContain('10');
    expect(el.querySelector('[data-testid="stat-Avg duration"]')?.textContent).toContain('1.21 s');
    expect(el.querySelectorAll('.chart-stub').length).toBe(5);
  });

  it('shows skeletons while loading', async () => {
    const { el } = await setup(NEVER, false);
    expect(el.querySelectorAll('p-skeleton').length).toBeGreaterThan(0);
    expect(el.querySelector('[data-testid="stat-Traces"]')).toBeNull();
  });

  it('shows an empty state with calls to action when there are no events', async () => {
    const { el } = await setup(of(dashboard({ totalEvents: 0, totalTraces: 0 })));
    expect(el.textContent).toContain('No events in this scope yet');
    expect(el.querySelector('.chart-stub')).toBeNull();
  });

  it('shows the error state with retry', async () => {
    const { el } = await setup(throwError(() => new HttpErrorResponse({ status: 500, error: { message: 'boom' } })));
    expect(el.textContent).toContain('Dashboard unavailable');
    expect(el.textContent).toContain('boom');
  });

  it('reloads when the data scope changes', async () => {
    const { fixture, api } = await setup(of(dashboard()));
    TestBed.inject(AppState).scope.set('IMPORT');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(api.dashboard).toHaveBeenLastCalledWith('IMPORT');
  });
});
