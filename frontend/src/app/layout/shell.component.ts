import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ConfirmDialog } from 'primeng/confirmdialog';
import { ProgressBar } from 'primeng/progressbar';
import { SelectButton } from 'primeng/selectbutton';
import { Toast } from 'primeng/toast';
import { Tooltip } from 'primeng/tooltip';
import { filter } from 'rxjs';
import { Scope } from '../core/api/models';
import { TraceflowApi } from '../core/api/traceflow-api.service';
import { AppState } from '../core/state/app-state';

export interface NavItem {
  path: string;
  label: string;
  icon: string;
}

export const NAV_ITEMS: NavItem[] = [
  { path: '/dashboard', label: 'Dashboard', icon: 'pi pi-th-large' },
  { path: '/traces', label: 'Trace Explorer', icon: 'pi pi-sitemap' },
  { path: '/analyzer', label: 'Log Analyzer', icon: 'pi pi-align-justify' },
  { path: '/scenarios', label: 'Demo Scenarios', icon: 'pi pi-play-circle' },
  { path: '/imports', label: 'Import Logs', icon: 'pi pi-upload' },
  { path: '/errors', label: 'Error Explorer', icon: 'pi pi-exclamation-triangle' },
  { path: '/analytics', label: 'Analytics', icon: 'pi pi-chart-line' },
  { path: '/settings', label: 'Settings', icon: 'pi pi-cog' }
];

@Component({
  selector: 'tf-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule, SelectButton, Toast, ConfirmDialog, ProgressBar, Tooltip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss'
})
export class ShellComponent implements OnInit {
  protected readonly state = inject(AppState);
  private readonly api = inject(TraceflowApi);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);

  protected readonly nav = NAV_ITEMS;
  protected readonly sidebarOpen = signal(false);
  protected readonly searchTerm = signal('');
  protected readonly scopeOptions: { label: string; value: Scope }[] = [
    { label: 'Demo', value: 'DEMO' },
    { label: 'Imported', value: 'IMPORT' },
    { label: 'All', value: 'ALL' }
  ];

  ngOnInit(): void {
    this.api.systemInfo().subscribe({ next: (info) => this.state.systemInfo.set(info), error: () => undefined });
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => this.sidebarOpen.set(false));
  }

  setScope(scope: Scope | null): void {
    if (scope) {
      this.state.scope.set(scope);
    }
  }

  /** correlationId / traceId lookup; a single match opens the trace, otherwise the explorer is filtered. */
  search(): void {
    const q = this.searchTerm().trim();
    if (q.length < 2) {
      return;
    }
    this.api.search(q).subscribe((result) => {
      if (result.traces.length === 1) {
        void this.router.navigate(['/traces', result.traces[0].id]);
      } else {
        if (result.traces.length === 0) {
          this.messages.add({ severity: 'info', summary: 'No exact id match', detail: `Searching traces for "${q}"`, life: 2500 });
        }
        void this.router.navigate(['/traces'], { queryParams: { q } });
      }
    });
  }
}
