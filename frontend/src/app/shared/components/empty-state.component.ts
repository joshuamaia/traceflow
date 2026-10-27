import { booleanAttribute, ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'tf-empty-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.compact]': 'compact()' },
  template: `
    <i [class]="icon()" aria-hidden="true"></i>
    <h3>{{ title() }}</h3>
    @if (message()) {
      <p>{{ message() }}</p>
    }
    <ng-content />
  `,
  styles: `
    :host { display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center;
      gap: .5rem; padding: 2.5rem 1rem; color: var(--tf-text-muted); }
    :host.compact { padding: 1.5rem 1rem; }
    i { font-size: 2rem; opacity: .6; }
    h3 { margin: 0; font-size: 1rem; font-weight: 600; color: var(--tf-text-secondary); }
    p { margin: 0; max-width: 32rem; font-size: .875rem; line-height: 1.5; }
  `
})
export class EmptyStateComponent {
  readonly icon = input('pi pi-inbox');
  readonly title = input('Nothing here yet');
  readonly message = input<string | null>(null);
  readonly compact = input(false, { transform: booleanAttribute });
}
