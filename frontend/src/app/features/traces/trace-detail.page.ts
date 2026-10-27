import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { ButtonDirective } from 'primeng/button';
import { Skeleton } from 'primeng/skeleton';
import { TraceflowApi } from '../../core/api/traceflow-api.service';
import { errorMessage } from '../../core/api/interceptors';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { TraceViewComponent } from '../../shared/components/trace-view.component';

@Component({
  selector: 'tf-trace-detail',
  imports: [RouterLink, ButtonDirective, Skeleton, EmptyStateComponent, TraceViewComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="tf-page">
      <div class="tf-page-head">
        <a pButton routerLink="/traces" size="small" severity="secondary" [text]="true"><i class="pi pi-arrow-left"></i> All traces</a>
      </div>
      @if (error()) {
        <div class="tf-panel">
          <tf-empty-state icon="pi pi-exclamation-circle" title="Trace unavailable" [message]="error()">
            <button pButton type="button" size="small" (click)="trace.reload()">Retry</button>
          </tf-empty-state>
        </div>
      } @else if (trace.hasValue()) {
        <tf-trace-view [detail]="trace.value()" />
      } @else {
        <p-skeleton height="140px" />
        <p-skeleton height="380px" styleClass="mt-2" />
        <p-skeleton height="260px" styleClass="mt-2" />
      }
    </div>
  `
})
export class TraceDetailPage {
  private readonly api = inject(TraceflowApi);
  /** Route parameter. */
  readonly id = input.required<string>();

  protected readonly trace = rxResource({ params: () => this.id(), stream: ({ params }) => this.api.trace(params) });
  protected readonly error = computed(() => (this.trace.error() ? errorMessage(this.trace.error()) : null));
}
