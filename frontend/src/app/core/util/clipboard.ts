import { inject, Injectable } from '@angular/core';
import { MessageService } from 'primeng/api';

/** Copies text to the clipboard with toast feedback. */
@Injectable({ providedIn: 'root' })
export class ClipboardService {
  private readonly messages = inject(MessageService);

  async copy(text: string | null | undefined, label = 'Value'): Promise<boolean> {
    if (!text) {
      return false;
    }
    try {
      await navigator.clipboard.writeText(text);
      this.messages.add({ severity: 'success', summary: `${label} copied`, detail: truncate(text, 80), life: 2000 });
      return true;
    } catch {
      this.messages.add({ severity: 'warn', summary: 'Copy failed', detail: 'Clipboard access is not available' });
      return false;
    }
  }
}

function truncate(text: string, max: number): string {
  return text.length > max ? `${text.slice(0, max)}…` : text;
}
