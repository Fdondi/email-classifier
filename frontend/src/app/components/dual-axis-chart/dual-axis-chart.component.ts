import { ChangeDetectionStrategy, Component, ElementRef, EventEmitter, HostListener, Input, Output, ViewChild } from '@angular/core';
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
  changeDetection: ChangeDetectionStrategy.Eager,
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
  // Clicked points are remembered per browser so they stay faded after navigating away.
  private clicked = new Set<number>(loadClicked());

  constructor(
    private readonly router: Router,
    private readonly api: ApiService
  ) {}

  isClicked(id: number): boolean {
    return this.clicked.has(id);
  }

  /** Emails of the bundle currently open below the chart, or null when none is open. */
  openedBundle: EmailSummary[] | null = null;

  openBundle(point: ChartPoint, event: MouseEvent): void {
    event.preventDefault();
    this.openedBundle = this.openedBundle && point.bundle && this.openedBundle[0]?.id === point.bundle[0]?.id
      ? null
      : point.bundle;
  }

  closeBundle(): void {
    this.openedBundle = null;
  }

  unclickedInBundle(): EmailSummary[] {
    return (this.openedBundle ?? []).filter((email) => !this.clicked.has(email.id));
  }

  clickedInBundle(): EmailSummary[] {
    return (this.openedBundle ?? []).filter((email) => this.clicked.has(email.id));
  }

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
    this.clicked.add(email.id);
    saveClicked(this.clicked);
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

const CLICKED_KEY = 'emailtool.chartClicked';

function loadClicked(): number[] {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(CLICKED_KEY) ?? '[]');
    return Array.isArray(parsed) ? parsed.filter((id): id is number => typeof id === 'number') : [];
  } catch {
    return [];
  }
}

function saveClicked(ids: Set<number>): void {
  try {
    localStorage.setItem(CLICKED_KEY, JSON.stringify([...ids]));
  } catch {
    // Storage may be unavailable (private mode); the fade then lasts only for this page view.
  }
}
