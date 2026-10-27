/** Formatting helpers shared by pipes, charts and exports. */

export function formatDuration(ms: number | null | undefined): string {
  if (ms === null || ms === undefined || Number.isNaN(ms)) {
    return '—';
  }
  if (ms < 1000) {
    return `${Math.round(ms)} ms`;
  }
  if (ms < 60_000) {
    const seconds = ms / 1000;
    return `${seconds >= 10 ? seconds.toFixed(1) : seconds.toFixed(2)} s`;
  }
  const minutes = Math.floor(ms / 60_000);
  const seconds = Math.round((ms % 60_000) / 1000);
  return `${minutes} min ${seconds} s`;
}

export function formatBytes(bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined) {
    return '—';
  }
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  const units = ['KB', 'MB', 'GB'];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value.toFixed(value >= 10 ? 0 : 1)} ${units[unit]}`;
}

export function formatTimestamp(iso: string | null | undefined, withDate = true): string {
  if (!iso) {
    return '—';
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return iso;
  }
  const pad = (n: number, size = 2) => String(n).padStart(size, '0');
  const time = `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}.${pad(date.getMilliseconds(), 3)}`;
  if (!withDate) {
    return time;
  }
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${time}`;
}

export function formatPercent(ratio: number | null | undefined, digits = 1): string {
  if (ratio === null || ratio === undefined) {
    return '—';
  }
  return `${(ratio * 100).toFixed(digits)}%`;
}

export function formatNumber(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '—';
  }
  return new Intl.NumberFormat('en-US').format(value);
}

/** Shortens long identifiers for display, keeping start and end. */
export function shortId(id: string | null | undefined, keep = 8): string {
  if (!id) {
    return '—';
  }
  return id.length <= keep * 2 + 1 ? id : `${id.slice(0, keep)}…${id.slice(-4)}`;
}

/** Short Java class name: dev.traceflow.demo.OrderService -> OrderService. */
export function simpleClassName(name: string | null | undefined): string {
  if (!name) {
    return '—';
  }
  const parts = name.split('.');
  return parts[parts.length - 1];
}
