import type { EChartsOption } from 'echarts';
import { CountItem, Dashboard, TimeSeries } from '../../core/api/models';
import { formatTimestamp, simpleClassName } from '../../core/util/format';
import { baseOption, categoryAxis, chrome, levelColor, STATUS, valueAxis } from '../../shared/components/chart-theme';

/** Pure option builders (unit tested) - charts are derived from API data only. */

export function eventsOverTimeOption(series: TimeSeries | null, dark: boolean): EChartsOption {
  const c = chrome(dark);
  const buckets = series?.buckets ?? [];
  const labels = buckets.map((b) => formatTimestamp(b.start, false).slice(0, series && series.bucketSeconds >= 60 ? 5 : 8));
  const other = buckets.map((b) => Math.max(0, b.events - b.errors - b.warnings));
  return {
    ...baseOption(dark),
    tooltip: { ...(baseOption(dark).tooltip as object), trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { ...(baseOption(dark).legend as object), data: ['Other', 'Warnings', 'Errors'] },
    xAxis: categoryAxis(dark, labels),
    yAxis: valueAxis(dark, { minInterval: 1 }),
    series: [
      { name: 'Other', type: 'bar', stack: 'events', data: other, itemStyle: { color: c.palette[0] }, barMaxWidth: 28 },
      { name: 'Warnings', type: 'bar', stack: 'events', data: buckets.map((b) => b.warnings), itemStyle: { color: STATUS.warning } },
      {
        name: 'Errors', type: 'bar', stack: 'events', data: buckets.map((b) => b.errors),
        itemStyle: { color: STATUS.critical, borderRadius: [4, 4, 0, 0] }
      }
    ]
  };
}

export function horizontalBarOption(items: CountItem[], dark: boolean, opts: {
  name: string; color?: (item: CountItem) => string; labelFormatter?: (key: string) => string;
}): EChartsOption {
  const c = chrome(dark);
  const sorted = [...items].reverse();
  return {
    ...baseOption(dark),
    grid: { left: 8, right: 40, top: 8, bottom: 8, containLabel: true },
    legend: { show: false },
    tooltip: {
      ...(baseOption(dark).tooltip as object),
      trigger: 'item',
      formatter: (p: unknown) => {
        const param = p as { dataIndex: number };
        const item = sorted[param.dataIndex];
        return `${escapeHtml(item.key)}<br/><b>${item.count}</b> ${opts.name}`;
      }
    },
    xAxis: valueAxis(dark, { minInterval: 1 }),
    yAxis: categoryAxis(dark, sorted.map((i) => (opts.labelFormatter ? opts.labelFormatter(i.key) : i.key)), {
      axisLabel: { color: c.textSecondary, fontSize: 11, width: 150, overflow: 'truncate' }
    }),
    series: [{
      name: opts.name,
      type: 'bar',
      barMaxWidth: 18,
      data: sorted.map((i) => ({ value: i.count, itemStyle: { color: opts.color ? opts.color(i) : c.palette[0], borderRadius: [0, 4, 4, 0] } })),
      label: { show: true, position: 'right', color: c.textSecondary, fontSize: 11 }
    }]
  };
}

export function severityOption(items: CountItem[], dark: boolean): EChartsOption {
  return horizontalBarOption(items, dark, { name: 'events', color: (i) => levelColor(i.key, dark) });
}

export function exceptionsOption(items: CountItem[], dark: boolean): EChartsOption {
  return horizontalBarOption(items, dark, { name: 'occurrences', labelFormatter: simpleClassName, color: () => STATUS.critical });
}

export function durationHistogramOption(d: Dashboard, dark: boolean): EChartsOption {
  const c = chrome(dark);
  const buckets = d.requestDurationHistogram;
  return {
    ...baseOption(dark),
    tooltip: { ...(baseOption(dark).tooltip as object), trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { show: false },
    xAxis: categoryAxis(dark, buckets.map((b) => b.label), { axisLabel: { color: c.muted, fontSize: 10, interval: 0, rotate: 30 } }),
    yAxis: valueAxis(dark, { minInterval: 1, name: 'traces', nameTextStyle: { color: c.muted, fontSize: 10 } }),
    series: [{
      name: 'Traces',
      type: 'bar',
      barMaxWidth: 34,
      data: buckets.map((b) => b.count),
      itemStyle: { color: c.palette[2], borderRadius: [4, 4, 0, 0] }
    }]
  };
}

export function escapeHtml(text: string): string {
  return text.replace(/[&<>"']/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[ch] ?? ch);
}
