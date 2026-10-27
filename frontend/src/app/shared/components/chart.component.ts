import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import type { EChartsOption } from 'echarts';
import { NgxEchartsDirective } from 'ngx-echarts';
import { EmptyStateComponent } from './empty-state.component';

/** ECharts wrapper with consistent sizing and an explicit empty state (never an empty plot). */
@Component({
  selector: 'tf-chart',
  imports: [NgxEchartsDirective, EmptyStateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (empty()) {
      <tf-empty-state icon="pi pi-chart-bar" [title]="emptyTitle()" [message]="emptyMessage()" compact />
    } @else {
      <div echarts class="chart" [options]="options()" [autoResize]="true" [style.height.px]="height()"
           role="img" [attr.aria-label]="label()"></div>
    }
  `,
  styles: `.chart { width: 100%; }`
})
export class ChartComponent {
  readonly options = input.required<EChartsOption>();
  readonly height = input(260);
  readonly empty = input(false);
  readonly label = input('Chart');
  readonly emptyTitle = input('No data');
  readonly emptyMessage = input('There is no data for this chart in the selected scope.');
}
