import { formatBytes, formatDuration, formatPercent, shortId, simpleClassName } from './format';
import { toCsv } from './download';

describe('format helpers', () => {
  it('formats durations with adaptive units', () => {
    expect(formatDuration(null)).toBe('—');
    expect(formatDuration(42)).toBe('42 ms');
    expect(formatDuration(5014)).toBe('5.01 s');
    expect(formatDuration(12_500)).toBe('12.5 s');
    expect(formatDuration(125_000)).toBe('2 min 5 s');
  });

  it('formats bytes, percentages and identifiers', () => {
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(50 * 1024 * 1024)).toBe('50 MB');
    expect(formatPercent(0.2623)).toBe('26.2%');
    expect(shortId('a2ddcd3f60eb5b6925371733a029ed3a')).toBe('a2ddcd3f…ed3a');
    expect(shortId('short')).toBe('short');
    expect(simpleClassName('java.sql.SQLException')).toBe('SQLException');
  });
});

describe('toCsv', () => {
  it('escapes separators and quotes and neutralizes formulas', () => {
    const csv = toCsv([{ a: 'x,y', b: 'say "hi"', c: '=HYPERLINK("evil")', d: null }], ['a', 'b', 'c', 'd']);
    expect(csv).toBe('a,b,c,d\n"x,y","say ""hi""","\'=HYPERLINK(""evil"")",');
  });
});
