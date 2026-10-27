import { Dashboard, Page, Scenario, TraceDetail, TraceSummary } from '../app/core/api/models';

/** Test fixtures shaped like real API responses. */

export function page<T>(content: T[], totalElements = content.length): Page<T> {
  return { content, page: 0, size: 20, totalElements, totalPages: Math.max(1, Math.ceil(totalElements / 20)) };
}

export function traceSummary(overrides: Partial<TraceSummary> = {}): TraceSummary {
  return {
    id: 't-1', analysisId: 'a-1', sourceKind: 'DEMO', correlationId: 'corr-123', traceId: 'abc123def456',
    rootService: 'api-gateway', rootOperation: 'POST /api/orders', startedAt: '2026-10-27T10:00:00Z',
    endedAt: '2026-10-27T10:00:05Z', durationMs: 5014, durationKind: 'REPORTED', status: 'ERROR', httpStatus: 504,
    eventCount: 14, errorCount: 2, warnCount: 4, services: ['api-gateway', 'order-service', 'payment-service'],
    ...overrides
  };
}

export function dashboard(overrides: Partial<Dashboard> = {}): Dashboard {
  return {
    scope: 'ALL', totalTraces: 5, totalEvents: 61, errorEvents: 10, warnEvents: 12, servicesCount: 5,
    avgTraceDurationMs: 1210, avgDurationBasis: 'REPORTED', tracesWithDuration: 5,
    eventsOverTime: { bucketSeconds: 60, bucketLabel: '1 minute', buckets: [{ start: '2026-10-27T10:00:00Z', events: 61, errors: 10, warnings: 12 }] },
    errorsByService: [{ key: 'order-service', count: 8 }, { key: 'payment-service', count: 2 }],
    severityDistribution: [{ key: 'ERROR', count: 10 }, { key: 'INFO', count: 39 }],
    topExceptions: [{ key: 'java.sql.SQLException', count: 1 }],
    requestDurationHistogram: [{ label: '0ms–50ms', fromMs: 0, toMs: 50, count: 3 }],
    dataFrom: '2026-10-27T10:00:00Z', dataTo: '2026-10-27T10:00:09Z',
    ...overrides
  };
}

export function scenarios(): Scenario[] {
  const keys: [string, string][] = [
    ['successful-request', 'Successful Request'], ['service-timeout', 'Service Timeout'],
    ['database-exception', 'Database Exception'], ['circuit-breaker', 'Circuit Breaker & Retries'],
    ['async-messaging', 'Async Messaging']
  ];
  return keys.map(([key, name], i) => ({
    key, name, description: `${name} description`, flow: ['Order Service', 'Payment Service'],
    expectedOutcome: 'expected', totalRuns: i === 0 ? 1 : 0,
    lastRun: i === 0 ? {
      id: 'run-1', scenarioKey: key, scenarioName: name, status: 'COMPLETED', httpStatus: 200, outcome: 'ok',
      correlationId: 'corr-first', traceId: 'tid', traceRef: null, analysisId: 'a-1',
      startedAt: '2026-10-27T10:00:00Z', finishedAt: '2026-10-27T10:00:01Z', details: { attempts: 3 }, failureReason: null
    } : null
  }));
}

export function traceDetail(): TraceDetail {
  return {
    trace: traceSummary(),
    nodes: [
      { id: 'api-gateway', name: 'api-gateway', type: 'GATEWAY', virtual: false, eventCount: 3, errorCount: 0, warnCount: 1, status: 'WARNING', firstSeen: null, lastSeen: null, operations: [], httpStatus: 504 },
      { id: 'payment-service', name: 'payment-service', type: 'SERVICE', virtual: false, eventCount: 6, errorCount: 1, warnCount: 0, status: 'ERROR', firstSeen: null, lastSeen: null, operations: [], httpStatus: null }
    ],
    edges: [],
    spans: [
      { spanId: 'g', parentSpanId: null, service: 'api-gateway', operation: 'POST /api/orders', kind: 'SERVER', start: null, end: null, startOffsetMs: 0, durationMs: 5014, durationKind: 'REPORTED', status: 'WARNING', httpStatus: 504, eventCount: 3, peerService: null, depth: 0, async: false, parentMissing: false },
      { spanId: 'p', parentSpanId: 'g', service: 'payment-service', operation: 'POST /payments/authorize', kind: 'SERVER', start: null, end: null, startOffsetMs: 8, durationMs: 7316, durationKind: 'REPORTED', status: 'ERROR', httpStatus: null, eventCount: 6, peerService: null, depth: 1, async: false, parentMissing: false }
    ],
    serviceWindows: [],
    timingBasis: 'SPANS',
    timeline: [
      { id: 'e1', timestamp: '2026-10-27T10:00:00Z', offsetMs: 0, service: 'api-gateway', level: 'INFO', message: 'Incoming request', spanId: 'g', spanKind: 'SERVER', operation: null, exceptionClass: null, durationMs: null, httpStatus: null, error: false, fileId: 'f', lineStart: 1, messageId: null, peerService: null },
      { id: 'e2', timestamp: '2026-10-27T10:00:07Z', offsetMs: 7300, service: 'payment-service', level: 'ERROR', message: 'Client aborted', spanId: 'p', spanKind: 'SERVER', operation: null, exceptionClass: 'x.ClientAbortException', durationMs: null, httpStatus: null, error: true, fileId: 'f', lineStart: 9, messageId: null, peerService: null }
    ],
    relationships: { confirmed: 0, inferred: 0, unlinkedServices: [], spansWithMissingParent: 0, timelineOnly: false },
    errorOrigin: { service: 'payment-service', spanId: 'p', eventId: 'e2', reason: 'Deepest span with an error in the call tree' },
    notes: [], truncated: false, totalEvents: 2
  };
}
