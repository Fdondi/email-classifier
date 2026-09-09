import { Component, Input } from '@angular/core';
import { Router } from '@angular/router';
import { DualAxisSection, EmailSummary } from '../../models/email.model';
import { DualAxisChartModel, buildDualAxisChart } from './dual-axis-chart.layout';

@Component({
  selector: 'app-dual-axis-chart',
  standalone: true,
  templateUrl: './dual-axis-chart.component.html'
})
export class DualAxisChartComponent {
  @Input({ required: true }) section!: DualAxisSection;

  constructor(private readonly router: Router) {}

  model(): DualAxisChartModel {
    return buildDualAxisChart(this.section);
  }

  openPoint(email: EmailSummary, event: MouseEvent): void {
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
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
}
