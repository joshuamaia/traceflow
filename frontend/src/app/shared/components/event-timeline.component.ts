import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { TimelineEvent } from '../../core/api/models';
import { DurationPipe, SimpleClassPipe, TimestampPipe } from '../pipes/format.pipes';
import { LevelBadgeComponent } from './badges.component';

/** Chronological list of the events of a trace with offsets from the first event. */
@Component({
  selector: 'tf-event-timeline',
  imports: [LevelBadgeComponent, DurationPipe, TimestampPipe, SimpleClassPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ol class="timeline" aria-label="Event timeline">
      @for (e of visible(); track e.id) {
        <li [class.error]="e.error" [class.selected]="selectedEventId() === e.id" tabindex="0"
            (click)="eventSelected.emit(e)" (keydown.enter)="eventSelected.emit(e)">
          <span class="offset">{{ e.offsetMs === null ? '—' : '+' + (e.offsetMs | duration) }}</span>
          <span class="rail" [style.--svc]="serviceColor()(e.service ?? '')"><i></i></span>
          <div class="body">
            <div class="meta">
              <tf-level [level]="e.level" />
              <span class="svc">{{ e.service ?? 'unknown service' }}</span>
              @if (e.operation) { <span class="chip">{{ e.operation }}</span> }
              @if (e.spanKind && e.spanKind !== 'SERVER') { <span class="chip">{{ e.spanKind.toLowerCase() }}</span> }
              @if (e.httpStatus) { <span class="chip" [class.bad]="e.httpStatus >= 500">HTTP {{ e.httpStatus }}</span> }
              @if (e.durationMs !== null) { <span class="chip">{{ e.durationMs | duration }}</span> }
              @if (e.messageId) { <span class="chip mono" [title]="e.messageId">msg</span> }
              <span class="time">{{ e.timestamp | ts: false }}</span>
            </div>
            <div class="msg">{{ e.message }}</div>
            @if (e.exceptionClass) {
              <div class="exc"><i class="pi pi-bolt" aria-hidden="true"></i>{{ e.exceptionClass | simpleClass }}</div>
            }
          </div>
        </li>
      }
    </ol>
  `,
  styles: `
    .timeline { list-style: none; margin: 0; padding: 0; }
    li { display: grid; grid-template-columns: 74px 18px 1fr; gap: .5rem; padding: .45rem .4rem; border-radius: 8px; cursor: pointer; }
    li:hover, li.selected { background: var(--tf-hover); }
    li.error .msg { color: var(--tf-text); }
    .offset { font-family: var(--tf-mono); font-size: .75rem; color: var(--tf-text-muted); text-align: right; padding-top: .15rem; }
    .rail { position: relative; display: flex; justify-content: center; }
    .rail::before { content: ''; position: absolute; top: 0; bottom: -0.9rem; width: 2px; background: var(--tf-border); }
    .rail i { position: relative; margin-top: .3rem; width: 10px; height: 10px; border-radius: 50%; background: var(--svc);
      box-shadow: 0 0 0 2px var(--tf-surface); }
    li.error .rail i { background: var(--tf-critical); }
    li:last-child .rail::before { display: none; }
    .meta { display: flex; flex-wrap: wrap; align-items: center; gap: .35rem; font-size: .75rem; }
    .svc { font-weight: 600; color: var(--tf-text); }
    .chip { padding: 0 .4rem; border-radius: 4px; background: var(--tf-surface-2); color: var(--tf-text-secondary);
      border: 1px solid var(--tf-border-soft); }
    .chip.bad { color: var(--tf-critical); border-color: var(--tf-critical); }
    .mono { font-family: var(--tf-mono); }
    .time { margin-left: auto; color: var(--tf-text-muted); font-family: var(--tf-mono); }
    .msg { margin-top: .2rem; font-size: .83rem; color: var(--tf-text-secondary); word-break: break-word; white-space: pre-wrap; }
    .exc { margin-top: .2rem; font-size: .75rem; color: var(--tf-critical); display: flex; gap: .3rem; align-items: center; }
  `
})
export class EventTimelineComponent {
  readonly events = input<TimelineEvent[]>([]);
  readonly serviceFilter = input<string | null>(null);
  readonly selectedEventId = input<string | null>(null);
  readonly serviceColor = input<(service: string) => string>(() => '#3987e5');
  readonly eventSelected = output<TimelineEvent>();

  protected readonly visible = computed(() => {
    const filter = this.serviceFilter();
    return filter ? this.events().filter((e) => e.service === filter || e.peerService === filter) : this.events();
  });
}
