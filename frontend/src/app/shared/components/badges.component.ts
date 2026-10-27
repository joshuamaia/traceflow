import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Severity badge: color + icon + text so the level is never conveyed by color alone. */
@Component({
  selector: 'tf-level',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': '"tf-badge level-" + level().toLowerCase()' },
  template: `<i [class]="icon()" aria-hidden="true"></i>{{ level() }}`
})
export class LevelBadgeComponent {
  readonly level = input.required<string>();
  protected readonly icon = computed(() => {
    switch (this.level()) {
      case 'FATAL':
      case 'ERROR':
        return 'pi pi-times-circle';
      case 'WARN':
        return 'pi pi-exclamation-triangle';
      case 'INFO':
        return 'pi pi-info-circle';
      default:
        return 'pi pi-circle';
    }
  });
}

/** Trace/span status badge. */
@Component({
  selector: 'tf-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': '"tf-badge status-" + status().toLowerCase()' },
  template: `<i [class]="icon()" aria-hidden="true"></i>{{ label() }}`
})
export class StatusBadgeComponent {
  readonly status = input.required<string>();
  readonly httpStatus = input<number | null | undefined>(null);
  protected readonly icon = computed(() =>
    this.status() === 'ERROR' || this.status() === 'FAILED'
      ? 'pi pi-times-circle'
      : this.status() === 'WARNING'
        ? 'pi pi-exclamation-triangle'
        : this.status() === 'RUNNING' || this.status() === 'PROCESSING' || this.status() === 'PENDING'
          ? 'pi pi-spin pi-spinner'
          : 'pi pi-check-circle'
  );
  protected readonly label = computed(() => (this.httpStatus() ? `${this.status()} · ${this.httpStatus()}` : this.status()));
}
