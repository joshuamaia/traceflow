import { describePercentile, errorRateOption } from './analytics/analytics.page';
import { eventsOverTimeOption, horizontalBarOption } from './dashboard/dashboard-charts';
import { toErrorQuery } from './errors/error-explorer.page';
import { checkFiles, DEFAULT_LIMITS } from './imports/upload-validation';
import { STATUS } from '../shared/components/chart-theme';

describe('Error Explorer filters', () => {
  it('maps the form to API parameters, trimming text and expanding the period to whole days', () => {
    const query = toErrorQuery({
      service: 'order-service', exceptionClass: null, correlationId: '  corr-1 ', q: ' ', period: [new Date(2026, 9, 27)]
    }, 'DEMO');
    expect(query.scope).toBe('DEMO');
    expect(query.service).toBe('order-service');
    expect(query.exceptionClass).toBeUndefined();
    expect(query.correlationId).toBe('corr-1');
    expect(query.q).toBeUndefined();
    expect(new Date(query.from!).getHours()).toBe(0);
    expect(new Date(query.to!).getHours()).toBe(23);
  });

  it('leaves the period open when not selected', () => {
    const query = toErrorQuery({ service: null, exceptionClass: null, correlationId: '', q: 'timeout', period: null }, 'ALL');
    expect(query).toEqual(expect.objectContaining({ q: 'timeout', from: undefined, to: undefined }));
  });
});

describe('upload validation', () => {
  it('rejects unsupported extensions, empty and oversized files and excess files', () => {
    const big = new File(['x'], 'big.log');
    Object.defineProperty(big, 'size', { value: DEFAULT_LIMITS.maxFileSizeBytes + 1 });
    const checks = checkFiles([new File(['x'], 'ok.jsonl'), new File(['x'], 'evil.exe'), new File([], 'empty.log'), big],
      { ...DEFAULT_LIMITS, maxFiles: 3 });
    expect(checks.map((c) => c.error === null)).toEqual([true, false, false, false]);
    expect(checks[1].error).toContain('Unsupported extension');
    expect(checks[2].error).toBe('File is empty');
    expect(checks[3].error).toContain('Exceeds');
  });
});

describe('chart option builders', () => {
  it('stacks events by severity with reserved status colors', () => {
    const option = eventsOverTimeOption({ bucketSeconds: 60, bucketLabel: '1 minute', buckets: [
      { start: '2026-10-27T10:00:00Z', events: 10, errors: 2, warnings: 3 }] }, true);
    const series = option.series as { name: string; data: number[]; itemStyle: { color: string } }[];
    expect(series.map((s) => s.name)).toEqual(['Other', 'Warnings', 'Errors']);
    expect(series[0].data).toEqual([5]);
    expect(series[2].itemStyle.color).toBe(STATUS.critical);
  });

  it('hides the legend for single series bars and keeps the data order', () => {
    const option = horizontalBarOption([{ key: 'a', count: 5 }, { key: 'b', count: 1 }], false, { name: 'errors' });
    expect((option.legend as { show: boolean }).show).toBe(false);
    const data = (option.series as { data: { value: number }[] }[])[0].data;
    expect(data.map((d) => d.value)).toEqual([1, 5]);
  });

  it('does not invent an error rate for empty buckets', () => {
    const option = errorRateOption({ bucketSeconds: 60, bucketLabel: '1 minute', buckets: [
      { start: '2026-10-27T10:00:00Z', events: 4, errors: 1, warnings: 0 },
      { start: '2026-10-27T10:01:00Z', events: 0, errors: 0, warnings: 0 }] }, true);
    expect((option.series as { data: (number | null)[] }[])[0].data).toEqual([25, null]);
  });

  it('explains missing percentiles', () => {
    const stats = { samples: 4, avgMs: 1, maxMs: 1, minMs: 1, p50Ms: null, p95Ms: null, p99Ms: null, note: null };
    expect(describePercentile(stats, 'p50Ms')).toBe('needs ≥ 5 samples');
    expect(describePercentile({ ...stats, p50Ms: 3 }, 'p50Ms')).toBeNull();
  });
});
