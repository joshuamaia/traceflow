import { Routes } from '@angular/router';
import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  {
    path: '',
    component: ShellComponent,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        title: 'Dashboard · TraceFlow',
        loadComponent: () => import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage)
      },
      {
        path: 'traces',
        title: 'Trace Explorer · TraceFlow',
        loadComponent: () => import('./features/traces/trace-list.page').then((m) => m.TraceListPage)
      },
      {
        path: 'traces/:id',
        title: 'Trace · TraceFlow',
        loadComponent: () => import('./features/traces/trace-detail.page').then((m) => m.TraceDetailPage)
      },
      {
        path: 'analyzer',
        title: 'Log Analyzer · TraceFlow',
        loadComponent: () => import('./features/analyzer/log-analyzer.page').then((m) => m.LogAnalyzerPage)
      },
      {
        path: 'scenarios',
        title: 'Demo Scenarios · TraceFlow',
        loadComponent: () => import('./features/scenarios/scenarios.page').then((m) => m.ScenariosPage)
      },
      {
        path: 'imports',
        title: 'Import Logs · TraceFlow',
        loadComponent: () => import('./features/imports/imports.page').then((m) => m.ImportsPage)
      },
      {
        path: 'errors',
        title: 'Error Explorer · TraceFlow',
        loadComponent: () => import('./features/errors/error-explorer.page').then((m) => m.ErrorExplorerPage)
      },
      {
        path: 'analytics',
        title: 'Analytics · TraceFlow',
        loadComponent: () => import('./features/analytics/analytics.page').then((m) => m.AnalyticsPage)
      },
      {
        path: 'settings',
        title: 'Settings · TraceFlow',
        loadComponent: () => import('./features/settings/settings.page').then((m) => m.SettingsPage)
      },
      { path: '**', redirectTo: 'dashboard' }
    ]
  }
];
