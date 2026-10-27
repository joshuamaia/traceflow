import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { of, Subject } from 'rxjs';
import { ScenarioRun } from '../../core/api/models';
import { TraceflowApi } from '../../core/api/traceflow-api.service';
import { page, scenarios } from '../../../testing/fixtures';
import { ScenariosPage } from './scenarios.page';

describe('ScenariosPage', () => {
  let execution: Subject<ScenarioRun>;

  async function setup() {
    execution = new Subject<ScenarioRun>();
    const api = {
      scenarios: vi.fn(() => of(scenarios())),
      executeScenario: vi.fn(() => execution.asObservable()),
      scenarioRuns: vi.fn(() => of(page([]))),
      trace: vi.fn()
    };
    TestBed.configureTestingModule({
      imports: [ScenariosPage],
      providers: [provideRouter([]), MessageService, { provide: TraceflowApi, useValue: api }]
    });
    const fixture = TestBed.createComponent(ScenariosPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, api, el: fixture.nativeElement as HTMLElement, messages: TestBed.inject(MessageService) };
  }

  it('renders the five scenarios with their state', async () => {
    const { el } = await setup();
    expect(el.querySelectorAll('article.card').length).toBe(5);
    const first = el.querySelector('[data-testid="scenario-successful-request"]')!;
    expect(first.textContent).toContain('COMPLETED · 200');
    expect(first.textContent).toContain('corr-first');
    expect(first.textContent).toContain('Run again');
    expect(el.querySelector('[data-testid="scenario-async-messaging"]')!.textContent).toContain('Execute Scenario');
  });

  it('executes a scenario, shows the running state and the new correlationId', async () => {
    const { fixture, api, el, messages } = await setup();
    const toast = vi.spyOn(messages, 'add');
    (el.querySelector('[data-testid="execute-service-timeout"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(api.executeScenario).toHaveBeenCalledWith('service-timeout');
    const card = el.querySelector('[data-testid="scenario-service-timeout"]')!;
    expect(card.textContent).toContain('RUNNING');
    expect((el.querySelector('[data-testid="execute-service-timeout"]') as HTMLButtonElement).disabled).toBe(true);

    execution.next({
      id: 'run-2', scenarioKey: 'service-timeout', scenarioName: 'Service Timeout', status: 'COMPLETED', httpStatus: 504,
      outcome: 'payment-service exceeded the 5000 ms timeout', correlationId: 'corr-new', traceId: 't', traceRef: null,
      analysisId: 'a', startedAt: '2026-10-27T10:00:00Z', finishedAt: null, details: { timeoutMs: 5000 }, failureReason: null
    });
    execution.complete();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(card.textContent).toContain('COMPLETED · 504');
    expect(card.textContent).toContain('corr-new');
    expect(el.querySelector('.run-panel')!.textContent).toContain('Timeout Ms');
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('selects a scenario to show its details', async () => {
    const { fixture, el } = await setup();
    (el.querySelector('[data-testid="scenario-database-exception"]') as HTMLElement).click();
    fixture.detectChanges();
    expect(el.querySelector('.run-panel h2')!.textContent).toContain('Database Exception');
    expect(el.querySelector('.run-panel')!.textContent).toContain('Not executed yet');
  });
});
