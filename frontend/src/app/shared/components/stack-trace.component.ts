import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { StackAnalysis } from '../../core/api/models';
import { CopyButtonComponent } from './copy-button.component';

interface StackLine {
  text: string;
  kind: 'header' | 'cause' | 'app' | 'framework' | 'more' | 'other';
  origin: boolean;
}

/**
 * Stack trace viewer: highlights exception headers and causes, mutes JDK/framework frames and marks the probable
 * origin frame (when the backend found evidence for one). Content is rendered as text (never as HTML).
 */
@Component({
  selector: 'tf-stack-trace',
  imports: [CopyButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="head">
      <span>Stack trace</span>
      <label class="toggle"><input type="checkbox" [checked]="collapsed()" (change)="collapsed.set(!collapsed())" />
        Hide framework frames</label>
      <tf-copy [value]="stackTrace()" label="stack trace" />
    </div>
    <pre class="tf-code" aria-label="Stack trace">@for (line of shown(); track $index) {<span class="l {{ line.kind }}" [class.origin]="line.origin">{{ line.text }}@if (line.origin) {<span class="badge">← probable origin</span>}
</span>}</pre>
  `,
  styles: `
    .head { display: flex; align-items: center; gap: .75rem; margin-bottom: .4rem; font-size: .8rem; font-weight: 600;
      color: var(--tf-text-secondary); }
    .toggle { margin-left: auto; font-weight: 400; display: inline-flex; align-items: center; gap: .35rem; cursor: pointer; }
    pre { max-height: 420px; overflow: auto; margin: 0; }
    .l { display: block; }
    .header { color: var(--tf-critical); font-weight: 700; }
    .cause { color: var(--tf-serious); font-weight: 600; }
    .framework, .more { color: var(--tf-text-muted); }
    .app { color: var(--tf-text); }
    .origin { background: color-mix(in srgb, var(--tf-critical) 18%, transparent); border-left: 3px solid var(--tf-critical);
      padding-left: .4rem; margin-left: -.55rem; }
    .badge { margin-left: .75rem; font-size: .7rem; color: var(--tf-critical); font-weight: 700; }
  `
})
export class StackTraceComponent {
  readonly stackTrace = input<string | null>(null);
  readonly analysis = input<StackAnalysis | null>(null);
  protected readonly collapsed = signal(false);

  private readonly lines = computed<StackLine[]>(() => {
    const text = this.stackTrace() ?? '';
    const origin = this.analysis()?.probableOrigin;
    let originMarked = false;
    return text.split('\n').map((raw, index) => {
      const t = raw.trim();
      let kind: StackLine['kind'] = 'other';
      if (index === 0) {
        kind = 'header';
      } else if (t.startsWith('Caused by:') || t.startsWith('Suppressed:')) {
        kind = 'cause';
      } else if (t.startsWith('...')) {
        kind = 'more';
      } else if (t.startsWith('at ')) {
        kind = isFramework(t.substring(3)) ? 'framework' : 'app';
      }
      let isOrigin = false;
      if (!originMarked && origin && kind === 'app' && t.includes(`${origin.className}.${origin.method}(${origin.location})`)) {
        // the origin belongs to the deepest cause: only mark a frame after the last matching cause header
        isOrigin = this.isWithinOriginCause(text.split('\n'), index, origin.exceptionClass);
        originMarked = isOrigin;
      }
      return { text: raw, kind, origin: isOrigin };
    });
  });

  protected readonly shown = computed(() =>
    this.collapsed() ? this.lines().filter((l) => l.kind !== 'framework' || l.origin) : this.lines()
  );

  private isWithinOriginCause(all: string[], index: number, exceptionClass: string): boolean {
    for (let i = index; i >= 0; i--) {
      const t = all[i].trim();
      if (i === 0 || t.startsWith('Caused by:')) {
        return t.includes(exceptionClass);
      }
    }
    return false;
  }
}

const FRAMEWORK = ['java.', 'javax.', 'jakarta.', 'jdk.', 'sun.', 'com.sun.', 'org.springframework.', 'org.hibernate.',
  'org.apache.', 'com.zaxxer.', 'io.netty.', 'reactor.', 'kotlin.', 'org.postgresql.', 'com.fasterxml.', 'tools.jackson.',
  'io.github.resilience4j.', 'org.junit.', 'ch.qos.logback.', 'org.slf4j.', 'io.micrometer.', 'org.h2.'];

function isFramework(frame: string): boolean {
  const cls = frame.replace(/^[\w.$/@-]+\//, '');
  return FRAMEWORK.some((p) => cls.startsWith(p));
}

