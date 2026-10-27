import { TestBed } from '@angular/core/testing';
import { traceDetail } from '../../../testing/fixtures';
import { LevelBadgeComponent, StatusBadgeComponent } from './badges.component';
import { EventTimelineComponent } from './event-timeline.component';
import { WaterfallComponent } from './waterfall.component';

describe('trace presentation components', () => {
  it('renders the waterfall with proportional bars and error markers', () => {
    const fixture = TestBed.createComponent(WaterfallComponent);
    const detail = traceDetail();
    fixture.componentRef.setInput('spans', detail.spans);
    fixture.componentRef.setInput('basis', 'SPANS');
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    const rows = el.querySelectorAll('li.row');
    expect(rows.length).toBe(2);
    const bars = el.querySelectorAll<HTMLElement>('.bar');
    expect(parseFloat(bars[0].style.width)).toBeCloseTo((5014 / 7324) * 100, 0);
    expect(bars[1].classList.contains('error')).toBe(true);
    expect(rows[1].textContent).toContain('7.32 s');
  });

  it('shows an empty state when the waterfall has no timing data', () => {
    const fixture = TestBed.createComponent(WaterfallComponent);
    fixture.componentRef.setInput('basis', 'NONE');
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('No timing information');
  });

  it('filters the event timeline by service and emits selections', () => {
    const fixture = TestBed.createComponent(EventTimelineComponent);
    fixture.componentRef.setInput('events', traceDetail().timeline);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('li').length).toBe(2);
    expect(el.querySelector('li.error')!.textContent).toContain('ClientAbortException');

    const selected: string[] = [];
    fixture.componentInstance.eventSelected.subscribe((e) => selected.push(e.id));
    (el.querySelector('li') as HTMLElement).click();
    expect(selected).toEqual(['e1']);

    fixture.componentRef.setInput('serviceFilter', 'payment-service');
    fixture.detectChanges();
    expect(el.querySelectorAll('li').length).toBe(1);
  });

  it('renders badges with text and icon, never color only', () => {
    const level = TestBed.createComponent(LevelBadgeComponent);
    level.componentRef.setInput('level', 'ERROR');
    level.detectChanges();
    expect(level.nativeElement.textContent).toContain('ERROR');
    expect(level.nativeElement.querySelector('i.pi-times-circle')).not.toBeNull();

    const status = TestBed.createComponent(StatusBadgeComponent);
    status.componentRef.setInput('status', 'WARNING');
    status.componentRef.setInput('httpStatus', 404);
    status.detectChanges();
    expect(status.nativeElement.textContent).toContain('WARNING · 404');
    expect(status.nativeElement.className).toContain('status-warning');
  });
});
