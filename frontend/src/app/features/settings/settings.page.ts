import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { InputNumber } from 'primeng/inputnumber';
import { SelectButton } from 'primeng/selectbutton';
import { Scope } from '../../core/api/models';
import { AppState, Theme } from '../../core/state/app-state';
import { BytesPipe } from '../../shared/pipes/format.pipes';

@Component({
  selector: 'tf-settings',
  imports: [FormsModule, SelectButton, InputNumber, BytesPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="tf-page">
      <div class="tf-page-head">
        <div>
          <h1>Settings</h1>
          <p>Preferences are stored in this browser. Server limits are configured through environment variables.</p>
        </div>
      </div>
      <section class="tf-grid two">
        <div class="tf-panel">
          <h3>Preferences</h3>
          <div class="row">
            <label for="theme">Theme</label>
            <p-selectbutton inputId="theme" [options]="themes" [ngModel]="state.theme()" (ngModelChange)="setTheme($event)"
                            optionLabel="label" optionValue="value" [allowEmpty]="false" size="small" />
          </div>
          <div class="row">
            <label for="scope">Default data scope</label>
            <p-selectbutton inputId="scope" [options]="scopes" [ngModel]="state.scope()" (ngModelChange)="setScope($event)"
                            optionLabel="label" optionValue="value" [allowEmpty]="false" size="small" />
          </div>
          <div class="row">
            <label for="polling">Job polling interval (ms)</label>
            <p-inputnumber inputId="polling" [ngModel]="state.pollingIntervalMs()" (ngModelChange)="setPolling($event)"
                           [min]="500" [max]="10000" [step]="250" [showButtons]="true" size="small" />
          </div>
        </div>
        <div class="tf-panel">
          <h3>Server configuration</h3>
          @if (state.systemInfo(); as info) {
            <dl class="kv">
              <dt>Version</dt><dd>{{ info.version }}</dd>
              <dt>Mode</dt><dd>{{ info.publicMode ? 'Public (persistent imports disabled)' : 'Private (imports enabled)' }}</dd>
              <dt>Max file size</dt><dd>{{ info.maxFileSizeBytes | bytes }}</dd>
              <dt>Max files / import</dt><dd>{{ info.maxFiles }}</dd>
              <dt>Extensions</dt><dd>{{ info.allowedExtensions.join(', ') }}</dd>
              <dt>Paste limit</dt><dd>{{ info.maxPasteSizeBytes | bytes }}</dd>
              <dt>Retention</dt><dd>{{ info.retentionEnabled ? 'Analyses older than ' + info.retentionDays + ' days are deleted daily' : 'Disabled' }}</dd>
              <dt>Default time zone</dt><dd>{{ info.defaultZone }} (for timestamps without offset)</dd>
              <dt>Parsers</dt><dd>{{ info.parsers.join(', ') }}</dd>
            </dl>
          } @else {
            <p class="tf-muted">Server information is unavailable.</p>
          }
        </div>
      </section>
      <section class="tf-panel">
        <h3>Privacy</h3>
        <ul class="privacy">
          <li>All processing happens on this TraceFlow server; logs are never sent to external services.</li>
          <li>JWTs, passwords, API keys, cookies and Authorization headers are masked before anything is stored.</li>
          <li>Imported analyses can be deleted at any time from Import Logs; the retention policy removes old data automatically.</li>
          <li>Paste mode in the Log Analyzer parses content without storing it.</li>
        </ul>
      </section>
    </div>
  `,
  styles: `
    .row { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding: .6rem 0; border-bottom: 1px solid var(--tf-border-soft); flex-wrap: wrap; }
    .row:last-child { border-bottom: 0; }
    label { font-size: .88rem; }
    .kv { display: grid; grid-template-columns: 160px minmax(0, 1fr); gap: .45rem .8rem; margin: 0; font-size: .85rem; }
    .kv dt { color: var(--tf-text-muted); }
    .kv dd { margin: 0; }
    .privacy { margin: 0; padding-left: 1.1rem; display: flex; flex-direction: column; gap: .35rem; color: var(--tf-text-secondary); }
  `
})
export class SettingsPage {
  protected readonly state = inject(AppState);
  protected readonly themes: { label: string; value: Theme }[] = [{ label: 'Dark', value: 'dark' }, { label: 'Light', value: 'light' }];
  protected readonly scopes: { label: string; value: Scope }[] = [
    { label: 'Demo', value: 'DEMO' }, { label: 'Imported', value: 'IMPORT' }, { label: 'All', value: 'ALL' }
  ];

  setTheme(theme: Theme | null): void {
    if (theme) {
      this.state.theme.set(theme);
    }
  }

  setScope(scope: Scope | null): void {
    if (scope) {
      this.state.scope.set(scope);
    }
  }

  setPolling(value: number | null): void {
    if (value && value >= 500) {
      this.state.pollingIntervalMs.set(value);
    }
  }
}
