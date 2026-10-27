import { SystemInfo } from '../../core/api/models';
import { formatBytes } from '../../core/util/format';

export interface FileCheck {
  file: File;
  error: string | null;
}

export const DEFAULT_LIMITS: Pick<SystemInfo, 'allowedExtensions' | 'maxFileSizeBytes' | 'maxFiles'> = {
  allowedExtensions: ['log', 'txt', 'json', 'jsonl'],
  maxFileSizeBytes: 50 * 1024 * 1024,
  maxFiles: 10
};

/** Client side pre-validation; the backend re-validates extension, size and content. */
export function checkFiles(files: File[], limits: Pick<SystemInfo, 'allowedExtensions' | 'maxFileSizeBytes' | 'maxFiles'>): FileCheck[] {
  return files.map((file, index) => {
    const ext = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : '';
    let error: string | null = null;
    if (!limits.allowedExtensions.includes(ext)) {
      error = `Unsupported extension. Allowed: ${limits.allowedExtensions.map((e) => '.' + e).join(', ')}`;
    } else if (file.size === 0) {
      error = 'File is empty';
    } else if (file.size > limits.maxFileSizeBytes) {
      error = `Exceeds the ${formatBytes(limits.maxFileSizeBytes)} limit`;
    } else if (index >= limits.maxFiles) {
      error = `At most ${limits.maxFiles} files per import`;
    }
    return { file, error };
  });
}
