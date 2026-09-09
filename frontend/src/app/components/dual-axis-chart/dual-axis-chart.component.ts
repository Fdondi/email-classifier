import { Component, ElementRef, EventEmitter, HostListener, Input, Output, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { DualAxisSection, EmailSummary } from '../../models/email.model';
import { ApiService, describeHttpError } from '../../services/api.service';
import {
  ChartPoint,
  DualAxisChartModel,
  applyRatingToSection,
  buildDualAxisChart,
  ratingFromSvg,
  svgPointFromClient
} from './dual-axis-chart.layout';

@Component({
  selector: 'app-dual-axis-chart',
  standalone: true,
  templateUrl: './dual-axis-chart.component.html'
})
export class DualAxisChartComponent {
  @Input({ required: true }) section!: DualAxisSection;
  @Output() rated = new EventEmitter<DualAxisSection>();
  @Output() failed = new EventEmitter<string>();
  @ViewChild('chartSvg') chartSvg?: ElementRef<SVGSVGElement>;

  private drag: {
    id: number;
    startClientX: number;
    startClientY: number;
    x: number;
    y: number;
    plotX: number;
    plotY: number;
    plotW: number;
    plotH: number;
    predictedUrgency: number;
    predictedImportance: number;
    moved: boolean;
  } | null = null;
  savingId: number | null = null;

  constructor(
    private readonly router: Router,
    private readonly api: ApiService
  ) {}

  model(): DualAxisChartModel {
    const chart = buildDualAxisChart(this.section);
    if (!this.drag) {
      return chart;
    }
    const drag = this.drag;
    return {
      ...chart,
      points: chart.points.map((point) =>
        point.email.id === drag.id ? { ...point, x: drag.x, y: drag.y } : point
      )
    };
  }

  draggingId(): number | null {
    return this.drag?.id ?? null;
  }

  onPointerDown(event: PointerEvent, point: ChartPoint): void {
    if (event.button !== 0) {
      return;
    }
    event.preventDefault();
    event.stopPropagation();
    (event.target as Element).setPointerCapture?.(event.pointerId);
    const plot = buildDualAxisChart(this.section).plot;
    this.drag = {
      id: point.email.id,
      startClientX: event.clientX,
      startClientY: event.clientY,
      x: point.x,
      y: point.y,
      plotX: plot.x,
      plotY: plot.y,
      plotW: plot.w,
      plotH: plot.h,
      predictedUrgency: point.urgency,
      predictedImportance: point.importance,
      moved: false
    };
  }

  @HostListener('document:pointermove', ['$event'])
  onPointerMove(event: PointerEvent): void {
    if (!this.drag) {
      return;
    }
    const svg = this.chartSvg?.nativeElement;
    if (!svg) {
      return;
    }
    const point = svgPointFromClient(event.clientX, event.clientY, svg);
    const dx = event.clientX - this.drag.startClientX;
    const dy = event.clientY - this.drag.startClientY;
    this.drag = {
      ...this.drag,
      x: clampRange(point.x, this.drag.plotX, this.drag.plotX + this.drag.plotW),
      y: clampRange(point.y, this.drag.plotY, this.drag.plotY + this.drag.plotH),
      moved: this.drag.moved || dx * dx + dy * dy > 25
    };
  }

  @HostListener('document:pointerup', ['$event'])
  onPointerUp(event: PointerEvent): void {
    const drag = this.drag;
    if (!drag) {
      return;
    }
    this.drag = null;
    const email = this.sectionEmail(drag.id);
    if (!email) {
      return;
    }
    if (!drag.moved) {
      this.openPoint(email, event);
      return;
    }
    const chart = buildDualAxisChart(this.section);
    const rating = ratingFromSvg(drag.x, drag.y, chart);
    this.savingId = drag.id;
    this.api
      .rateEmail(drag.id, {
        urgency: rating.urgency,
        importance: rating.importance,
        predictedUrgency: drag.predictedUrgency,
        predictedImportance: drag.predictedImportance
      })
      .subscribe({
        next: () => {
          this.savingId = null;
          this.rated.emit(applyRatingToSection(this.section, drag.id, rating.urgency, rating.importance));
        },
        error: (err: unknown) => {
          this.savingId = null;
          this.failed.emit(describeHttpError(err, 'Could not save rating'));
        }
      });
  }

  openPoint(email: EmailSummary, event: MouseEvent | PointerEvent): void {
    if (event.defaultPrevented || ('button' in event && event.button !== 0)) {
      return;
    }
    if (email.gmailUrl) {
      event.preventDefault();
      window.open(email.gmailUrl, '_blank', 'noopener,noreferrer');
      return;
    }
    event.preventDefault();
    void this.router.navigate(['/email', email.id]);
  }

  private sectionEmail(id: number): EmailSummary | undefined {
    return this.section.allEmails?.find((email) => email.id === id)
      ?? this.section.byUrgency.find((email) => email.id === id)
      ?? this.section.byImportance.find((email) => email.id === id);
  }
}

function clampRange(value: number, lo: number, hi: number): number {
  return Math.min(hi, Math.max(lo, value));
}
