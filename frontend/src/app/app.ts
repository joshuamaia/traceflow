import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AppState } from './core/state/app-state';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<router-outlet />`
})
export class App {
  // instantiated eagerly so the persisted theme is applied before the first render
  protected readonly state = inject(AppState);
}
