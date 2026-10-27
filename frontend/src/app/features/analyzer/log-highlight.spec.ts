import { tokenize } from './log-highlight';
import { fromLive } from './log-analyzer.page';

describe('tokenize', () => {
  const classes = (line: string) => tokenize(line).filter((t) => t.cls).map((t) => `${t.cls}:${t.text}`);

  it('highlights timestamps, levels, brackets and key/value pairs in text logs', () => {
    const tokens = classes('2026-10-27 10:15:30 ERROR [order-service] correlationId=abc123 failed');
    expect(tokens).toContain('ts:2026-10-27 10:15:30');
    expect(tokens).toContain('lv-error:ERROR');
    expect(tokens).toContain('bracket:[order-service]');
    expect(tokens).toContain('key:correlationId');
  });

  it('highlights JSON keys, strings, numbers and the level value', () => {
    const tokens = classes('{"level":"WARN","durationMs":42,"ok":true}');
    expect(tokens).toContain('key:"level"');
    expect(tokens).toContain('lv-warn:"WARN"');
    expect(tokens).toContain('num:42');
    expect(tokens).toContain('kw:true');
  });

  it('marks stack frames, causes and masked secrets', () => {
    expect(tokenize('\tat com.acme.A.b(A.java:1)')[0].cls).toBe('frame');
    expect(tokenize('Caused by: java.io.IOException: x')[0].cls).toBe('cause');
    expect(classes('Authorization: Bearer [MASKED]')).toContain('masked:[MASKED]');
  });

  it('never loses characters', () => {
    const line = '2026-10-27T10:15:32.000Z  WARN 4242 --- [svc] [main] d.t.C : took 1830 ms';
    expect(tokenize(line).map((t) => t.text).join('')).toBe(line);
  });
});

describe('fromLive', () => {
  it('maps live analysis events to view events', () => {
    const events = fromLive({
      lines: [], problems: [], totalLines: 1, totalEvents: 1, truncated: false, detectedFormat: 'TEXT', levels: {},
      services: [], correlationIds: [],
      events: [{ index: 0, lineStart: 1, lineEnd: 3, level: 'ERROR', stackTrace: 'x', message: 'm' } as never]
    });
    expect(events[0].key).toBe('live-0');
    expect(events[0].hasStackTrace).toBe(true);
    expect(events[0].eventId).toBeNull();
  });
});
