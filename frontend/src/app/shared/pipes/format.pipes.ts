import { Pipe, PipeTransform } from '@angular/core';
import { formatBytes, formatDuration, formatNumber, formatPercent, formatTimestamp, shortId, simpleClassName } from '../../core/util/format';

@Pipe({ name: 'duration' })
export class DurationPipe implements PipeTransform {
  transform(ms: number | null | undefined): string {
    return formatDuration(ms);
  }
}

@Pipe({ name: 'bytes' })
export class BytesPipe implements PipeTransform {
  transform(bytes: number | null | undefined): string {
    return formatBytes(bytes);
  }
}

@Pipe({ name: 'ts' })
export class TimestampPipe implements PipeTransform {
  transform(iso: string | null | undefined, withDate = true): string {
    return formatTimestamp(iso, withDate);
  }
}

@Pipe({ name: 'percent1' })
export class PercentPipe implements PipeTransform {
  transform(ratio: number | null | undefined): string {
    return formatPercent(ratio);
  }
}

@Pipe({ name: 'num' })
export class NumberPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    return formatNumber(value);
  }
}

@Pipe({ name: 'shortId' })
export class ShortIdPipe implements PipeTransform {
  transform(id: string | null | undefined, keep = 8): string {
    return shortId(id, keep);
  }
}

@Pipe({ name: 'simpleClass' })
export class SimpleClassPipe implements PipeTransform {
  transform(name: string | null | undefined): string {
    return simpleClassName(name);
  }
}
