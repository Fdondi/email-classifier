import { DualAxisSection, EmailSummary } from '../../models/email.model';
import {
  IMPORTANCE_RGB,
  URGENCY_RGB,
  buildDualAxisChart,
  importanceScore,
  rgbSum,
  urgencyScore
} from './dual-axis-chart.layout';

describe('dual-axis-chart layout', () => {
  const today = '2026-09-08';

  it('ranks earlier due dates as more urgent', () => {
    expect(urgencyScore(email({ id: 1, dueDate: '2026-09-01' }))).toBeGreaterThan(
      urgencyScore(email({ id: 2, dueDate: '2026-09-20' }))
    );
  });

  it('puts emails with no due date below dated mail', () => {
    expect(urgencyScore(email({ id: 1, dueDate: '2026-12-01' }))).toBeGreaterThan(
      urgencyScore(email({ id: 2, dateSent: '2020-01-01T00:00:00Z' }))
    );
  });

  it('treats expired mail as more important than a higher blended score', () => {
    const expired = email({ id: 1, dueDate: '2026-09-01', blendedScore: 0.1 });
    const upcoming = email({ id: 2, dueDate: '2026-09-20', blendedScore: 0.9 });
    expect(importanceScore(expired, today)).toBeGreaterThan(importanceScore(upcoming, today));
  });

  it('sums urgency and importance RGB in the intersection', () => {
    const section = threeEmailSection();
    const chart = buildDualAxisChart(section, today);
    const summed = rgbSum(URGENCY_RGB, IMPORTANCE_RGB);
    expect(chart.bothFill).toBe(`rgb(${summed.r}, ${summed.g}, ${summed.b})`);
    expect(summed.r).toBe(Math.min(255, URGENCY_RGB.r + IMPORTANCE_RGB.r));
    expect(summed.g).toBe(Math.min(255, URGENCY_RGB.g + IMPORTANCE_RGB.g));
    expect(summed.b).toBe(Math.min(255, URGENCY_RGB.b + IMPORTANCE_RGB.b));
  });

  it('colors right of the urgency cutoff and above the importance cutoff', () => {
    const section = threeEmailSection();
    const chart = buildDualAxisChart(section, today);
    const urgent = chart.points.find((point) => point.email.id === 1)!;
    const important = chart.points.find((point) => point.email.id === 2)!;
    const neither = chart.points.find((point) => point.email.id === 3)!;

    expect(chart.urgencyCutoffX).not.toBeNull();
    expect(chart.importanceCutoffY).not.toBeNull();
    expect(urgent.x).toBeGreaterThan(chart.urgencyCutoffX!);
    expect(important.y).toBeLessThan(chart.importanceCutoffY!);
    expect(neither.x).toBeLessThan(chart.urgencyCutoffX!);
    expect(neither.y).toBeGreaterThan(chart.importanceCutoffY!);

    expect(chart.urgencyBand).not.toBeNull();
    expect(chart.importanceBand).not.toBeNull();
    expect(chart.bothBand).not.toBeNull();
    expect(chart.bothBand!.x).toBeCloseTo(chart.urgencyCutoffX!, 5);
    expect(chart.bothBand!.y).toBeCloseTo(chart.plot.y, 5);
  });

  it('plots every allEmails point, not only the two lists', () => {
    const section = threeEmailSection();
    const chart = buildDualAxisChart(section, today);
    expect(chart.points.map((point) => point.email.id).sort()).toEqual([1, 2, 3]);
  });
});

function threeEmailSection(): DualAxisSection {
  const urgent = email({ id: 1, subject: 'Soon', dueDate: '2026-09-09', blendedScore: 0.2 });
  const important = email({ id: 2, subject: 'Big', dueDate: '2026-09-20', blendedScore: 0.9 });
  const neither = email({ id: 3, subject: 'Later', dueDate: '2026-10-01', blendedScore: 0.1 });
  return {
    byUrgency: [urgent],
    byImportance: [important],
    allEmails: [urgent, important, neither],
    total: 3
  };
}

function email(partial: Partial<EmailSummary> & { id: number }): EmailSummary {
  return {
    subject: 'subject',
    fromAddress: null,
    fromDisplayName: null,
    dateSent: null,
    category: 'EVENT',
    summary: null,
    confidence: null,
    interestScore: null,
    dueDate: null,
    blendedScore: null,
    batchRank: null,
    injectionSuspected: false,
    needsManualReview: false,
    classificationSource: null,
    gmailUrl: null,
    ...partial
  };
}
