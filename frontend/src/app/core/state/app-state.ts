import { computed, DOCUMENT, effect, inject, Injectable, signal } from '@angular/core';
import { Scope, SystemInfo } from '../api/models';

export type Theme = 'dark' | 'light';

const STORAGE_KEYS = { scope: 'tf.scope', theme: 'tf.theme', polling: 'tf.pollingMs' } as const;

function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    // storage unavailable (private mode) - preferences are kept in memory only
  }
}

/** Global UI state shared by the shell and the feature pages. */
@Injectable({ providedIn: 'root' })
export class AppState {
  private readonly document = inject(DOCUMENT);

  /** Dataset shown by read pages: demo data, imported data or both. */
  readonly scope = signal<Scope>(this.initialScope());
  readonly theme = signal<Theme>(read(STORAGE_KEYS.theme) === 'light' ? 'light' : 'dark');
  readonly pollingIntervalMs = signal<number>(Number(read(STORAGE_KEYS.polling)) || 1500);
  readonly systemInfo = signal<SystemInfo | null>(null);

  private readonly pendingRequests = signal(0);
  private readonly backgroundJobs = signal(0);

  /** True while HTTP requests or background jobs (imports) are in progress. */
  readonly processing = computed(() => this.pendingRequests() > 0 || this.backgroundJobs() > 0);
  readonly isDark = computed(() => this.theme() === 'dark');
  readonly scopeLabel = computed(() => {
    switch (this.scope()) {
      case 'DEMO':
        return 'Demo';
      case 'IMPORT':
        return 'Imported';
      default:
        return 'Demo + Imported';
    }
  });

  constructor() {
    effect(() => {
      const dark = this.theme() === 'dark';
      const root = this.document.documentElement;
      root.classList.toggle('tf-dark', dark);
      root.setAttribute('data-theme', this.theme());
      write(STORAGE_KEYS.theme, this.theme());
    });
    effect(() => write(STORAGE_KEYS.scope, this.scope()));
    effect(() => write(STORAGE_KEYS.polling, String(this.pollingIntervalMs())));
  }

  toggleTheme(): void {
    this.theme.update((t) => (t === 'dark' ? 'light' : 'dark'));
  }

  requestStarted(): void {
    this.pendingRequests.update((n) => n + 1);
  }

  requestFinished(): void {
    this.pendingRequests.update((n) => Math.max(0, n - 1));
  }

  jobStarted(): void {
    this.backgroundJobs.update((n) => n + 1);
  }

  jobFinished(): void {
    this.backgroundJobs.update((n) => Math.max(0, n - 1));
  }

  private initialScope(): Scope {
    const stored = read(STORAGE_KEYS.scope);
    return stored === 'DEMO' || stored === 'IMPORT' || stored === 'ALL' ? stored : 'ALL';
  }
}
