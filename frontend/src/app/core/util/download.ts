/** Client side downloads (JSON exports) and server side downloads (CSV streaming endpoint). */

export function downloadJson(data: unknown, fileName: string): void {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
  downloadBlob(blob, fileName.endsWith('.json') ? fileName : `${fileName}.json`);
}

export function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  triggerDownload(url, fileName);
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function downloadUrl(url: string, fileName: string): void {
  triggerDownload(url, fileName);
}

function triggerDownload(href: string, fileName: string): void {
  const anchor = document.createElement('a');
  anchor.href = href;
  anchor.download = fileName;
  anchor.rel = 'noopener';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
}

/** Builds CSV text with formula-injection protection, used for client side exports. */
export function toCsv(rows: Record<string, unknown>[], columns: string[]): string {
  const escape = (value: unknown): string => {
    if (value === null || value === undefined) {
      return '';
    }
    let text = String(value).replace(/[\r\n]+/g, ' ');
    if (/^[=+\-@\t]/.test(text)) {
      text = `'${text}`;
    }
    return /[",;]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
  };
  return [columns.join(','), ...rows.map((row) => columns.map((c) => escape(row[c])).join(','))].join('\n');
}
