import { DualAxisSection, EmailSummary, isExpiredDueDate, todayIsoDate } from '../../models/email.model';

export const CHART_WIDTH = 640;
export const CHART_HEIGHT = 340;
export const CHART_MARGIN = { top: 18, right: 18, bottom: 44, left: 52 };

export const URGENCY_RGB = { r: 196, g: 92, b: 38 };
export const IMPORTANCE_RGB = { r: 48, g: 118, b: 186 };

export interface Rgb {
  r: number;
  g: number;
  b: number;
}

export interface ChartRect {
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface ChartPoint {
  email: EmailSummary;
  x: number;
  y: number;
  urgency: number;
  importance: number;
  inUrgency: boolean;
  inImportance: boolean;
}

export interface DualAxisChartModel {
  width: number;
  height: number;
  plot: ChartRect;
  points: ChartPoint[];
  urgencyBand: ChartRect | null;
  importanceBand: ChartRect | null;
  bothBand: ChartRect | null;
  urgencyCutoffX: number | null;
  importanceCutoffY: number | null;
  urgencyFill: string;
  importanceFill: string;
  bothFill: string;
}

export function rgbSum(a: Rgb, b: Rgb): Rgb {
  return {
    r: Math.min(255, a.r + b.r),
    g: Math.min(255, a.g + b.g),
    b: Math.min(255, a.b + b.b)
  };
}

export function rgbCss(color: Rgb): string {
  return `rgb(${color.r}, ${color.g}, ${color.b})`;
}

/** Higher is more urgent: earlier due date, then earlier sent; missing due dates last. */
export function urgencyScore(email: EmailSummary): number {
  if (email.dueDate) {
    return -epochDays(email.dueDate);
  }
  if (email.dateSent) {
    return -1e9 - Date.parse(email.dateSent) / 86_400_000;
  }
  return -2e9;
}

/** Higher is more important: expired first (same as the importance list), then blended score. */
export function importanceScore(email: EmailSummary, today: string = todayIsoDate()): number {
  const blended = email.blendedScore ?? -1;
  const expiredBoost = isExpiredDueDate(email.dueDate, today) ? 1.01 : 0;
  const rankTie = email.batchRank == null ? 0 : -email.batchRank * 1e-6;
  return expiredBoost + blended + rankTie;
}

export function emailsForChart(section: DualAxisSection): EmailSummary[] {
  if (section.allEmails?.length) {
    return section.allEmails;
  }
  const byId = new Map<number, EmailSummary>();
  for (const email of [...section.byUrgency, ...section.byImportance]) {
    byId.set(email.id, email);
  }
  return [...byId.values()];
}

export function buildDualAxisChart(
  section: DualAxisSection,
  today: string = todayIsoDate()
): DualAxisChartModel {
  const plot: ChartRect = {
    x: CHART_MARGIN.left,
    y: CHART_MARGIN.top,
    w: CHART_WIDTH - CHART_MARGIN.left - CHART_MARGIN.right,
    h: CHART_HEIGHT - CHART_MARGIN.top - CHART_MARGIN.bottom
  };
  const emails = emailsForChart(section);
  const urgencyIds = new Set(section.byUrgency.map((email) => email.id));
  const importanceIds = new Set(section.byImportance.map((email) => email.id));
  const scored = emails.map((email) => ({
    email,
    urgency: urgencyScore(email),
    importance: importanceScore(email, today)
  }));
  const xScale = paddedScale(scored.map((item) => item.urgency));
  const yScale = paddedScale(scored.map((item) => item.importance));
  const toX = (urgency: number) => plot.x + ((urgency - xScale.min) / xScale.span) * plot.w;
  const toY = (importance: number) => plot.y + (1 - (importance - yScale.min) / yScale.span) * plot.h;

  const urgencyCutoff = section.byUrgency.length
    ? bandThreshold(
        Math.min(...section.byUrgency.map((email) => urgencyScore(email))),
        scored.map((item) => item.urgency)
      )
    : null;
  const importanceCutoff = section.byImportance.length
    ? bandThreshold(
        Math.min(...section.byImportance.map((email) => importanceScore(email, today))),
        scored.map((item) => item.importance)
      )
    : null;
  const urgencyCutoffX = urgencyCutoff == null ? null : clamp(toX(urgencyCutoff), plot.x, plot.x + plot.w);
  const importanceCutoffY =
    importanceCutoff == null ? null : clamp(toY(importanceCutoff), plot.y, plot.y + plot.h);

  const right = plot.x + plot.w;
  const bottom = plot.y + plot.h;
  const bands = cutoffBands(plot, urgencyCutoffX, importanceCutoffY, right, bottom);

  const points: ChartPoint[] = scored.map((item) => ({
    email: item.email,
    x: toX(item.urgency),
    y: toY(item.importance),
    urgency: item.urgency,
    importance: item.importance,
    inUrgency: urgencyIds.has(item.email.id),
    inImportance: importanceIds.has(item.email.id)
  }));

  return {
    width: CHART_WIDTH,
    height: CHART_HEIGHT,
    plot,
    points: jitterOverlapping(points),
    urgencyBand: bands.urgencyBand,
    importanceBand: bands.importanceBand,
    bothBand: bands.bothBand,
    urgencyCutoffX,
    importanceCutoffY,
    urgencyFill: rgbCss(URGENCY_RGB),
    importanceFill: rgbCss(IMPORTANCE_RGB),
    bothFill: rgbCss(rgbSum(URGENCY_RGB, IMPORTANCE_RGB))
  };
}

function epochDays(isoDate: string): number {
  const parts = isoDate.slice(0, 10).split('-').map(Number);
  const year = parts[0];
  const month = parts[1];
  const day = parts[2];
  if (year == null || month == null || day == null || [year, month, day].some((part) => Number.isNaN(part))) {
    return Number.POSITIVE_INFINITY;
  }
  return Date.UTC(year, month - 1, day) / 86_400_000;
}

function bandThreshold(listedMin: number, allValues: number[]): number {
  const below = allValues.filter((value) => value < listedMin);
  if (below.length) {
    return (listedMin + Math.max(...below)) / 2;
  }
  const span = Math.max(...allValues) - Math.min(...allValues);
  return listedMin - (span === 0 ? 1 : span * 0.04);
}

function paddedScale(values: number[]): { min: number; span: number } {
  if (values.length === 0) {
    return { min: 0, span: 1 };
  }
  let min = Math.min(...values);
  let max = Math.max(...values);
  if (min === max) {
    min -= 1;
    max += 1;
  } else {
    const pad = (max - min) * 0.08;
    min -= pad;
    max += pad;
  }
  return { min, span: max - min };
}

function clamp(value: number, lo: number, hi: number): number {
  return Math.min(hi, Math.max(lo, value));
}

function rect(x: number, y: number, w: number, h: number): ChartRect {
  return { x, y, w, h };
}

function cutoffBands(
  plot: ChartRect,
  urgencyCutoffX: number | null,
  importanceCutoffY: number | null,
  right: number,
  bottom: number
): { urgencyBand: ChartRect | null; importanceBand: ChartRect | null; bothBand: ChartRect | null } {
  if (urgencyCutoffX != null && importanceCutoffY != null) {
    return {
      urgencyBand: emptyToNull(rect(urgencyCutoffX, importanceCutoffY, right - urgencyCutoffX, bottom - importanceCutoffY)),
      importanceBand: emptyToNull(rect(plot.x, plot.y, urgencyCutoffX - plot.x, importanceCutoffY - plot.y)),
      bothBand: emptyToNull(rect(urgencyCutoffX, plot.y, right - urgencyCutoffX, importanceCutoffY - plot.y))
    };
  }
  if (urgencyCutoffX != null) {
    return {
      urgencyBand: emptyToNull(rect(urgencyCutoffX, plot.y, right - urgencyCutoffX, plot.h)),
      importanceBand: null,
      bothBand: null
    };
  }
  if (importanceCutoffY != null) {
    return {
      urgencyBand: null,
      importanceBand: emptyToNull(rect(plot.x, plot.y, plot.w, importanceCutoffY - plot.y)),
      bothBand: null
    };
  }
  return { urgencyBand: null, importanceBand: null, bothBand: null };
}

function emptyToNull(band: ChartRect): ChartRect | null {
  if (band.w <= 0.5 || band.h <= 0.5) {
    return null;
  }
  return band;
}

function jitterOverlapping(points: ChartPoint[]): ChartPoint[] {
  const groups = new Map<string, ChartPoint[]>();
  for (const point of points) {
    const key = `${Math.round(point.x)},${Math.round(point.y)}`;
    const group = groups.get(key) ?? [];
    group.push(point);
    groups.set(key, group);
  }
  const result: ChartPoint[] = [];
  for (const group of groups.values()) {
    const first = group[0];
    if (group.length === 1 && first) {
      result.push(first);
      continue;
    }
    group.forEach((point, index) => {
      const angle = (index / group.length) * Math.PI * 2;
      const radius = 6;
      result.push({
        ...point,
        x: point.x + Math.cos(angle) * radius,
        y: point.y + Math.sin(angle) * radius
      });
    });
  }
  return result;
}
