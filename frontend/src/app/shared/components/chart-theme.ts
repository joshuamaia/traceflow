import type { EChartsOption } from 'echarts';

/**
 * Chart palette and chrome. Values come from a validated, CVD-checked reference palette:
 * categorical slots are assigned in fixed order (never cycled) and status colors are reserved for severity/state.
 */
export const CATEGORICAL = {
  light: ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#6250d6', '#e34948'],
  dark: ['#3987e5', '#d95926', '#199e70', '#c98500', '#d55181', '#008300', '#9085e9', '#e66767']
} as const;

export const STATUS = { good: '#0ca30c', warning: '#fab219', serious: '#ec835a', critical: '#d03b3b' } as const;

/** Severity -> color. Severity is a state, so it uses the status palette (always shown with a text label). */
export function levelColor(level: string, dark: boolean): string {
  switch (level) {
    case 'FATAL':
    case 'ERROR':
      return STATUS.critical;
    case 'WARN':
      return STATUS.warning;
    case 'INFO':
      return dark ? CATEGORICAL.dark[0] : CATEGORICAL.light[0];
    case 'DEBUG':
      return dark ? CATEGORICAL.dark[6] : CATEGORICAL.light[6];
    default:
      return '#898781';
  }
}

export interface ChartChrome {
  surface: string;
  textPrimary: string;
  textSecondary: string;
  muted: string;
  grid: string;
  axis: string;
  palette: readonly string[];
}

export function chrome(dark: boolean): ChartChrome {
  return dark
    ? { surface: '#1a1a19', textPrimary: '#f0efec', textSecondary: '#c3c2b7', muted: '#898781', grid: '#2c2c2a', axis: '#383835', palette: CATEGORICAL.dark }
    : { surface: '#fcfcfb', textPrimary: '#0b0b0b', textSecondary: '#52514e', muted: '#898781', grid: '#e1e0d9', axis: '#c3c2b7', palette: CATEGORICAL.light };
}

/** Base option shared by every chart: recessive axes, hairline grid, themed tooltip. */
export function baseOption(dark: boolean): EChartsOption {
  const c = chrome(dark);
  return {
    backgroundColor: 'transparent',
    color: [...c.palette],
    textStyle: { color: c.textSecondary, fontFamily: 'Inter, system-ui, -apple-system, "Segoe UI", sans-serif' },
    grid: { left: 8, right: 16, top: 28, bottom: 8, containLabel: true },
    tooltip: {
      backgroundColor: dark ? '#242422' : '#ffffff',
      borderColor: dark ? 'rgba(255,255,255,0.10)' : 'rgba(11,11,11,0.10)',
      textStyle: { color: c.textPrimary, fontSize: 12 },
      extraCssText: 'border-radius:8px;box-shadow:0 4px 16px rgba(0,0,0,0.2);'
    },
    legend: { textStyle: { color: c.textSecondary }, icon: 'roundRect', itemWidth: 10, itemHeight: 10, top: 0, right: 0 }
  };
}

export function categoryAxis(dark: boolean, data: string[], extra: Record<string, unknown> = {}) {
  const c = chrome(dark);
  return {
    type: 'category' as const,
    data,
    axisLine: { lineStyle: { color: c.axis } },
    axisTick: { show: false },
    axisLabel: { color: c.muted, fontSize: 11 },
    ...extra
  };
}

export function valueAxis(dark: boolean, extra: Record<string, unknown> = {}) {
  const c = chrome(dark);
  return {
    type: 'value' as const,
    splitLine: { lineStyle: { color: c.grid } },
    axisLabel: { color: c.muted, fontSize: 11 },
    ...extra
  };
}
