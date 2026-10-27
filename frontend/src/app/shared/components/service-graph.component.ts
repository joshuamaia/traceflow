import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  output,
  signal,
  viewChild
} from '@angular/core';
import type cytoscape from 'cytoscape';
import { Tooltip } from 'primeng/tooltip';
import { TraceEdge, TraceNode } from '../../core/api/models';
import { formatDuration } from '../../core/util/format';
import { chrome, STATUS } from './chart-theme';

type Cy = cytoscape.Core;

const NODE_SHAPES: Record<TraceNode['type'], string> = {
  SERVICE: 'round-rectangle',
  GATEWAY: 'round-hexagon',
  DATABASE: 'barrel',
  BROKER: 'round-diamond',
  EXTERNAL: 'round-octagon',
  CACHE: 'barrel'
};

/**
 * Interactive service graph (Cytoscape.js + dagre layout): zoom, pan, fit, node selection.
 * Solid edges are synchronous calls, dashed edges asynchronous messaging, dotted edges inferred relations.
 */
@Component({
  selector: 'tf-service-graph',
  imports: [Tooltip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="toolbar" role="toolbar" aria-label="Graph controls">
      <button type="button" class="tf-icon-btn" pTooltip="Zoom in" aria-label="Zoom in" (click)="zoom(1.25)">
        <i class="pi pi-search-plus"></i>
      </button>
      <button type="button" class="tf-icon-btn" pTooltip="Zoom out" aria-label="Zoom out" (click)="zoom(0.8)">
        <i class="pi pi-search-minus"></i>
      </button>
      <button type="button" class="tf-icon-btn" pTooltip="Fit to view" aria-label="Fit to view" (click)="fit()">
        <i class="pi pi-expand"></i>
      </button>
    </div>
    <div #container class="canvas" [style.height.px]="height()" aria-label="Service call graph" role="img"></div>
    @if (renderError()) {
      <div class="fallback">{{ renderError() }}</div>
    }
    <div class="legend" aria-label="Graph legend">
      <span><i class="line solid"></i>Sync call</span>
      <span><i class="line dashed"></i>Async message</span>
      <span><i class="line dotted"></i>Inferred (not confirmed)</span>
      <span><i class="dot error"></i>Errors</span>
      <span><i class="dot origin"></i>Probable error origin</span>
    </div>
  `,
  styles: `
    :host { display: block; position: relative; }
    .canvas { width: 100%; border-radius: 10px; background: var(--tf-surface-2); border: 1px solid var(--tf-border); }
    .toolbar { position: absolute; top: .5rem; right: .5rem; z-index: 2; display: flex; gap: .25rem;
      background: var(--tf-surface); border: 1px solid var(--tf-border); border-radius: 8px; padding: .15rem; }
    .fallback { padding: 1rem; color: var(--tf-text-muted); font-size: .85rem; }
    .legend { display: flex; flex-wrap: wrap; gap: .9rem; margin-top: .6rem; font-size: .75rem; color: var(--tf-text-muted); }
    .legend span { display: inline-flex; align-items: center; gap: .35rem; }
    .line { display: inline-block; width: 22px; border-top: 2px solid var(--tf-text-secondary); }
    .line.dashed { border-top-style: dashed; }
    .line.dotted { border-top-style: dotted; }
    .dot { width: 10px; height: 10px; border-radius: 3px; display: inline-block; }
    .dot.error { background: ${STATUS.critical}; }
    .dot.origin { border: 3px double ${STATUS.critical}; background: transparent; width: 12px; height: 12px; }
  `
})
export class ServiceGraphComponent {
  readonly nodes = input.required<TraceNode[]>();
  readonly edges = input.required<TraceEdge[]>();
  readonly dark = input(true);
  readonly originService = input<string | null>(null);
  readonly selected = input<string | null>(null);
  readonly height = input(340);
  readonly nodeSelected = output<string | null>();

  protected readonly renderError = signal<string | null>(null);
  private readonly container = viewChild.required<ElementRef<HTMLDivElement>>('container');
  private cy: Cy | null = null;
  private lib: typeof cytoscape | null = null;
  private resizeObserver: ResizeObserver | null = null;

  constructor() {
    afterNextRender(() => void this.init());
    effect(() => {
      // re-render whenever data or theme changes
      const nodes = this.nodes();
      const edges = this.edges();
      const dark = this.dark();
      const origin = this.originService();
      if (this.lib) {
        this.render(nodes, edges, dark, origin);
      }
    });
    effect(() => {
      const selected = this.selected();
      if (this.cy) {
        this.cy.$('node').unselect();
        if (selected) {
          this.cy.$id(selected).select();
        }
      }
    });
    inject(DestroyRef).onDestroy(() => {
      this.resizeObserver?.disconnect();
      this.cy?.destroy();
    });
  }

  zoom(factor: number): void {
    if (!this.cy) {
      return;
    }
    const center = { x: this.cy.width() / 2, y: this.cy.height() / 2 };
    this.cy.zoom({ level: this.cy.zoom() * factor, renderedPosition: center });
  }

  fit(): void {
    this.cy?.fit(undefined, 30);
  }

  private async init(): Promise<void> {
    try {
      const [{ default: cy }, { default: dagre }] = await Promise.all([import('cytoscape'), import('cytoscape-dagre')]);
      cy.use(dagre);
      this.lib = cy;
      this.render(this.nodes(), this.edges(), this.dark(), this.originService());
      if (typeof ResizeObserver !== 'undefined') {
        this.resizeObserver = new ResizeObserver(() => {
          this.cy?.resize();
          this.fit();
        });
        this.resizeObserver.observe(this.container().nativeElement);
      }
    } catch {
      this.renderError.set('The graph could not be rendered in this environment.');
    }
  }

  private render(nodes: TraceNode[], edges: TraceEdge[], dark: boolean, origin: string | null): void {
    if (!this.lib) {
      return;
    }
    const c = chrome(dark);
    const elements: cytoscape.ElementDefinition[] = [
      ...nodes.map((n) => ({
        data: {
          id: n.id,
          label: `${n.name}\n${nodeSubtitle(n)}`,
          shape: NODE_SHAPES[n.type] ?? 'round-rectangle',
          status: n.status,
          virtual: n.virtual ? 'yes' : 'no',
          origin: origin === n.id ? 'yes' : 'no'
        }
      })),
      ...edges.map((e) => ({
        data: {
          id: e.id,
          source: e.source,
          target: e.target,
          label: edgeLabel(e),
          mode: e.mode,
          relation: e.relation,
          failing: e.errors > 0 ? 'yes' : 'no'
        }
      }))
    ];
    const style: cytoscape.StylesheetJson = [
      {
        selector: 'node',
        style: {
          shape: 'data(shape)' as never,
          label: 'data(label)',
          'text-wrap': 'wrap',
          'text-valign': 'center',
          'text-halign': 'center',
          'font-size': 11,
          'font-weight': 600,
          color: c.textPrimary,
          width: 150,
          height: 54,
          'background-color': dark ? '#242422' : '#ffffff',
          'border-width': 2,
          'border-color': dark ? '#4a4a46' : '#c3c2b7'
        }
      },
      { selector: 'node[virtual = "yes"]', style: { 'border-style': 'dashed', 'background-opacity': 0.6 } },
      { selector: 'node[status = "WARNING"]', style: { 'border-color': STATUS.warning } },
      {
        selector: 'node[status = "ERROR"]',
        style: { 'border-color': STATUS.critical, 'background-color': dark ? '#3a1d1d' : '#fdecec' }
      },
      { selector: 'node[origin = "yes"]', style: { 'border-width': 5, 'border-style': 'double', 'border-color': STATUS.critical } },
      { selector: 'node:selected', style: { 'overlay-color': c.palette[0], 'overlay-opacity': 0.18, 'overlay-padding': 6 } },
      {
        selector: 'edge',
        style: {
          width: 2,
          'curve-style': 'bezier',
          'line-color': c.textSecondary,
          'target-arrow-color': c.textSecondary,
          'target-arrow-shape': 'triangle',
          label: 'data(label)',
          'font-size': 10,
          color: c.textSecondary,
          'text-background-color': c.surface,
          'text-background-opacity': 1,
          'text-background-padding': '2px',
          'text-rotation': 'autorotate'
        }
      },
      { selector: 'edge[mode = "ASYNC"]', style: { 'line-style': 'dashed', 'line-dash-pattern': [7, 5] } },
      { selector: 'edge[relation = "INFERRED"]', style: { 'line-style': 'dotted', opacity: 0.75 } },
      { selector: 'edge[failing = "yes"]', style: { 'line-color': STATUS.critical, 'target-arrow-color': STATUS.critical } }
    ];

    this.cy?.destroy();
    try {
      this.cy = this.lib({
        container: this.container().nativeElement,
        elements,
        style,
        layout: { name: 'dagre', rankDir: 'LR', nodeSep: 40, rankSep: 90, padding: 30 } as cytoscape.LayoutOptions,
        minZoom: 0.2,
        maxZoom: 3,
        boxSelectionEnabled: false,
        autoungrabify: false
      });
      this.cy.on('tap', 'node', (event) => this.nodeSelected.emit(event.target.id() as string));
      this.cy.on('tap', (event) => {
        if (event.target === this.cy) {
          this.nodeSelected.emit(null);
        }
      });
      const selected = this.selected();
      if (selected) {
        this.cy.$id(selected).select();
      }
      this.renderError.set(null);
    } catch {
      this.renderError.set('The graph could not be rendered in this environment.');
    }
  }
}

function nodeSubtitle(n: TraceNode): string {
  const type = n.type === 'SERVICE' ? '' : n.type.toLowerCase();
  const errors = n.errorCount > 0 ? `${n.errorCount} error${n.errorCount > 1 ? 's' : ''}` : '';
  const events = n.virtual ? 'no own logs' : `${n.eventCount} events`;
  return [type, events, errors].filter(Boolean).join(' · ');
}

function edgeLabel(e: TraceEdge): string {
  const parts = [e.calls > 1 ? `${e.calls}×` : ''];
  if (e.mode === 'ASYNC' && e.asyncLatencyMs !== null) {
    parts.push(`async ${formatDuration(e.asyncLatencyMs)}`);
  } else if (e.avgDurationMs !== null) {
    parts.push(formatDuration(e.avgDurationMs));
  }
  if (e.relation === 'INFERRED') {
    parts.push('inferred');
  }
  return parts.filter(Boolean).join(' · ');
}
