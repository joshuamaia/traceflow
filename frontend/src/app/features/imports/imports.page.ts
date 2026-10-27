import { HttpEventType } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Paginator, PaginatorState } from 'primeng/paginator';
import { ProgressBar } from 'primeng/progressbar';
import { Skeleton } from 'primeng/skeleton';
import { Tooltip } from 'primeng/tooltip';
import { filter, finalize, switchMap, takeWhile, timer } from 'rxjs';
import { Analysis, Job } from '../../core/api/models';
import { TraceflowApi } from '../../core/api/traceflow-api.service';
import { AppState } from '../../core/state/app-state';
import { StatusBadgeComponent } from '../../shared/components/badges.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { BytesPipe, NumberPipe, TimestampPipe } from '../../shared/pipes/format.pipes';
import { checkFiles, DEFAULT_LIMITS, FileCheck } from './upload-validation';

type Phase = 'idle' | 'uploading' | 'processing' | 'done' | 'failed';

@Component({
  selector: 'tf-imports',
  imports: [ReactiveFormsModule, RouterLink, ButtonDirective, InputText, ProgressBar, Paginator, Skeleton, Tooltip,
    StatusBadgeComponent, EmptyStateComponent, BytesPipe, NumberPipe, TimestampPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './imports.page.html',
  styleUrl: './imports.page.scss'
})
export class ImportsPage {
  private readonly api = inject(TraceflowApi);
  private readonly messages = inject(MessageService);
  private readonly confirm = inject(ConfirmationService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly state = inject(AppState);

  protected readonly limits = computed(() => this.state.systemInfo() ?? { ...DEFAULT_LIMITS, publicMode: false });
  protected readonly importsDisabled = computed(() => this.state.systemInfo()?.publicMode === true);

  protected readonly name = new FormControl('', { nonNullable: true, validators: [Validators.maxLength(200)] });
  protected readonly selection = signal<FileCheck[]>([]);
  protected readonly dragging = signal(false);
  protected readonly phase = signal<Phase>('idle');
  protected readonly uploadPercent = signal(0);
  protected readonly job = signal<Job | null>(null);

  protected readonly validFiles = computed(() => this.selection().filter((c) => !c.error).map((c) => c.file));
  protected readonly hasInvalid = computed(() => this.selection().some((c) => c.error));
  protected readonly busy = computed(() => this.phase() === 'uploading' || this.phase() === 'processing');

  protected readonly page = signal(0);
  protected readonly history = rxResource({ params: () => this.page(), stream: ({ params }) => this.api.analyses('IMPORT', params, 10) });
  protected readonly analyses = computed(() => (this.history.hasValue() ? this.history.value().content : []));
  protected readonly actionInProgress = signal<string | null>(null);

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  onDragLeave(): void {
    this.dragging.set(false);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    this.addFiles(Array.from(event.dataTransfer?.files ?? []));
  }

  onFileInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.addFiles(Array.from(input.files ?? []));
    input.value = '';
  }

  addFiles(files: File[]): void {
    if (!files.length || this.busy()) {
      return;
    }
    const merged = [...this.selection().map((c) => c.file), ...files]
      .filter((f, i, all) => all.findIndex((o) => o.name === f.name && o.size === f.size) === i);
    this.selection.set(checkFiles(merged, this.limits()));
    if (this.phase() === 'done' || this.phase() === 'failed') {
      this.phase.set('idle');
    }
  }

  remove(index: number): void {
    const files = this.selection().map((c) => c.file).filter((_, i) => i !== index);
    this.selection.set(checkFiles(files, this.limits()));
  }

  upload(): void {
    const files = this.validFiles();
    if (!files.length || this.busy() || this.name.invalid) {
      return;
    }
    this.phase.set('uploading');
    this.uploadPercent.set(0);
    this.job.set(null);
    this.state.jobStarted();
    let jobTracking = false;
    this.api.upload(files, this.name.value || undefined)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => { if (!jobTracking) { this.state.jobFinished(); } }))
      .subscribe({
        next: (event) => {
          if (event.type === HttpEventType.UploadProgress && event.total) {
            this.uploadPercent.set(Math.round((event.loaded / event.total) * 100));
          } else if (event.type === HttpEventType.Response && event.body) {
            jobTracking = true;
            this.uploadPercent.set(100);
            this.track(event.body);
          }
        },
        error: () => this.phase.set('failed')
      });
  }

  /** Polls the processing job until it finishes. */
  private track(analysis: Analysis): void {
    this.phase.set('processing');
    this.selection.set([]);
    this.name.reset('');
    timer(0, this.state.pollingIntervalMs())
      .pipe(
        switchMap(() => this.api.job(analysis.id)),
        takeWhile((job) => !job.finished, true),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.state.jobFinished())
      )
      .subscribe({
        next: (job) => {
          this.job.set(job);
          if (job.finished) {
            this.phase.set(job.status === 'COMPLETED' ? 'done' : 'failed');
            this.history.reload();
            this.messages.add(job.status === 'COMPLETED'
              ? { severity: 'success', summary: 'Import processed', detail: `${job.eventsCount} events, ${job.tracesCount} traces, ${job.parseErrorsCount} parse problems` }
              : { severity: 'error', summary: 'Import failed', detail: job.failureReason ?? 'Processing failed' });
          }
        },
        error: () => this.phase.set('failed')
      });
  }

  reprocess(a: Analysis): void {
    this.actionInProgress.set(a.id);
    this.api.reprocess(a.id).pipe(
      switchMap(() => timer(0, this.state.pollingIntervalMs()).pipe(switchMap(() => this.api.job(a.id)))),
      filter((job) => job.finished),
      takeWhile((job) => !job.finished, true),
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.actionInProgress.set(null))
    ).subscribe((job) => {
      this.history.reload();
      this.messages.add({ severity: job.status === 'COMPLETED' ? 'success' : 'error', summary: 'Reprocessing finished',
        detail: `${a.name}: ${job.eventsCount} events, ${job.parseErrorsCount} parse problems` });
    });
  }

  deleteAnalysis(a: Analysis): void {
    this.confirm.confirm({
      header: 'Delete analysis',
      message: `Delete "${a.name}" with its events, traces and stored files? This cannot be undone.`,
      icon: 'pi pi-trash',
      acceptLabel: 'Delete',
      rejectLabel: 'Cancel',
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', outlined: true },
      accept: () => {
        this.actionInProgress.set(a.id);
        this.api.deleteAnalysis(a.id).pipe(finalize(() => this.actionInProgress.set(null))).subscribe(() => {
          this.messages.add({ severity: 'success', summary: 'Analysis deleted', detail: a.name });
          this.history.reload();
        });
      }
    });
  }

  onPage(event: PaginatorState): void {
    this.page.set(event.page ?? 0);
  }
}
