import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { Tooltip } from 'primeng/tooltip';
import { ClipboardService } from '../../core/util/clipboard';

/** Small icon button copying a value (correlationId, traceId, stack trace...). */
@Component({
  selector: 'tf-copy',
  imports: [Tooltip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button type="button" class="tf-icon-btn" [pTooltip]="'Copy ' + label()" tooltipPosition="top"
            [attr.aria-label]="'Copy ' + label()" [disabled]="!value()" (click)="copy($event)">
      <i class="pi pi-copy" aria-hidden="true"></i>
    </button>
  `
})
export class CopyButtonComponent {
  private readonly clipboard = inject(ClipboardService);
  readonly value = input<string | null | undefined>(null);
  readonly label = input('value');

  copy(event: Event): void {
    event.stopPropagation();
    void this.clipboard.copy(this.value(), this.label().charAt(0).toUpperCase() + this.label().slice(1));
  }
}
