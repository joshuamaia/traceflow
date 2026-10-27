/** TypeScript mirrors of the TraceFlow REST API DTOs. */

export type Scope = 'ALL' | 'DEMO' | 'IMPORT';
export type TraceStatus = 'OK' | 'WARNING' | 'ERROR';
export type DurationKind = 'REPORTED' | 'OBSERVED' | 'UNKNOWN';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  violations: { field: string; message: string }[];
}

// ---------------------------------------------------------------- analyses / imports

export interface AnalysisFile {
  id: string;
  originalName: string;
  extension: string;
  sizeBytes: number;
  lineCount: number;
  eventsCount: number;
  parseErrorsCount: number;
  detectedFormat: string | null;
}

export interface Analysis {
  id: string;
  kind: 'DEMO' | 'IMPORT';
  name: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  progressPercent: number;
  totalBytes: number;
  processedBytes: number;
  linesProcessed: number;
  eventsCount: number;
  tracesCount: number;
  errorEventsCount: number;
  parseErrorsCount: number;
  failureReason: string | null;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  files: AnalysisFile[];
}

export interface Job {
  analysisId: string;
  status: Analysis['status'];
  progressPercent: number;
  processedBytes: number;
  totalBytes: number;
  linesProcessed: number;
  eventsCount: number;
  tracesCount: number;
  parseErrorsCount: number;
  failureReason: string | null;
  finished: boolean;
}

export interface FileLines {
  fileId: string;
  fromLine: number;
  lines: { number: number; text: string }[];
  hasMore: boolean;
  totalLines: number;
}

export interface ParseError {
  id: string;
  fileId: string;
  lineNumber: number;
  severity: 'ERROR' | 'WARNING';
  reason: string;
  raw: string | null;
}

// ---------------------------------------------------------------- events

export interface LogEvent {
  id: string;
  analysisId: string;
  fileId: string;
  traceRef: string | null;
  sourceKind: string;
  seq: number;
  lineStart: number;
  lineEnd: number;
  timestamp: string | null;
  timestampRaw: string | null;
  level: string;
  service: string | null;
  thread: string | null;
  logger: string | null;
  correlationId: string | null;
  traceId: string | null;
  spanId: string | null;
  parentSpanId: string | null;
  spanKind: string | null;
  operation: string | null;
  peerService: string | null;
  peerType: string | null;
  messageId: string | null;
  message: string | null;
  exceptionClass: string | null;
  exceptionMessage: string | null;
  hasStackTrace: boolean;
  stackTrace: string | null;
  durationMs: number | null;
  httpStatus: number | null;
  error: boolean;
  fingerprint: string | null;
  parser: string;
  attributes: Record<string, unknown>;
  raw: string | null;
}

export interface EventQuery {
  scope?: Scope;
  analysisId?: string;
  fileId?: string;
  traceRef?: string;
  correlationId?: string;
  traceId?: string;
  service?: string;
  level?: string[];
  q?: string;
  from?: string;
  to?: string;
  errorsOnly?: boolean;
  exceptionClass?: string;
  fingerprint?: string;
  page?: number;
  size?: number;
  sort?: string;
  includeRaw?: boolean;
}

// ---------------------------------------------------------------- traces

export interface TraceSummary {
  id: string;
  analysisId: string;
  sourceKind: string;
  correlationId: string | null;
  traceId: string | null;
  rootService: string | null;
  rootOperation: string | null;
  startedAt: string | null;
  endedAt: string | null;
  durationMs: number | null;
  durationKind: DurationKind;
  status: TraceStatus;
  httpStatus: number | null;
  eventCount: number;
  errorCount: number;
  warnCount: number;
  services: string[];
}

export interface TraceNode {
  id: string;
  name: string;
  type: 'SERVICE' | 'GATEWAY' | 'DATABASE' | 'BROKER' | 'EXTERNAL' | 'CACHE';
  virtual: boolean;
  eventCount: number;
  errorCount: number;
  warnCount: number;
  status: TraceStatus;
  firstSeen: string | null;
  lastSeen: string | null;
  operations: string[];
  httpStatus: number | null;
}

export interface TraceEdge {
  id: string;
  source: string;
  target: string;
  relation: 'CONFIRMED' | 'INFERRED';
  mode: 'SYNC' | 'ASYNC';
  calls: number;
  errors: number;
  avgDurationMs: number | null;
  asyncLatencyMs: number | null;
  operations: string[];
  evidence: string | null;
}

export interface TraceSpan {
  spanId: string;
  parentSpanId: string | null;
  service: string | null;
  operation: string | null;
  kind: string | null;
  start: string | null;
  end: string | null;
  startOffsetMs: number | null;
  durationMs: number | null;
  durationKind: DurationKind;
  status: TraceStatus;
  httpStatus: number | null;
  eventCount: number;
  peerService: string | null;
  depth: number;
  async: boolean;
  parentMissing: boolean;
}

export interface ServiceWindow {
  service: string;
  start: string;
  end: string;
  startOffsetMs: number | null;
  durationMs: number | null;
  eventCount: number;
  status: TraceStatus;
}

export interface TimelineEvent {
  id: string;
  timestamp: string | null;
  offsetMs: number | null;
  service: string | null;
  level: string;
  message: string | null;
  spanId: string | null;
  spanKind: string | null;
  operation: string | null;
  exceptionClass: string | null;
  durationMs: number | null;
  httpStatus: number | null;
  error: boolean;
  fileId: string;
  lineStart: number;
  messageId: string | null;
  peerService: string | null;
}

export interface TraceDetail {
  trace: TraceSummary;
  nodes: TraceNode[];
  edges: TraceEdge[];
  spans: TraceSpan[];
  serviceWindows: ServiceWindow[];
  timingBasis: 'SPANS' | 'SERVICE_WINDOWS' | 'NONE';
  timeline: TimelineEvent[];
  relationships: {
    confirmed: number;
    inferred: number;
    unlinkedServices: string[];
    spansWithMissingParent: number;
    timelineOnly: boolean;
  };
  errorOrigin: { service: string | null; spanId: string | null; eventId: string | null; reason: string } | null;
  notes: string[];
  truncated: boolean;
  totalEvents: number;
}

export interface TraceQuery {
  scope?: Scope;
  analysisId?: string;
  q?: string;
  service?: string;
  status?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
}

// ---------------------------------------------------------------- errors

export interface ErrorGroup {
  fingerprint: string;
  exceptionClass: string | null;
  sampleMessage: string | null;
  occurrences: number;
  firstSeen: string | null;
  lastSeen: string | null;
  services: string[];
  sampleEventId: string | null;
  sampleTraceRef: string | null;
  sampleCorrelationId: string | null;
}

export interface StackFrame {
  text: string;
  className: string;
  method: string;
  location: string;
  framework: boolean;
}

export interface StackAnalysis {
  chain: { className: string; message: string | null; frames: StackFrame[] }[];
  rootCause: { className: string; message: string | null; frames: StackFrame[] } | null;
  probableOrigin: {
    className: string;
    method: string;
    location: string;
    exceptionClass: string;
    evidence: string;
  } | null;
  note: string | null;
}

export interface ErrorDetail {
  event: LogEvent;
  stackAnalysis: StackAnalysis;
  trace: TraceSummary | null;
  similarOccurrences: number;
}

export interface ErrorFacets {
  services: string[];
  exceptionClasses: string[];
  totalErrors: number;
}

export interface ErrorQuery {
  scope?: Scope;
  analysisId?: string;
  service?: string;
  exceptionClass?: string;
  correlationId?: string;
  fingerprint?: string;
  q?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

// ---------------------------------------------------------------- scenarios

export interface ScenarioRun {
  id: string;
  scenarioKey: string;
  scenarioName: string;
  status: 'RUNNING' | 'COMPLETED' | 'FAILED';
  httpStatus: number | null;
  outcome: string | null;
  correlationId: string | null;
  traceId: string | null;
  traceRef: string | null;
  analysisId: string | null;
  startedAt: string;
  finishedAt: string | null;
  details: Record<string, unknown>;
  failureReason: string | null;
}

export interface Scenario {
  key: string;
  name: string;
  description: string;
  flow: string[];
  expectedOutcome: string;
  totalRuns: number;
  lastRun: ScenarioRun | null;
}

// ---------------------------------------------------------------- analytics

export interface CountItem {
  key: string;
  count: number;
}

export interface TimeSeries {
  bucketSeconds: number;
  bucketLabel: string;
  buckets: { start: string; events: number; errors: number; warnings: number }[];
}

export interface DurationStats {
  samples: number;
  avgMs: number | null;
  maxMs: number | null;
  minMs: number | null;
  p50Ms: number | null;
  p95Ms: number | null;
  p99Ms: number | null;
  note: string | null;
}

export interface Dashboard {
  scope: Scope;
  totalTraces: number;
  totalEvents: number;
  errorEvents: number;
  warnEvents: number;
  servicesCount: number;
  avgTraceDurationMs: number | null;
  avgDurationBasis: DurationKind | null;
  tracesWithDuration: number;
  eventsOverTime: TimeSeries | null;
  errorsByService: CountItem[];
  severityDistribution: CountItem[];
  topExceptions: CountItem[];
  requestDurationHistogram: { label: string; fromMs: number; toMs: number | null; count: number }[];
  dataFrom: string | null;
  dataTo: string | null;
}

export interface Analytics {
  scope: Scope;
  from: string | null;
  to: string | null;
  hasData: boolean;
  totalEvents: number;
  errorEvents: number;
  eventErrorRate: number;
  totalTraces: number;
  errorTraces: number;
  traceErrorRate: number;
  volume: TimeSeries | null;
  severityDistribution: CountItem[];
  serviceFailures: { service: string; events: number; errors: number; errorRate: number }[];
  recurringExceptions: { exceptionClass: string; fingerprint: string; occurrences: number; services: string[] }[];
  requestDurations: DurationStats;
  requestDurationBasis: DurationKind | null;
  operationDurations: { service: string; operation: string; stats: DurationStats }[];
  notes: string[];
}

// ---------------------------------------------------------------- live analyzer / system

export interface LiveEvent {
  index: number;
  lineStart: number;
  lineEnd: number;
  timestamp: string | null;
  timestampRaw: string | null;
  level: string;
  service: string | null;
  thread: string | null;
  logger: string | null;
  correlationId: string | null;
  traceId: string | null;
  spanId: string | null;
  parentSpanId: string | null;
  spanKind: string | null;
  operation: string | null;
  peerService: string | null;
  message: string | null;
  exceptionClass: string | null;
  exceptionMessage: string | null;
  stackTrace: string | null;
  durationMs: number | null;
  httpStatus: number | null;
  error: boolean;
  parser: string;
  attributes: Record<string, unknown>;
}

export interface LiveAnalysis {
  lines: string[];
  events: LiveEvent[];
  problems: { lineNumber: number; severity: 'ERROR' | 'WARNING'; reason: string }[];
  totalLines: number;
  totalEvents: number;
  truncated: boolean;
  detectedFormat: string;
  levels: Record<string, number>;
  services: string[];
  correlationIds: string[];
}

export interface SystemInfo {
  version: string;
  publicMode: boolean;
  importsEnabled: boolean;
  maxFileSizeBytes: number;
  maxFiles: number;
  allowedExtensions: string[];
  maxPasteSizeBytes: number;
  retentionEnabled: boolean;
  retentionDays: number;
  defaultZone: string;
  parsers: string[];
}
