import { DatePipe } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { AppNavComponent } from '../../components/app-nav/app-nav.component';
import { TriageRun } from '../../models/email.model';
import { ApiService, describeHttpError } from '../../services/api.service';

@Component({
  selector: 'app-runs',
  standalone: true,
  imports: [AppNavComponent, DatePipe],
  templateUrl: './runs.component.html'
})
export class RunsComponent implements OnInit {
  runs: TriageRun[] = [];
  error: string | null = null;

  constructor(private readonly api: ApiService) {}

  ngOnInit(): void {
    this.api.runs().subscribe({
      next: (runs) => {
        this.runs = runs;
      },
      error: (err: unknown) => {
        this.error = describeHttpError(err, 'Failed to load runs');
      }
    });
  }

  duration(ms: number): string {
    if (ms < 1000) {
      return ms + ' ms';
    }
    const seconds = Math.round(ms / 1000);
    if (seconds < 60) {
      return seconds + 's';
    }
    const minutes = Math.floor(seconds / 60);
    const rest = seconds % 60;
    return minutes + ' min ' + rest + 's';
  }

  classified(run: TriageRun): number {
    return run.classifiedByRule + run.classifiedByLlm + run.fallback;
  }
}
